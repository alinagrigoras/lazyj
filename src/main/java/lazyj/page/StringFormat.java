/**
 * 
 */
package lazyj.page;

/**
 * Interface that any string formatting class able to process {@link BasePage} tag options must implement.
 * 
 * @author costing
 * @since 2006-10-15
 */
public interface StringFormat {

	/**
	 * This is the only method that must be implemented. It will be called by {@link BasePage} when it encounters an 
	 * option that has this class associated with it.
	 * 
	 * @param sTag tag name 
	 * @param sOption the exact option that triggered this call. Can be <code>null</code> !
	 * @param s string to format
	 * @return the method must return the formatted string
	 */
	public String format(String sTag, String sOption, String s);
	
}
