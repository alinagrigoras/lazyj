/**
 * 
 */
package lazyj.page.tags;

import lazyj.page.StringFormat;

/**
 * <i>csv</i> tag escapes the double quotes in CSV fields
 * 
 * @author costing
 * @since 2014-02-05
 */
public final class Round implements StringFormat {

	/**
	 * Rounds a floating point value
	 *  
	 * @param sTag ignored
	 * @param sOption always "round"
	 * @param s value to encode
	 * @return rounded value of the given floating point value, or the empty string if the number cannot be parsed
	 */
	@Override
	public String format(final String sTag, final String sOption,final String s) {
		try{
			double d = Double.parseDouble(s);
			
			return String.valueOf(Math.round(d));
		}
		catch (final NumberFormatException nfe){
			return ""; //$NON-NLS-1$
		}
	}

}
