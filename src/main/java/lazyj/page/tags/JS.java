/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>js</i> tag produces an JavaScript-safe encoding of the value.
 * 
 * @author costing
 * @since 2006-10-13
 * @see Enc
 * @see Esc
 */
public final class JS implements StringFormat {

	/**
	 * Make a string usable in a JS script, as a string value. Use this when you are about to initialize a string
	 * in a JS with a value that might contain special characters.
	 * 
	 * @param sTag tag name, ignored
	 * @param sOption always "js"
	 * @param s value to transform
	 * @return js-safe representation
	 * @see Format#escJS(String)
	 */
	@Override
	public String format(final String sTag, final String sOption, final String s) {
		return Format.escJS(s);
	}

}
