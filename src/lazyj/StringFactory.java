package lazyj;


/**
 * Keep references to common strings to avoid filling the memory with garbage.
 * It actually makes more sense to let Java do it for us , with this class we just keep statistics over what happened.
 * 
 * @author costing
 * @since Aug 10, 2007 (1.0.2)
 * @see java.lang.String#intern()
 */
public final class StringFactory {
	private static transient long		lCacheHit			= 0;

	private static transient long		lCacheMiss			= 0;

	private static transient long		lCacheIgnore		= 0;

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
		
		final String sRet = s.intern();
		
		// the string comparison here is actually valid, we want to see if from the string cache we got back the same pointer or a different one
		if (sRet == s){
			lCacheHit++;
		}
		else{
			lCacheMiss++;
		}

		return sRet;
	}

	/**
	 * Statistics function: the number of strings in the cache.
	 * 
	 * @return the number of strings in the cache
	 * @deprecated
	 */
	@Deprecated
	public static synchronized int getCacheSize() {
		return -1;
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
