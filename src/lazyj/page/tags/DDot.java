/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>ddot</i> or <i>ddotN</i> show nice double values, with a given number of digits after the point
 * (when not specified N is by default 2) and groups of 3 digits separated by comma. 
 * 
 * @author costing
 * @since 2006-10-13
 * @see Dot
 * @see Size
 */
public final class DDot implements StringFormat {

	/**
	 * Show nice double values, with a given number of digits after the point.
	 * 
	 * @param sTag ignored
	 * @param sOption "ddotN", with N>0 or simply "ddot" and N is assumed = 2 
	 * @param s string to format, should be a double
	 * @return the formatted value
	 * @see Format#showDottedDouble(double, int)
	 */
	public String format(final String sTag, final String sOption, final String s) {
		try {
			int iDDot = sOption!=null && sOption.length()>4 ? Integer.parseInt(sOption.substring(4)) : 2;
			
			return Format.showDottedDouble(Double.parseDouble(s), iDDot);
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
