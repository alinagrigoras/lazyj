/**
 * 
 */
package lazyj.widgets;

/**
 * Each <i>option</i> is identified by a key (unique or not) and a value that is to be displayed.
 * The key is what will be sent to the server. The optional level will be used to indent options, 
 * for example when you display a tree (categories...).
 * 
 * @author costing
 * @param <K> key type (usually Integer or String)
 * @param <V> value type (usually String)
 * @since Nov 26, 2007 (1.0.4)
 */
public interface Option<K,V> {

	/**
	 * Get the identifier for this entry
	 * 
	 * @return id
	 */
	public K getKey();
	
	/**
	 * Get the value to be displayed
	 * 
	 * @return value
	 */
	public V getValue();
	
	/**
	 * Indentation level (&gt;= 0)
	 * 
	 * @return level
	 */
	public int getLevel();
	
}
