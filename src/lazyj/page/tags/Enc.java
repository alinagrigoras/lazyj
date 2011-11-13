/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>enc</i> tag produces an URL-safe encoding of the value.
 * 
 * @author costing
 * @since 2006-10-13
 * @see Esc
 * @see JS
 * @see UEnc
 */
public final class Enc implements StringFormat {

	/**
	 * Make a string as to be put into an URL
	 * 
	 * @param sTag tag name, ignored
	 * @param sOption tag option, ignored
	 * @param s value to encode
	 * @return the URL encoding for this string
	 * @see Format#encode(String)
	 */
	@Override
	public String format(final String sTag, final String sOption, final String s) {
		return Format.encode(s);
	}

}
