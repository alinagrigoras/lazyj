/**
 * 
 */
package lazyj.widgets;

import lazyj.DBFunctions;

/**
 * Provide nodes for the {@link Tree}.
 * 
 * @author costing
 * @param <K> key type (usually Integer)
 * @param <V> value type (usually String)
 * @since Nov 26, 2007 (1.0.4)
 */
public interface TreeProvider<K,V>{

	/**
	 * Get the key object (unique identifier of the node).
	 * 
	 * @return key
	 */
	public K getKey();
	
	/**
	 * Get the parent id (should match the {@link #getKey()} for another entry).
	 * 
	 * @return parent key
	 */
	public K getParent();
	
	/**
	 * Get the value that is to be displayed for this node.
	 * 
	 * @return value
	 */
	public V getValue();

	/**
	 * Go to the next entry in the list. Take care if you use {@link DBFunctions#moveNext()} to
	 * implement this function because the first call is dummy on database rows while here it 
	 * is expected to actually do something from the first call. The easy way out is to do a 
	 * <code>moveNext</code> on the database result after executing the query and before building
	 * the tree from it.
	 * 
	 * @return true if move was possible
	 * @see DBFunctions#moveNext()
	 */
	public boolean moveNext();	
}
