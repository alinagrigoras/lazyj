/**
 * 
 */
package lazyj.page.tags;

import lazyj.Utils;
import lazyj.page.StringFormat;

/**
 * <i>checked</i> returns "checked" when the value, as integer, is &gt; 0. Great for quick building
 * of forms with checkboxes that have integers behind them in the database.
 * 
 * @author costing
 * @since 2006-10-13
 */
public final class Checked implements StringFormat {

	/**
	 * If the integer value of this string is positive, return the string "checked", otherwise ""
	 * 
	 * @param sTag tag name, ignored
	 * @param sOption option ("check"), ignored 
	 * @param s string to format
	 * @return "checked" or "", depending on the integer value of the string.
	 */
	public String format(final String sTag, final String sOption, final String s) {
		int iValue = 0;
		try{
			iValue = Integer.parseInt(s);
		}
		catch (NumberFormatException ne){
			// ignore
		}
			
		final boolean b = iValue>0 || Utils.stringToBool(s, false);
		
		return b ? "checked" : "";  //$NON-NLS-1$//$NON-NLS-2$
	}

}
