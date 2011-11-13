/**
 * 
 */
package lazyj.widgets;

import lazyj.DBFunctions;

/**
 * Implementation of {@link TreeProvider} that takes a database query as argument.
 * 
 * @author costing
 * @param <K> key type (usually Integer)
 * @param <V> value type (usually String)
 * @since Nov 26, 2007 (1.0.4)
 */
public abstract class DBTreeProvider<K,V> implements TreeProvider<K,V> {

	/**
	 * Database row.
	 */
	protected final DBFunctions db;
	
	/**
	 * Constructor based on a database query. The parameter should be a SELECT database query result.
	 * 
	 * @param db
	 */
	public DBTreeProvider(final DBFunctions db){
		this.db = db;
		
		db.moveNext();
	}
	
	/* (non-Javadoc)
	 * @see lazyj.widgets.TreeProvider#moveNext()
	 */
	@Override
	public boolean moveNext() {
		return this.db.moveNext();
	}

}
