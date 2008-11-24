package lazyj.cache;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import lazyj.DBFunctions;

/**
 * Create a cache based on the resultset produced by a DB query.
 * 
 * @author costing
 * @param <K> key type
 * @param <V> value type
 * @since 2007-03-08
 */
public abstract class GenericQueryCacher<K, V> implements CacheElement<K, V>, ReversableMultivaluesCache<K, V>, ReversableUniqueCache<K, V> {
	
	/**
	 * Get a query loaded with a query that generates the key on the first column and the value on the second one.
	 * 
	 * @return a DBFunctions object loaded with a query
	 */
	abstract protected DBFunctions getDB();
	
	/**
	 * Get the key object from the current DB row
	 * 
	 * @param db
	 * @return key object
	 */
	abstract protected K readKey(DBFunctions db);
	
	/**
	 * Get the value object from the current DB row
	 * 
	 * @param db
	 * @return value object
	 */
	abstract protected V readValue(DBFunctions db);
	
	/**
	 * Cache container
	 */
	protected Map<K, V> mValues = null;
	
	/**
	 * Reverse values
	 */
	protected Map<V, List<K>> mReverse = null;
	
	public final V get(final K key) {
		return this.mValues.get(key);
	}
	
	public final K getKeyForValue(final V value){
		final List<K> c = this.mReverse.get(value);
		
		return c!=null && c.size()>0 ? c.get(0) : null;
	}

	public final List<K> getKeysForValue(final V value){
		return this.mReverse.get(value);
	}
	
	/**
	 * Get the key set
	 * 
	 * @return the key set
	 */
	public Collection<K> keySet(){
		return this.mValues.keySet();
	}
	
	/**
	 * Get the values collection
	 * 
	 * @return collection of values
	 */
	public Collection<V> getValues(){
		return this.mValues.values();
	}
	
	/**
	 * Get the number of values in the forward map
	 * 
	 * @return number of values
	 */
	public int size(){
		return this.mValues.size();
	}
	
	public final void refresh() {
		final Map<K, V> mTemp = new TreeMap<K, V>();
		
		final Map<V, List<K>> mRevTemp = new TreeMap<V, List<K>>();
		
		final DBFunctions db = getDB();
		
		while (db.moveNext()){
			final K key = readKey(db);
			final V val = readValue(db);
						
			mTemp.put(key, val);
			
			List<K> c = mRevTemp.get(val);
			
			if (c==null){
				c = new LinkedList<K>();
				mRevTemp.put(val, c);
			}
			
			c.add(key);
		}
		
		this.mValues = mTemp;
		this.mReverse = mRevTemp;
	}

}
