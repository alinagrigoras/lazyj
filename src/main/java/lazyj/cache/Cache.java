package lazyj.cache;

import java.util.Hashtable;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import lazyj.Log;

/**
 * This class is a central point where all the data caching structure can be stored, for universal access.
 * Any structure registered here will be automatically refreshed and it will
 * be visible to everybody provided they know the registration key.<br>
 * <br>
 * To register something into this repository you need to have:<ul>
 * <li>an unique name  for the stored structure</li>
 * <li>a class that implements {@link lazyj.cache.CacheElement}</li>
 * </ul>
 *
 * @author costing
 * @since 2006-10-03
 */
public class Cache extends Thread {

	/**
	 * Wrapper class for the actual cache entries, adding expiration time.
	 */
	static final class Container implements Delayed {
		/**
		 * The entry that will be refreshed
		 */
		public final CacheElement<?, ?>	ce;

		/**
		 * When this cache entry will expire and will need to be refreshed 
		 */
		public long			lExpires;
		
		/**
		 * The only constructor
		 * 
		 * @param element
		 */
		public Container(final CacheElement<?, ?> element){
			this.ce = element;
		}
		
		/**
		 * Specified by Delayed
		 * 
		 * @param unit time unit to return the value into
		 * @return how much time has remained until this entry will expire 
		 */
		@Override
		public long getDelay(final TimeUnit unit) {
			return unit.convert(this.lExpires - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		}

		/**
		 * Compare two entries that are to expire at some point, and provide a method to sort them by
		 * the moment in time when they are going to expire.
		 * 
		 * @param o object to compare to
		 * @return a method to sort the queue entries by the expiration time
		 */
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
		public boolean equals(final Object o){
			if (o==null || !(o instanceof Container))
				return false;
			
			return this.ce.equals(((Container) o).ce);
		}
		
		@Override
		public int hashCode(){
			return this.ce.hashCode() * 31 + (int) this.lExpires;
		}
	}

	static {
		final Cache cache = new Cache();
		cache.setDaemon(true);
		cache.start();
	}

	/**
	 * This is the actual registry for the cache entries. It maps a unique key to a cache entry. 
	 */
	private static final Hashtable<String, Container>	htRegistry	= new Hashtable<>();
	
	/**
	 * The DelayQueue is used to watch for expired cache entries that need to be refreshed
	 */
	private static final DelayQueue<Container> queue = new DelayQueue<>();

	/**
	 * Register an element into the Cache. <b>Make sure that the key you give here is unique between all applications!</b>
	 * The last added entry for a given name is the one that will be seen by everybody.<br>
	 * <br>
	 * The method {@link lazyj.cache.CacheElement#refresh()} will be called before the actual registration, so you don't have to
	 * initialize the entry yourself. A construction like:<br><pre>
	 * static{
	 * 	   Cache.register("unique key", new CacheElementImplementation());
	 * }</pre><br>
	 * is probably the best way of using the Cache.<br>
	 * <br>
	 * The same method can be used to remove a cache entry. Just give <code>null</code> for the <code>ce</code> parameter.
	 * 
	 * @param sKey unique key
	 * @param ce cached structure
	 */
	public static final void register(final String sKey, final CacheElement<?, ?> ce) {
		try{
			final Container c = ce!=null ? new Container(ce) : null;
		
			synchronized (htRegistry){
				final Container cOld = htRegistry.remove(sKey);
		
				if (cOld!=null)
					queue.remove(cOld);

				if (c!=null)
					htRegistry.put(sKey, c);
			}
			
			if (c!=null){
				refreshAndQueue(c);
			
				Log.log(Log.FINE, "lazyj.cache.Cache", "Successfully registered '"+sKey+"'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
		catch (final Throwable t){
			Log.log(Log.WARNING, "lazyj.cache.Cache", "Cannot register '"+sKey+"' because", t);  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
		}
	}

	/**
	 * Use this method to retrieve an entry you are interested in. It will return the last registered CacheElement for
	 * that key, or <i>null</i> if there is no such entry. Typical use is something like:<br><pre>
	 *     CacheElement ce = Cache.get("unique key");
	 *     if (ce!=null){
	 *         SomeObject so = (SomeObject) ce.get("internal name");
	 *     } 
	 * </pre>
	 * 
	 * @param sKey the unique key given at registration
	 * @return the CacheElement that was last registered for this key
	 */
	public static final CacheElement<?, ?> get(final String sKey) {
		final Container c = htRegistry.get(sKey);

		return c != null ? c.ce : null;
	}
	
	/**
	 * Refresh a Container and (re)register it in the queue.
	 * 
	 * @param c
	 * @since 1.0.3
	 */
	private static final void refreshAndQueue(final Container c){
		try{
			c.ce.refresh();
		
			final long lRefreshTime = c.ce.getRefreshTime();
			
			if (lRefreshTime>0){
				c.lExpires = (c.lExpires==0 ? System.currentTimeMillis() : c.lExpires) + lRefreshTime*1000L;
				
				queue.offer(c);
			}
			
			Log.log(Log.FINEST, "lazyj.cache.Cache", "Successfully refreshed '"+c.ce.getClass().getName()+"'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		catch (final Throwable t){
			Log.log(Log.WARNING, "lazyj.cache.Cache", "Cannot refresh '"+c.ce.getClass().getName()+"' because", t); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}		
	}

	/**
	 * Force a refresh of a cache entry. Use this if you know that some value has changed and you need to make
	 * sure that the information in the cache is updated. <br>
	 * <br>You should not call the <i>refresh()</i> method on the
	 * {@link lazyj.cache.CacheElement} directly because the Cache will not be aware of the call and will try to update itself the 
	 * entry after an incorrect period of time. Not a big deal though :)
	 * 
	 * @param sKey the unique key to refresh
	 */
	public static final void refresh(final String sKey) {
		final Container c = htRegistry.get(sKey);
		
		if (c != null){
			queue.remove(c);
			
			refreshAndQueue(c);
		}
	}

	/**
	 * Method to discover all the registered entries. It will return the set of all keys that were registered
	 * into the Cache.
	 * 
	 * @return the Set of registered keys
	 */
	public static final Set<String> getKeySet() {
		return new TreeSet<>(htRegistry.keySet());
	}

	/**
	 * Set thread name
	 */
	private Cache() {
		super("lazyj.cache.Cache refresh thread"); //$NON-NLS-1$
	}

	/**
	 * This thread will take each entry that expires and refreshes it.
	 */
	@Override
	public void run() {
		while (true) {
			try{
				final Container c = queue.take();
				
				if (c!=null)
					refreshAndQueue(c);
			}
			catch (final InterruptedException ie){
				// ignore interruption signals
			}
		}
	}
}
