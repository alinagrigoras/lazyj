/**
 * 
 */
package lazyj.page.tags;

import java.text.SimpleDateFormat;

import lazyj.Format;
import lazyj.page.StringFormat;


/**
 * <i>date</i> or <i>dateFORMAT</i> decode the date from the value and show it in the specified format.
 * 
 * @author costing
 * @since 2006-10-13
 * @see NiceDate
 * @see Time
 */
public final class Date implements StringFormat {

	/**
	 * Implement the "date" tag. Parses the given string into a Date with {@link Format#parseDate(String)}
	 * then passes it to either {@link Format#showNamedDate(java.util.Date)}, if the tag is exactly "date" or
	 * to a {@link SimpleDateFormat} instance if the tag has something behind it, to see a nice value.<br>
	 * <br>
	 * Because tags cannot contain spaces, the formatting string is to be built with '_' instead of ' '. For example
	 * this could be a tag: <code>&lt;&lt;:fieldname dateyyyy-MM-dd_HH:mm:ss:&gt;&gt;</code> 
	 * 
	 * @param s string to format
	 * @param sTag tag, ignored
	 * @param sOption either "date" or "dateFORMAT", in {@link SimpleDateFormat} style
	 * @return the formatted string
	 * @see Format#showNamedDate(java.util.Date)
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
		
		if (sOption==null || sOption.length()<=4){
			return Format.showNamedDate(d);
		}
		
		final String sFormat = sOption.substring(4).replace('_', ' ');
		
		return (new SimpleDateFormat(sFormat)).format(d);
	}

}
