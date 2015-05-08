/**
 * 
 */
package lazyj.page.tags;

import lazyj.page.StringFormat;

/**
 * <i>under</i> replaces all non-alphanumeric sequences of characters each with a single underscore. 
 * 
 * @author costing
 * @since 2006-10-14
 * @see Dash
 */
public final class Under implements StringFormat {

	/**
	 * Replace non-alpha-numeric character sequences with a single underscore character. This is to be used
	 * in conjunction with url rewriting, when putting arbitrary strings in the url.
	 * 
	 * @param sTag tag name, ignored
	 * @param sOption always "under"
	 * @param sValue string to transform
	 * @return original string with underscore instead of any non-alpha-numeric characters 
	 * @see #format(String)
	 */
	@Override
	public String format(final String sTag, final String sOption, final String sValue) {
		return format(sValue);
	}
	
	/**
	 * Static method to replace non-alpha-numeric character sequences with a single underscore character.
	 * 
	 * @param sValue string to transform.
	 * @return original string with underscore instead of any non-alpha-numeric characters 
	 */
	public static String format(final String sValue) {
		if (sValue==null || sValue.length()==0)
			return sValue;
		
		final StringBuilder sb2 = new StringBuilder(sValue.length());

		char cOld = '_';
		for (int k = 0; k < sValue.length(); k++) {
			char c = sValue.charAt(k);

			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
				sb2.append(c);
				cOld = c;
			} else {
				if (cOld != '_') {
					sb2.append('_');
					cOld = '_';
				}
			}
		}

		return sb2.toString();
	}

}
