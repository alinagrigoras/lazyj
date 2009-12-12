/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>interval</i> decodes the value into a time interval and shows it in a human readable format
 * 
 * @author costing
 * @since 2006-10-14
 * @see Date
 * @see NiceDate
 */
public final class Interval implements StringFormat {

    /**
     * Simple tag to nicely show the time part of a date. The original string is first parsed into a Date object
     * then formatted to show only the time from it.
     * 
     * @param sTag tag name, ignored
     * @param sOption interval(ms|s|m|h|d|w|mo|y)
     * @param s original date / time to display
     * @return time representation
     * @see Format#toInterval(long)
     */
	public String format(final String sTag, final String sOption, final String s) {
		if (s==null || s.length()==0){
			// empty string
			return s;
		}

		try{
			double d = Double.parseDouble(s);
			
			// "interval"
			if (sOption==null || sOption.length()<=8){
				d *= 1000;
			}
			else{
				final String o = sOption.substring(8);
				
				if (o.equals("s")) d *= 1000L;
				else if (o.equals("m")) d *= 1000L*60;
				else if (o.equals("h")) d *= 1000L*60*60;
				else if (o.equals("d")) d *= 1000L*60*60*24;
				else if (o.equals("w")) d *= 1000L*60*60*24*7;
				else if (o.equals("mo"))d *= 1000L*60*60*24*30;
				else if (o.equals("y")) d *= 1000L*60*60*24*365;
			}
			
			return Format.toInterval((long) d);
		}
		catch (NumberFormatException nfe){
			// ignore
		}

		return null;
	}

}
