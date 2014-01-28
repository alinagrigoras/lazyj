/**
 * 
 */
package lazyj.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import lazyj.LRUMap;
import lazyj.Log;

/**
 * This class implements a caching with a maximum lifetime after which the elements are removed.
 * Optionally the cache can also have a limited size, in which case the least recently used entry
 * will be removed when adding a new entry with the size at the specified limit.
 * For expiration a single thread is used, so try to be fast in processing the callbacks.
 * 
 * @author costing
 * @param <K> key type
 * @param <V> value type
 * @since Nov 20, 2007 (1.0.3)
 */
public class ExpirationCache<K, V> implements CacheElement<K, V>{

	/**
	 * Cache structure
	 */
	final LRUMap<K, V> mCache;
	
	/**
	 * Expiration queue
	 */
	static final DelayQueue<QueueEntry<?, ?>> queue = new DelayQueue<>();  
	
	/**
	 * An entry in the cache, a key-value pair with an expiration time
	 * 
	 * @author costing
	 * @since Jan 17, 2009
	 * @param <K>
	 * @param <V>
	 */
	private static final class QueueEntry<K, V> implements Delayed{
		
		/**
		 * Key that is set to expire at some point
		 */
		final K key;
		
		/**
		 * The value that we will use to call back on expiration
		 */
		final V value;
		
		/**
		 * Timestamp showing moment in time when this element is going to expire
		 */
		final long expires;
		
		/**
		 * Pointer to the cache 
		 */
		public final ExpirationCache<K, V> cacheInstance;
		
		/**
		 * @param key
		 * @param value
		 * @param expires
		 * @param cacheInstance 
		 */
		public QueueEntry(final K key, final V value, final long expires, final ExpirationCache<K,V> cacheInstance){
			assert key!=null;
			
			this.key = key;
			this.value = value;
			this.expires = expires;
			this.cacheInstance = cacheInstance;
		}
		
		@Override
		public long getDelay(final TimeUnit unit) {
			return unit.convert(this.expires - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		}
		
		@Override
		public int compareTo(final Delayed o) {
			final long lDiff = getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS);
			
			if (lDiff>0)
				return 1;
			
			if (lDiff<0)
				return -1;
			
			return 0;
		}
		
		@Override
		public boolean equals(final Object obj) {
		    if (obj == null || ! (obj instanceof QueueEntry)) 
		    	return false;
		    
		    final QueueEntry<?,?> other = (QueueEntry<?,?>) obj; 
		    
		    if (this.cacheInstance != other.cacheInstance)
		    	return false;
		    
			return this.key.equals(other.key);
		}
		
		/**
		 * Callback function
		 */
		public void callback(){
			this.cacheInstance.callbackOnExpiry(this.key, this.value);
		}
		
		@Override
		public int hashCode(){
			return this.key.hashCode();
		}
	}
	
	/**
	 * Removes expired elements from the expiration queue
	 * 
	 * @author costing
	 * @since Jan 17, 2009
	 */
	private static final class ExpiryThread extends Thread{
		/**
		 * Default constructor
		 */
		public ExpiryThread(){
			setName("lazyj.cache.ExpirationCache.ExpiryThread"); //$NON-NLS-1$
			setDaemon(true);
		}
		
		@Override
		public void run(){
			while (true) {
				try{
					final QueueEntry<?, ?> qe = queue.take();
					
					if (qe!=null){
						final Object value;
						
						synchronized (qe.cacheInstance.mCache){
							value = qe.cacheInstance.mCache.remove(qe.key);
						}

						if (value!=null){
							try{
								qe.callback();
							}
							catch (final Throwable t){
								Log.log(Log.ERROR, "lazyj.cache.ExpirationCache", "I have encountered a problem on callback", t); //$NON-NLS-1$ //$NON-NLS-2$
							}
						}
						else{
							Log.log(Log.FINE, "lazyj.cache.ExpirationCache", "Got null value for key '"+qe.key.toString()+"', cache size: "+qe.cacheInstance.size()+" / "+qe.cacheInstance.mCache.getLimit()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						}
					}
				}
				catch (final InterruptedException ie){
					// ignore
				}
			}
		}
	}
	
	/**
	 * Expiration thread handle
	 */
	private static final ExpiryThread tExpirator = new ExpiryThread();

	static{
		tExpirator.start();
	}
	
	/**
	 * Build an expiration cache of unlimited size.
	 */
	public ExpirationCache(){
		this(Integer.MAX_VALUE);
	}
	
	/**
	 * An LRU map that intercepts the over-bounds condition and acts on it
	 * 
	 * @author costing
	 */
	private final class NotifyLRUMap extends LRUMap<K, V> {
		/**
		 * stop complaining :)
		 */
		private static final long serialVersionUID = -8536957422977636915L;

		/**
		 * @param iCacheSize
		 */
		public NotifyLRUMap(final int iCacheSize) {
			super(iCacheSize);
		}

		@Override
		protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
			final boolean willRemove = super.removeEldestEntry(eldest);
			
			if (willRemove){
				// if the entry is removed because the LRU map has ran out of space, remove it from 
				// the delayed queue as well
				queue.remove(new QueueEntry<>(eldest.getKey(), null, 0, ExpirationCache.this));
			}
			
			return willRemove;
		}
	}
	
	/**
	 * Build an expiration cache with a fixed size.
	 * 
	 * @param size max number of entries in the cache.
	 */
	public ExpirationCache(final int size){
		this.mCache = new NotifyLRUMap(size); 
	}
	
	/* (non-Javadoc)
	 * @see lazyj.cache.CacheElement#get(java.lang.Object)
	 */
	@Override
	public V get(final K key) {
		synchronized (this.mCache){
			return this.mCache.get(key);
		}
	}
	
	/**
	 * Remove an entry from the expiration queue
	 * 
	 * @param key
	 * @return old value, if any
	 */
	public V remove(final K key) {
		synchronized (this.mCache){
			final V value = this.mCache.remove(key);
			
			if (value!=null){
				queue.remove(new QueueEntry<>(key, null, 0, this));
			}
			
			return value;
		}
	}
	
	/**
	 * Put a value in the cache with a maximum lifetime. This method will not override an existing 
	 * cached value!
	 * 
	 * @param key 
	 * @param value
	 * @param lLifetime in milliseconds
	 * @see #overwrite(Object, Object, long)
	 */
	public void put(final K key, final V value, final long lLifetime){
		if (lLifetime>0){
			synchronized (this.mCache){
				if (!this.mCache.containsKey(key)){
					this.mCache.put(key, value);
					queue.offer(new QueueEntry<>(key, value, System.currentTimeMillis()+lLifetime, this));
				}
			}
		}
	}

	/**
	 * Add a new value in the cache for the given key, overwriting if necessary the previous entry. This will prevent the old entry from expiring, so
	 * {@link #callbackOnExpiry(Object, Object)} will never be called for the entry that is forcefully removed.
	 * 
	 * @param key
	 * @param value
	 * @param lLifetime
	 * @see #put(Object, Object, long)
	 */
	public void overwrite(final K key, final V value, final long lLifetime){
		remove(key);
		
		put(key, value, lLifetime);
	}
	
	/* (non-Javadoc)
	 * @see lazyj.cache.CacheElement#getRefreshTime()
	 */
	@Override
	public int getRefreshTime() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see lazyj.cache.CacheElement#refresh()
	 */
	@Override
	public void refresh() {
		synchronized (this.mCache){
			for (final K key: this.mCache.keySet()){
				queue.remove(new QueueEntry<>(key, null, 0, this));
			}
			this.mCache.clear();
		}
	}

	/**
	 * Get the cache size
	 * 
	 * @return the number of entries currently in the cache
	 */
	public int size(){
		synchronized (this.mCache){
			return this.mCache.size();
		}
	}
	
	/**
	 * Get the keys currently in the cache
	 * 
	 * @return the key set
	 */
	public Set<K> getKeys(){
		synchronized (this.mCache){
			return new TreeSet<>(this.mCache.keySet());
		}
	}
	
	/**
	 * Get the values in the cache
	 * 
	 * @return list of values in the cache
	 */
	public List<V> getValues(){
		synchronized (this.mCache){
			return new ArrayList<>(this.mCache.values());
		}
	}
	
	/**
	 * Callback function, to be able to react when something is removed.
	 * Override it to your liking.
	 * 
	 * @param key key that is removed
	 * @param value value for the key that is removed
	 */
	protected void callbackOnExpiry(final K key, final V value){
		// do nothing by default
	}
}
