/**
 * 
 */
package lazyj;

import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import lazyj.cache.ExpirationCache;

/**
 * This is the page cache.
 * 
 * @author costing
 * @since 2006-10-04
 */
public final class PageCache extends ExpirationCache<String, CachingStructure>{
	
	/**
	 * The minimum number of accesses required so that a page received a bonus
	 */
	static final int								BONUS_LIMIT	= 10;

	/**
	 * Cleanup thread, takes an entry that has expired, removes it from the cache and checks if it deserves a bonus.
	 */
	@Override
	protected void callbackOnExpiry(final String key, final CachingStructure cs){
		if (cs.iAccesses >= BONUS_LIMIT)
			CachingStructure.bonus.put(key, key);
		else
			CachingStructure.bonus.remove(key);
	}

	private PageCache(){
		super(10000);
	}
	
	private static final PageCache instance = new PageCache();
	
	/**
	 * Get the entrire contents of the cache.
	 * 
	 * @return a List of CachingStructure objects that is the entire contents of the memory cache.
	 */
	public static List<CachingStructure> getCacheContent() {
		final List<CachingStructure> l = instance.getValues();

		Collections.sort(l);
		Collections.reverse(l);

		return l;
	}

	/**
	 * Try to get the contents for a given key.	
	 * 
	 * @param sKey
	 * @return the structure for a given key
	 */
	static CachingStructure getCache(final String sKey) {
		return instance.get(sKey);
	}

	/**
	 * Get the contents generating the key on the fly from the request and the extra modifiers
	 * 
	 * @param request
	 * @param sCacheKeyModifier
	 * @return the structure, if exists, or null
	 */
	public static CachingStructure get(final HttpServletRequest request, final String sCacheKeyModifier){
		return getCache(getCacheKey(request, sCacheKeyModifier));
	}
	
	/**
	 * Package protected, should be used only by {@link ExtendedServlet} to add contents to the page cache
	 * 
	 * @param cs the structure to cache
	 */
	static void put(final CachingStructure cs) {
		instance.put(cs.sKey, cs, cs.lifetime);
	}
	
	/**
	 * Clear all the cache structures (in case of major changes to the templates ...)
	 * 
	 * @since 1.0.2
	 */
	public static void clear(){
		instance.refresh();
		CachingStructure.bonus.clear();
	}
	
	/**
	 * Uniform method to generate the caching key.
	 * 
	 * @param request
	 * @param sCacheKeyModifier
	 * @return the cache key for this request
	 */
	static String getCacheKey(final HttpServletRequest request, final String sCacheKeyModifier){
		final StringBuffer sb = javax.servlet.http.HttpUtils.getRequestURL(request);

		if (request.getQueryString() != null)
			sb.append('?').append(request.getQueryString());

		if (sCacheKeyModifier!=null && sCacheKeyModifier.length()>0)
			sb.append('\t').append(sCacheKeyModifier);

		return sb.toString();
	}
	
	/**
	 * Cache some arbitrary contents
	 * 
	 * @param request incoming request
	 * @param sCacheKeyModifier 
	 * @param vbContent
	 * @param lLifetime
	 * @param sContentType
	 * @return the newly created structure
	 */
	public static CachingStructure put(final HttpServletRequest request, final String sCacheKeyModifier, final byte[] vbContent, final long lLifetime, final String sContentType){
		final String sKey = getCacheKey(request, sCacheKeyModifier);
		
		final CachingStructure cs = new CachingStructure(sKey, vbContent, lLifetime, sContentType);
		
		put(cs);
		
		return cs;
	}
}
