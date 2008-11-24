/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>nicedate</i> translates the value (a date) into a nice human readable form.
 * 
 * @author costing
 * @since 2006-10-13
 * @see Date
 * @see Time
 */
public final class NiceDate implements StringFormat {

	/**
	 * Transform a date into something user-friendly ("today", "yesterday" ...)
	 * 
	 * @param sTag tag name, ignored
	 * @param sOption always "nicedate"
	 * @param s date representation
	 * @return nice representation of the given date
	 * @see Format#showNiceDate(java.util.Date)
	 * @see Format#parseDate(String)
	 */
	public String format(final String sTag, final String sOption, final String s) {		
		if (s==null || s.length()==0){
			// empty string
			return s;
		}
		
		final java.util.Date d = Format.parseDate(s);
		
		if (d==null){
			// unrecognized date format
			return null;
		}
		
		return Format.showNiceDate(d);
	}

}
