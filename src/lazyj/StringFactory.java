package lazyj;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;


/**
 * Keep references to common strings to avoid filling the memory with garbage.
 * We don't use String.intern() any more because it seems to never clean up. Instead it is now
 * relaying on a WeakHashMap.
 * 
 * @author costing
 * @since Aug 10, 2007 (1.0.2)
 * @see java.lang.String#intern()
 */
public final class StringFactory {
	/**
	 * How many hits
	 */
	private static transient long		lCacheHit			= 0;

	/**
	 * How many misses
	 */
	private static transient long		lCacheMiss			= 0;

	/**
	 * How many strings were ignored
	 */
	private static transient long		lCacheIgnore		= 0;

	/**
	 * Actual cache, the WeakHashMap
	 */
	private static Map<String, WeakReference<String>> 	hmCache = new WeakHashMap<String, WeakReference<String>>(1024);
	
	/**
	 * Get the global string pointer for this byte array
	 * 
	 * @param vb
	 * @return the global string pointer
	 */
	public static String get(final byte[] vb) {
		if (vb == null)
			return null;

		return get(new String(vb));
	}

	/**
	 * Get the global string pointer for this char array
	 * 
	 * @param vc
	 * @return the global string pointer
	 */
	public static String get(final char[] vc) {
		if (vc == null)
			return null;

		return get(new String(vc));
	}

	/**
	 * Get the global string pointer for this value
	 * 
	 * @param s
	 * @return the global string pointer for an object having the same value
	 */
	public static synchronized String get(final String s) {
		if (s == null) {
			lCacheIgnore++;
			return s;
		}
		
        final WeakReference<String> t = hmCache.get(s);

        String sRet;
        
        if (t == null || (sRet = t.get()) == null) {
            hmCache.put(s, new WeakReference<String>(s));
            lCacheMiss++;
            return s;
        }
        
        lCacheHit++;

		return sRet;
	}

	/**
	 * Statistics function: the number of strings in the cache.
	 * 
	 * @return the number of strings in the cache
	 */
	public static synchronized int getCacheSize() {
		return hmCache.size();
	}

	/**
	 * Calculate the cache efficiency (hits / total requests)
	 * 
	 * @return cache efficiency
	 */
	public static synchronized double getHitRatio() {
		final double d = getAccessCount();

		if (d >= 1)
			return (lCacheHit * 100d) / d;
		
		return 0;
	}

	/**
	 * Calculate the percentage of requests that were ignored because of size limits.
	 * 
	 * @return ignore ratio
	 */
	public static synchronized double getIgnoreRatio() {
		final double d = getAccessCount();

		if (d >= 1)
			return (lCacheIgnore * 100d) / d;
		
		return 0;
	}

	/**
	 * Get the total number of accesses to this cache
	 * 
	 * @return the number of accesses to this cache
	 */
	public static synchronized long getAccessCount() {
		return lCacheHit + lCacheMiss + lCacheIgnore;
	}

	/**
	 * Clear the counters
	 */
	public static synchronized void resetHitCounters() {
		lCacheHit = lCacheMiss = lCacheIgnore = 0;
	}
	
	/**
	 * Clear this cache structure
	 * @deprecated
	 */
	@Deprecated
	public static synchronized void clear(){
		// nothing
	}
}
