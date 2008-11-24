/**
 * 
 */
package lazyj.page;

/**
 * @author costing
 * @since Jan 18, 2008 (1.0.5)
 */
public interface TemplatePage extends Page{
	
	/**
	 * Append the value of some object to a tag. One can specify if the value is inserted in the front
	 * of any existing text or at the end of it.
	 * 
	 * @param tag Tag name. Cannot be <code>null</code>.
	 * @param value Value to display instead of the tag.
	 * @param beginning <code>true</code> to insert in the front of existing text, <code>false</code> to append it at the end.
	 */
	public void append(final String tag, final Object value, final boolean beginning);
	
}
