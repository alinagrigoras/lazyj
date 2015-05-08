package lazyj.cache;

/**
 * An interface that must be implemented by any object that must be registered into the {@link lazyj.cache.Cache}.
 * 
 * @author costing
 * @param <K> key type
 * @param <V> value type
 * @since 2006-10-03
 */
public interface CacheElement<K, V> {

    /**
     * The desired refresh time, in seconds. After this much time from the previous update, the
     * {@link #refresh()} method is called.
     * 
     * @return the number of seconds between the calls to {@link #refresh()}
     */
    public int getRefreshTime();
    
    /**
     * Update the contents of the cache entry. The implementation is application-dependent.
     * Typically it involves filling an internal hash with some values from the database. But it
     * can do whatever operation here, even something that doesn't involve actually caching anything,
     * but just to execute a periodic task (cron style).
     */
    public void refresh();
    
    /**
     * The cached entry is most probably a hash of some sort. This is why the get() method
     * is specified in this interface, to provide an easy and uniform way of retrieving the
     * data between all the implementations.
     * 
     * @param key some unique key
     * @return the value associated with that key, if any, or null.
     */
    public V get(K key);

}
