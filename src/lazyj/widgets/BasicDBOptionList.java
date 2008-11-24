/**
 * 
 */
package lazyj.widgets;

import lazyj.DBFunctions;

/**
 * A simple option list builder having string keys and values. 
 * The key and value are taken from database rows, by default first and second column.
 * 
 * @author costing
 * @since Nov 26, 2007 (1.0.4)
 */
public class BasicDBOptionList extends DBOptionList<String, String> {

	private final String sKeyColumn;
	private final String sValueColumn;
	
	/**
	 * Build a list of options taking the first column from the query as key (option id) and the 
	 * second column as value (displayed string).
	 * 
	 * @param db
	 */
	public BasicDBOptionList(final DBFunctions db){
		this(db, null, null);
	}
	
	/**
	 * Build a list of options taking from the given query specific columns as key / value.
	 * 
	 * @param db
	 * @param sKeyColumn
	 * @param sValueColumn
	 */
	public BasicDBOptionList(final DBFunctions db, final String sKeyColumn, final String sValueColumn){
		super(db);
		
		this.sKeyColumn = sKeyColumn;
		this.sValueColumn = sValueColumn;
	}
	
	@Override
	protected String getKey(final DBFunctions dbRow) {
		return this.sKeyColumn==null ? dbRow.gets(1) : dbRow.gets(this.sKeyColumn);
	}

	@Override
	protected String getValue(final DBFunctions dbRow) {
		return this.sValueColumn==null ? dbRow.gets(2) : dbRow.gets(this.sValueColumn);
	}

}
