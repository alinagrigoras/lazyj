/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>uenc</i> decodes a previously URL-encoded string. 
 * 
 * @author costing
 * @since 2006-10-15
 * @see Enc
 */
public final class UEnc implements StringFormat {

	/**
	 * Reverse function of {@link Enc}, takes a previously URL encoded string and shows it normally.
	 * 
	 * @param sTag tag name, ignored
	 * @param sOption always "uenc"
	 * @param s URL encoded string
	 * @return normal string
	 * @see Format#decode(String)
	 */
	public String format(final String sTag, final String sOption, final String s) {
		return Format.decode(s);
	}

}
