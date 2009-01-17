/**
 * 
 */
package lazyj;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Map with restricted size and LRU behavior (when the size is reached the oldest unused
 * entry is deleted to make some space for the new entry).<br>
 * <br>
 * The implementation is based on LinkedHashMap, thus it is not thread safe. Remember
 * {@link java.util.Collections#synchronizedMap}. 
 * 
 * @author costing
 * @param <K> Key type
 * @param <V> Value type
 * @since Oct 26, 2007 (1.0.2)
 */
public class LRUMap<K, V> extends LinkedHashMap<K, V> {
	/**
	 * serial version
	 */
	private static final long	serialVersionUID	= 2470194500885712833L;

	/**
	 * default load factor
	 */
	private static final float	DEFAULT_FACTOR				= 0.75f;

	/**
	 * how many entries to allow
	 */
	private final int			iCacheSize;

	/**
	 * @param iCacheSize How many entries can be in this map at maximum. A negative value means "unlimited" size.
	 */
	public LRUMap(final int iCacheSize) {
		this(iCacheSize, DEFAULT_FACTOR);
	}
		
	/**
	 * @param iCacheSize How many entries can be in this map at maximum. A negative value means "unlimited" size.
	 * @param fFactor Fill factor for the underlying LinkedHashMap
	 */
	public LRUMap(final int iCacheSize, final float fFactor){
		super(32, fFactor, true);

		this.iCacheSize = iCacheSize;
	}

	/**
	 * Get the maximum number of elements that will be stored in the cache
	 * 
	 * @return size limit
	 */
	public int getLimit(){
		return this.iCacheSize;
	}
	
	@Override
	protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
		return this.iCacheSize>=0 && size() > this.iCacheSize;
	}
}
