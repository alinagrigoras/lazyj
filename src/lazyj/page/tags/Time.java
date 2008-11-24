/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>time</i> decodes the value into a Date object then displays only the time part of it.
 * 
 * @author costing
 * @since 2006-10-14
 * @see Date
 * @see NiceDate
 */
public final class Time implements StringFormat {

    /**
     * Simple tag to nicely show the time part of a date. The original string is first parsed into a Date object
     * then formatted to show only the time from it.
     * 
     * @param sTag tag name, ignored
     * @param sOption always "time"
     * @param s original date / time to display
     * @return time representation
     * @see Format#parseDate(String)
     * @see Format#showTime(java.util.Date)
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
		
		return Format.showTime(d);
	}

}
