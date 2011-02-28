/**
 * 
 */
package lazyj;

import java.lang.ref.WeakReference;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletResponse;

/**
 * Holding class for page cache entries
 * 
 * @author costing
 * @since 2006-10-04
 */
public final class CachingStructure implements Comparable<CachingStructure> {
	/**
	 * The time when this cache entry was generated
	 */
	public final long		lGenerated	= System.currentTimeMillis();
	
	/**
	 * Lifetime, in milliseconds, from the moment of its registration in the PageCache.
	 */
	public final long		lifetime;

	/**
	 * Cached content
	 */
	private final byte[]	content;

	/**
	 * Unique key to access this content
	 */
	public final String		sKey;

	/**
	 * Number of accesses to this cache entry.
	 */
	public int				iAccesses	= 0;
		
	/**
	 * Content length, in bytes
	 */
	private final int		iOriginalLength;
	
	/**
	 * Whether or not the content is kept compressed in memory
	 */
	private final boolean	isCompressed;
	
	/**
	 * Content type (defaults to text/html)
	 */
	public final String		sContentType;
	
	/**
	 * Keep a weak reference to the uncompressed contents 
	 */
	private WeakReference<byte[]> 	wrUncompressed = null;
	
	/**
	 * Keep a week reference to the contents as String
	 */
	private WeakReference<String>	wrStringContent = null;

	/**
	 * If one page had a lot of accesses (more than {@link PageCache#BONUS_LIMIT}) in its lifetime it will receive a bonus at the next run
	 * (eg. it will receive twice as much lifetime as it should normally have).
	 */
	static final ConcurrentHashMap<String, String>	bonus		= new ConcurrentHashMap<String, String>();
	
	/**
	 * Create a caching structure with all the possible fields. This constructor is package protected
	 * because only classes from this package can store/query it any way (security reason?)
	 * 
	 * @param sCacheKey unique key that identifies the request (URL + parameters + session + ...)
	 * @param vbContent cached content
	 * @param lLifetime expiration time of the cached content, in millis. See {@link ExtendedServlet#getMaxRunTime()}
	 * @param sContentType content type
	 */
	CachingStructure(final String sCacheKey, final byte[] vbContent, final long lLifetime, final String sContentType){
		this.sKey = sCacheKey;
		this.iOriginalLength = vbContent.length;
		this.lifetime = lLifetime;
		this.sContentType = sContentType;
		
		if (vbContent.length>3000){
			this.content = Utils.compress(vbContent);
			this.isCompressed = true;
			this.wrUncompressed = new WeakReference<byte[]>(vbContent);
		}
		else{
			this.content = vbContent;
			this.isCompressed = false;
		}
	}
	
	/**
	 * Get the contents.
	 * 
	 * @return the contents of the cache
	 */
	public byte[] getContent(){
		this.iAccesses++;
		
		if (this.isCompressed){
			byte[] uncompressed = this.wrUncompressed.get();
			
			if (uncompressed!=null)
				return uncompressed;
			
			uncompressed = Utils.uncompress(this.content);
			
			this.wrUncompressed = new WeakReference<byte[]>(uncompressed);
			
			return uncompressed;
		}
		
		return this.content;
	}
	
	/**
	 * Set the proper HTTP headers for this cached content
	 * 
	 * @param response object to set the headers on
	 */
	public void setHeaders(final HttpServletResponse response){
		response.setContentType(this.sContentType);
		response.setHeader("Content-Language", "en"); //$NON-NLS-1$ //$NON-NLS-2$
		response.setContentLength(length());
		
		RequestWrapper.setCacheTimeout(response, (int) ((this.lGenerated+this.lifetime-System.currentTimeMillis())/1000));
	}
	
	/**
	 * @return the String representation of this cache entry. Useful for JSPs where we anyway
	 * 			have to convert to String. This method caches the returned value so it's more
	 * 			efficient to use it instead of <code>new String(getContent())</code> each time
	 */
	public String getContentAsString(){
		String sContent;
		
		if (this.wrStringContent==null || (sContent = this.wrStringContent.get())==null){
			sContent = new String(getContent());
			this.wrStringContent = new WeakReference<String>(sContent); 
		}
		
		return sContent;
	}
	
	/**
	 * For sorting first by the number of accesses then by the key, then by expiration time
	 * @param cs object to compare to
	 * @return sort order
	 */
	public int compareTo(final CachingStructure cs) {
		int diff = this.iAccesses - cs.iAccesses;

		if (diff != 0)
			return diff;

		diff = (int) (this.lGenerated + this.lifetime - cs.lGenerated - cs.lifetime);
		
		if (diff != 0)
			return diff;
		
		// last resort, key (for sure unique)
		return this.sKey.compareTo(cs.sKey);
	}

	/**
	 * Whether or not two objects are in fact the same structure
	 * 
	 * @param o object to compare to
	 * @return true if the two objects are the same, false otherwise
	 */
	@Override
	public boolean equals(final Object o) {
		if (o==null || !(o instanceof CachingStructure))
			return false;
		
		return compareTo((CachingStructure) o) == 0;
	}

	/**
	 * Rewrite the default hash distribution method 
	 * 
	 * @return hash code of the key combined with the number of accesses
	 */
	@Override
	public int hashCode() {
		return this.sKey.hashCode() * 31 + this.iAccesses;
	}

	/**
	 * Get the size of the cached content. Beware that the actual content might be compressed in memory to save space.
	 * 
	 * @return size of the cached data entry
	 */
	public int length(){
		return this.iOriginalLength;
	}
	
	/**
	 * Get the memory footprint of the cached content. Might be less than the original size if a compression was applied.
	 * 
	 * @return original content size
	 */
	public int getRealSize(){
		return this.content!=null ? this.content.length : -1;
	}
	
	/**
	 * Overriden toString() produces a nicer HTML output, for web statistics
	 */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		final String s = this.sKey.length() > 70 ? this.sKey.substring(0, 70)+"..." : this.sKey;

		final StringTokenizer st = new StringTokenizer(this.sKey);
		
		final String sURL = st.nextToken();
		
		return "<tr><td align=left>" + 
						"<a target=_blank href='"+sURL+"' title='"+sURL+"'>"+s +"</a>"+ 
					"</td><td align=right>" + 
						this.iAccesses + 
					"</td><td align=right>" + 
						(this.lGenerated + this.lifetime - System.currentTimeMillis()) / 1000 + 
					"</td><td align=right>" + 
						(CachingStructure.bonus.get(this.sKey) != null) + 
					"</td><td align=right>" + 
						getRealSize()+" / "+length()+" ("+(length()>0 ? ""+(getRealSize()*100)/length() : "na")+
					"%)</td><td align=right>" + 
						this.sContentType + 
					"</td></tr>";
	}
}
