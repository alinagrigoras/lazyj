/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>csv</i> tag escapes the double quotes in CSV fields
 * 
 * @author costing
 * @since 2014-02-05
 */
public final class CSV implements StringFormat {

	/**
	 * Convert a string to a CSV-safe representation, escaping the double quotes.
	 *  
	 * @param sTag ignored
	 * @param sOption always "csv"
	 * @param s value to encode
	 * @return csv-safe representation of the original string
	 */
	@Override
	public String format(final String sTag, final String sOption,final String s) {
		return Format.replace(s, "\"", "\\\""); //$NON-NLS-1$ //$NON-NLS-2$
	}

}
