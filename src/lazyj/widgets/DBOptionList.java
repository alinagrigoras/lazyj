/**
 * 
 */
package lazyj.widgets;

import java.util.ArrayList;
import java.util.Collection;

import lazyj.DBFunctions;

/**
 * Get a list of option based on a simple query.
 * 
 * @author costing
 * @param <K> key type (usually Integer or String)
 * @param <V> value type (usually String)
 * @since Nov 26, 2007 (1.0.4)
 */
public abstract class DBOptionList<K,V> implements OptionProvider<K, V>{

	/**
	 * Database row.
	 */
	protected final DBFunctions db;
	
	/**
	 * Database query to build the list of options upon.
	 * 
	 * @param db
	 */
	public DBOptionList(final DBFunctions db){
		this.db = db;
	}
	
	/**
	 * Get the key of this option.
	 * @param dbRow database row
	 * 
	 * @return key
	 */
	protected abstract K getKey(DBFunctions dbRow);

	/**
	 * Get the value of this option
	 * @param dbRow database row
	 * 
	 * @return value
	 */
	protected abstract V getValue(DBFunctions dbRow);
	
	/**
	 * @author costing
	 * @since Jan 17, 2009
	 */
	private class DBOption implements Option<K, V>{

		/**
		 * Key
		 */
		private final K key;
		
		/**
		 * Value
		 */
		private final V value;
		
		/**
		 * @param key
		 * @param value
		 */
		DBOption(final K key, final V value){
			this.key = key;
			this.value = value;
		}
		
		public K getKey() {
			return this.key;
		}

		public int getLevel() {
			return 0;
		}

		public V getValue() {
			return this.value;
		}
		
	}
	
	public Collection<Option<K,V>> getOptions(){
		final Collection<Option<K, V>> ret = new ArrayList<Option<K,V>>();
		
		while (this.db.moveNext()){
			ret.add(new DBOption(getKey(this.db), getValue(this.db)));
		}
		
		return ret;
	}
	
}
