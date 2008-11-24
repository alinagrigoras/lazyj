/**
 * 
 */
package lazyj.widgets;

import lazyj.DBFunctions;

/**
 * Simple tree provider, having integer keys and string values. 
 * The simplest constructor takes first three columns of each database row as (key, parent, value).
 * 
 * @author costing
 * @since Nov 26, 2007 (1.0.4)
 */
public class BasicDBTreeProvider extends DBTreeProvider<Integer, String> {

	private final String sIDColumn;
	private final String sParentColumn;
	private final String sValueColumn;
	
	/**
	 * Build the tree options taking from each database row:<br><ul>
	 * <li>first column: node id == option key</li>
	 * <li>second column: parent id</li>
	 * <li>third column: value == displayed option string</li>
	 * </ul>
	 * 
	 * @param db
	 */
	public BasicDBTreeProvider(final DBFunctions db){
		this(db, null, null, null);
	}
	
	/**
	 * Build the tree of options taking the specified columns from each database row.
	 * 
	 * @param db
	 * @param sIDColumn
	 * @param sParentColumn
	 * @param sValueColumn
	 */
	public BasicDBTreeProvider(final DBFunctions db, final String sIDColumn, final String sParentColumn, final String sValueColumn){
		super(db);
		
		this.sIDColumn = sIDColumn;
		this.sParentColumn = sParentColumn;
		this.sValueColumn = sValueColumn;
	}

	public Integer getKey() {
		return Integer.valueOf(this.sIDColumn==null ? this.db.geti(1) : this.db.geti(this.sIDColumn));
	}

	public Integer getParent() {
		return Integer.valueOf(this.sParentColumn==null ? this.db.geti(2) : this.db.geti(this.sParentColumn));
	}

	public String getValue() {
		return this.sValueColumn==null ? this.db.gets(3) : this.db.gets(this.sValueColumn);
	}

}
