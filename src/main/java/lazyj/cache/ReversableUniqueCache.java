package lazyj.cache;


/**
 * Most of the cache entries are reversible, eg if an ID is associated to a unique name you usually need
 * two way queries: "give me the name for this ID" and "give me the ID for this name". 
 * 
 * @author costing
 * @param <K> key type
 * @param <V> value type
 * @since 2007-03-11
 */
public interface ReversableUniqueCache<K, V> {

		/**
		 * Reverse query, get the key associated with a value.
		 * 
		 * @param value
		 * @return the key, if it exists, for this value
		 */
		public K getKeyForValue(V value);
	
}
