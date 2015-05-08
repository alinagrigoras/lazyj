/**
 * 
 */
package lazyj.page;

/**
 * Very simplistic definition of a page. It is something that returns a bit of content.
 * 
 * @author costing
 * @since Nov 26, 2007 (1.0.4)
 */
public interface Page {
	
	/**
	 * Get the (dynamic) content.
	 * 
	 * @return content
	 */
	public StringBuilder getContents();
	
}
