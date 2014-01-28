package lazyj.cache;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import lazyj.LRUMap;

/**
 * Keep a cache of the last used values. The user has to implement a way to resolve missing keys.
 * 
 * @author costing
 * @param <K> key type
 * @param <V> value type
 * @since 2007-03-08
 */
public abstract class GenericLastValuesCache<K, V> implements CacheElement<K, V>, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * This function gives the maximum number of entries that are allowed in the cache.
	 * 
	 * @return 1000 in the default implementation, override for a custom cache size
	 */
	@SuppressWarnings("static-method")
	protected int getMaximumSize(){
		return 1000;
	}
	
	/**
	 * By default <code>null</code> values returned by {@link #resolve(Object)} will be cached, meaning that the
	 * function won't be called again for keys for which it was determined that there is no value associated. You can override
	 * this function to return <code>false</code> if you don't want to cache <code>null</code> return values but keep asking
	 * for the missing value until there is a positive answer.
	 * 
	 * @return <code>true</code> to enable <code>null</code> caching, <code>false</code> to disable it.
	 */
	@SuppressWarnings("static-method")
	protected boolean cacheNulls(){
		return true;
	}
	
	/**
	 * Actual cache contents
	 */
	private final Map<K, V> cache = Collections.synchronizedMap(new LRUMap<K, V>(getMaximumSize()));
	
	/**
	 * Cache for <code>null</code> values (only retains the key name)
	 */
	private Map<K, K> nullCache = null;
	
	@Override
	public V get(final K key) {
		V oVal = this.cache.get(key);
		
		if (cacheNulls() && this.nullCache!=null){
			if (this.nullCache.containsKey(key))
				return null;
		}
		
		if (oVal==null){
			oVal = resolve(key);
			
			if (oVal!=null)
				this.cache.put(key, oVal);
			else
			if (cacheNulls()){
				if (this.nullCache==null)
					this.nullCache = Collections.synchronizedMap(new LRUMap<K, K>(getMaximumSize()));
				
				this.nullCache.put(key, key);
			}
		}
		
		return oVal;
	}
	
	/**
	 * You should implement here a way of finding out the value for a given key. Can return null if there is no value for the given key.
	 * 
	 * @param key
	 * @return the value for the given key.
	 */
	protected abstract V resolve(K key);

	/**
	 * Remove the entry for this key, so that the next request will force a refresh, of this key only.
	 * 
	 * @param key key to remove
	 */
	public void remove(final K key){
		if (this.cache.remove(key)==null && this.nullCache!=null)
			this.nullCache.remove(key);
	}
	
	/**
	 * Force a refresh of this key. To be used for example with null-enabled caches when a key that didn't have a value until now has 
	 * been defined and the cache for this entry must be refreshed. Or when the value has changed of course.
	 * 
	 * @param key
	 * @return new value for this key
	 */
	public V refresh(final K key){
		remove(key);
		
		return get(key);
	}
	
	@Override
	public void refresh() {
		this.cache.clear();
		
		if (this.nullCache!=null)
			this.nullCache.clear();
	}
	
	@Override
	public int getRefreshTime(){
		return -1;
	}

}
