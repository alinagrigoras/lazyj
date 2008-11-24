/**
 * 
 */
package lazyj.page.tags;

import lazyj.page.StringFormat;

/**
 * <i>dash</i> replaces all non-alphanumeric sequences of characters each with a single dash. 
 * 
 * @author costing
 * @since 2006-10-13
 * @see Under
 */
public final class Dash implements StringFormat {

	/**
	 * Dashify a string (leave all the letters and digits, everything else is colapsed into '-')
	 * 
	 * @param sTag ignored
	 * @param sOption always "dash"
	 * @param sValue original string
	 * @return dashified version of the original string
	 */
	public String format(final String sTag, final String sOption, final String sValue) {
		return format(sValue);
	}

	/**
	 * Publicly available method to transform a string into a dashed representation of it, good to be 
	 * used in rewritten URLs.
	 * 
	 * @param sValue string to format
	 * @return dashified version of the string
	 */
	public static final String format(final String sValue){
		if (sValue==null || sValue.length()==0)
			return sValue;
		
		final StringBuilder sb2 = new StringBuilder(sValue.length());

		char cOld = '-';
		for (int k = 0; k < sValue.length(); k++) {
			char c = sValue.charAt(k);

			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
				sb2.append(c);
				cOld = c;
			} else {
				if (cOld != '-') {
					sb2.append('-');
					cOld = '-';
				}
			}
		}

		return sb2.toString();		
	}
	
}
