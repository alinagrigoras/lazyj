/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>esc</i> tag produces an HTML-safe encoding of the value.
 * 
 * @author costing
 * @since 2006-10-13
 * @see Enc
 * @see JS
 */
public final class Esc implements StringFormat {

	/**
	 * Convert a string into a HTML-safe representation of it. Use this when you are about to display strings
	 * that might contain special html characters.
	 * 
	 * @param sTag ignored
	 * @param sOption always "esc"
	 * @param s value to encode
	 * @return html-safe representation of the original string
	 * @see Format#escHtml(String)
	 */
	@Override
	public String format(final String sTag, final String sOption,final String s) {
		return Format.escHtml(s);
	}

}
