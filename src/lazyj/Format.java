package lazyj;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * Utility class providing lots of useful or less useful functions. 
 * 
 * @author costing
 * @since 2006-10-13
 */
public final class Format {

	/**
	 * Nicely show the size of a file. Something like 12.1KB, 0.75MB ...
	 * 
	 * @param dim file size, in bytes
	 * @return the nice human-readable size
	 */
	public static String size(final long dim) {
		return size(dim, "B");
	}
	
	/**
	 * Nicely show a size, starting from a given measurement unit.
	 * 
	 * @param size file size, in the specified unit
	 * @param unit unit. Can be nothing(==B) or one of the following: B, K, M, G, T, P
	 * @return nice human-readable size
	 * @since 1.0.5
	 */
	public static String size(final long size, final String unit){
		final double dDiv = 1024;
		
		String sSize = unit!=null ? unit.toUpperCase(Locale.getDefault()) : "B";

		final boolean bMinus = size<0;
		
		double d = Math.abs(size);
		
		while (d > dDiv) {
			d /= dDiv;

			if (sSize.equals("") || sSize.equals("B"))
				sSize = "K";
			else if (sSize.equals("K"))
				sSize = "M";
			else if (sSize.equals("M"))
				sSize = "G";
			else if (sSize.equals("G"))
				sSize = "T";
			else if (sSize.equals("T"))
				sSize = "P";
			else if (sSize.equals("P"))
				sSize = "X";
		}

		while (d < 0.1d && sSize.length() > 0 && !sSize.equals("B")) {
			d *= dDiv;

			switch (sSize.charAt(0)) {
				case 'X':
					sSize = "P";
					break;
				case 'P':
					sSize = "T";
					break;
				case 'T':
					sSize = "G";
					break;
				case 'G':
					sSize = "M";
					break;
				case 'M':
					sSize = "K";
					break;
				case 'K':
					sSize = "B";
					break;
			}
		}

		if (dDiv < 1024d && sSize.equals("B"))
			sSize = "b";

		String sRez = point(d);
		
		if (bMinus && !sRez.equals("0"))
			sRez = "-"+sRez;
		
		if (!sSize.toLowerCase(Locale.getDefault()).equals("b"))
			sSize += "B";

		return sRez + (sSize.equals("") ? "" : (" " + sSize));
	}
	
	/**
	 * Show a nice number, with the number of decimal places chosen automatically depending
	 * on the number to format 
	 * 
	 * @param number
	 * @return nice floating point number representation
	 * @since 1.0.5
	 */
	public static String point(final double number) {
		String sRez;

		final boolean bMinus = number<0;
		
		double d = Math.abs(number);
		
		if (d < 10) {
			d = Math.round(d * 1000) / 1000.0d;
			sRez = "" + d;
		} else if (d < 100) {
			d = Math.round(d * 100) / 100.0d;
			sRez = "" + d;
		} else if (d < 1000) {
			d = Math.round(d * 10) / 10.0d;
			sRez = "" + d;
		} else {
			sRez = "" + ((long) d);
		}

		while (sRez.indexOf(".") >= 0 && sRez.endsWith("0")) {
			sRez = sRez.substring(0, sRez.length() - 1);
		}

		if (sRez.endsWith("."))
			sRez = sRez.substring(0, sRez.length() - 1);

		if (bMinus && !sRez.equals("0"))
			sRez = "-"+sRez;
		
		return sRez;
	}

	
	/**
	 * From a full mail address (eg. "Full Name <account@server.com>") try to extract the full name. If it
	 * cannot extract the full name it will return the mail address.
	 * 
	 * @param address full address 
	 * @return the name of the person
	 */
	public static String extractMailTitle(final String address) {
		if (address==null || address.length()==0)
			return address;
		
		final StringTokenizer st = new StringTokenizer(address, "<>\"'`");

		if (st.hasMoreTokens())
			return st.nextToken().trim();
		
		return address;
	}

	/**
	 * From a full mail address (eg. <i>"Full Name" &lt;account@server.com&gt;</i>) try to extract the email address.
	 * 
	 * @param address full address 
	 * @return the address part or <code>null</code> if there is no email part in it
	 */
	public static String extractAddress(final String address) {
		if (address==null || address.length()==0)
			return null;
		
		final StringTokenizer st = new StringTokenizer(address, "<>\"'`");

		String s = null;
		
		while (st.hasMoreTokens()) {
			String sTemp = st.nextToken();
			
			if (sTemp.indexOf('@')>0)
				s = sTemp.trim();
		}

		return s;
	}


	/**
	 * Transform a text into an HTML-safe string.
	 * 
	 * @param text original text 
	 * @return the HTML-safe version of the text
	 */
	public static String escHtml(final String text) {
		if (text==null || text.length()==0)
			return text;
		
		final char[] vc = text.toCharArray();
		final int l = vc.length;

		final StringBuilder sb = new StringBuilder(l + 30);

		char c;

		for (int i = 0; i < l; i++) {
			c = vc[i];
			switch (c) {
				case '&':
					sb.append("&amp;");
					break;
				case '<':
					sb.append("&lt;");
					break;
				case '>':
					sb.append("&gt;");
					break;
				case '\"':
					sb.append("&quot;");
					break;
				default:
					sb.append(c);
			}
		}

		return sb.toString();
	}

	/**
	 * Produce an URL encoding of the given text, using the UTF-8 charset 
	 * 
	 * @param text text to encode
	 * @return URL-safe version of the text
	 */
	public static String encode(final String text) {
		try {
			return URLEncoder.encode(text, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	/**
	 * Decode an URL-encoded string, using the UTF-8 charset.
	 * 
	 * @param text text to decode
	 * @return plain text version
	 */
	public static String decode(final String text) {
		try {
			return URLDecoder.decode(text, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	private static final char[]	ESC_SLASH	= new char[] { '\\', '\\' };

	private static final char[]	ESC_QUOTE	= new char[] { '\'', '\'' };

	private static final char[]	JS_QUOTE	= new char[] { '\\', '\'' };

	private static final char[]	ESC_DQUOTE	= new char[] { '\\', '"' };

	private static final char[]	ESC_N		= new char[] { '\\', 'n' };

	private static final char[]	ESC_R		= new char[] { '\\', 'r' };

	private static final char[]	ESC_0		= new char[] { '\\', '0' };

	/**
	 * Create a SQL-safe version of the given text, to be embedded into SQL queries
	 * 
	 * @param text original text
	 * @return SQL-safe version of the text
	 */
	public static String escSQL(final String text) {
		if (text==null || text.length()==0)
			return "";
		
		final char[] vc = text.toCharArray();
		final int l = vc.length;

		final StringBuilder sb = new StringBuilder(l + 30);

		char c;

		for (int i = 0; i < l; i++) {
			c = vc[i];
			switch (c) {
				case '\\':
					sb.append(ESC_SLASH);
					break;
				case '\'':
					sb.append(ESC_QUOTE);
					break;
				case '\"':
					sb.append(ESC_DQUOTE);
					break;
				case '\n':
					sb.append(ESC_N);
					break;
				case '\r':
					sb.append(ESC_R);
					break;
				case (char) 0:
					sb.append(ESC_0);
					break;
				default:
					sb.append(c);
			}
		}

		return sb.toString();
	}

	/**
	 * Create a JS-safe string representation. This is useful when you want to pass a text to a dynamic string
	 * variable in the final HTML document.
	 * 
	 * @param text original text
	 * @return JS-string-safe version
	 */
	public static String escJS(final String text) {
		if (text==null || text.length()==0)
			return "";
		
		final char[] vc = text.toCharArray();
		final int l = vc.length;

		final StringBuilder sb = new StringBuilder(l + 30);

		char c;

		for (int i = 0; i < l; i++) {
			c = vc[i];
			switch (c) {
				case '\\':
					sb.append(ESC_SLASH);
					break;
				case '\'':
					sb.append(JS_QUOTE);
					break;
				case '\"':
					sb.append(ESC_DQUOTE);
					break;
				case '\n':
					sb.append(ESC_N);
					break;
				case '\r':
					sb.append(ESC_R);
					break;
				case (char) 0:
					sb.append(ESC_0);
					break;
				default:
					sb.append(c);
			}
		}

		return sb.toString();
	}

	private static volatile int	iOldReplacements1	= 2;

	private static volatile int	iOldReplacements2	= 2;

	/**
	 * Replace a sequence of text with another sequence in an original string
	 * 
	 * @param s original text
	 * @param sWhat what to search and replace
	 * @param sWith the new text to put in place
	 * @return the modified text
	 */
	public static String replace(final String s, final String sWhat, final String sWith) {
		if (s==null || sWhat==null || sWhat.length()==0)
			return s;

		StringBuffer sb = null;

		final int iWhatLen = sWhat.length();
		final int iWithLen = sWith.length();
		
		int iOld = 0;
		int i = 0;
		int iReplacements = 0;
		
		while ((i = s.indexOf(sWhat, iOld)) >= 0) {
			if (sb == null) {
				final int diff = (iWhatLen - iWithLen) * (iOldReplacements1 * 2 + iOldReplacements2 + 1) / 3;
				sb = new StringBuffer(s.length() + (diff > 0 ? diff : 0));
			}

			if (iOld<i)
				sb.append(s.substring(iOld, i));
			
			sb.append(sWith);
			
			iOld = i + iWhatLen;
			
			iReplacements++;
		}

		iOldReplacements2 = iOldReplacements1;
		iOldReplacements1 = iReplacements;

		if (sb != null) {
			sb.append(s.substring(iOld));
			return sb.toString();
		}
		
		// no replacements to do
		return s;
	}

	/**
	 * Put HTML line breaks in the place of normal text line breaks.
	 * 
	 * @param text original text
	 * @return text with &lt;BR&gr; instead of the \n in the original text
	 */
	public static final String formatBR(final String text) {
		return replace(replace(text, "\\r", ""), "\\n", "<BR>");
	}

	/**
	 * Reverse function of the {@link #formatBR(String)}, converts HTML line breaks into text line breaks  
	 * 
	 * @param text original text
	 * @return text with \n instead of &lt;BR&gt;
	 */
	public static final String formatN(final String text) {
		return replace(replace(text, "<br>", "\n"), "<BR>", "\n");
	}

	private final static SimpleDateFormat[]	sdfFormats	= new SimpleDateFormat[] { 
				new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z"), // 0
				new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), // 1
				new SimpleDateFormat("yyyy-MM-dd"), // 2
				new SimpleDateFormat("dd.MM.yyyy HH:mm:ss Z"), // 3
				new SimpleDateFormat("dd.MM.yyyy HH:mm:ss"), // 4
				new SimpleDateFormat("dd.MM.yyyy"), // 5
				new SimpleDateFormat("MM/dd/yyyy HH:mm:ss Z"), // 6
				new SimpleDateFormat("MM/dd/yyyy HH:mm:ss"), // 7
				new SimpleDateFormat("MM/dd/yyyy"), // 8
				new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z"), // 9
				new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"), // 10
				new SimpleDateFormat("yyyy/MM/dd"), // 11
				new SimpleDateFormat("MMM dd HH:mm:ss zzzz yyyy"), // 12 - Mar 14 13:04:30 CET 2007
				new SimpleDateFormat("EEE MMM dd HH:mm:ss zzzz yyyy"), // 13 - Wed Mar 14 13:04:30 CET 2007
	};

	/**
	 * Transform a string that represents a Date into a real Date object. It will try several formats, including
	 * date-only and time-only representations.
	 * 
	 * @param s string to convert
	 * @return date representation, or null if the conversion was not possible.
	 */
	@SuppressWarnings("deprecation")
	public static final Date parseDate(final String s) {
		if (s==null || s.length()==0)
			return null;
		
		// first try the default Date constructor, maybe it is in this format
		try{
			return new Date(s);
		}
		catch (IllegalArgumentException ile){
			//System.err.println("Date parser didn't work");
		}
		
		try{
			long l = Long.parseLong(s);
			
            if (l<2000000000)
                l*=1000;

            return new Date(l);
		}
		catch (NumberFormatException nfe){
			// ignore
		}
		
		// try all the date formats
		for (int i = 0; i < sdfFormats.length; i++) {
			try {
				Date d = sdfFormats[i].parse(s);
				
				//System.err.println("Parser ok : "+i);
				
				return d;
			}
			catch (ParseException e) {
				// ignore this
			}
		}

		// if nothing worked so far, maybe this is a time only, so try to add the current date to it and parse it again 
		final String sNew = sdfFormats[2].format(new Date()) + " " + s;
			
		for (int i = 0; i < 2; i++){
			try {
				return sdfFormats[i].parse(sNew);
			}
			catch (ParseException e) {
				// ignore this too
			}			
		}
		
		return null;
	}

	/**
	 * For date formatting, put a "0" in front of a single digit, or leave the number as it is if there are 
	 * at least two digits already 
	 * 
	 * @param i the number to format
	 * @return 0-padded string representation
	 */
	public static final String show0(int i) {
		if (i < 10)
			return "0" + i;
		
		return "" + i;
	}
	
	/**
	 * Get a nifty string representation for a date. Something like "today", "yesterday", "21 May 2006" ...
	 * 
	 * @param d date to convert
	 * @return nice human readable date
	 */
	@SuppressWarnings("deprecation")
	public static final String showNiceDate(final Date d) {
		final Date now = new Date();
		if (now.getDate() == d.getDate() && now.getMonth() == d.getMonth() && now.getYear() == d.getYear())
			return "today";

		final Date y = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24);
		if (y.getDate() == d.getDate() && y.getMonth() == d.getMonth() && y.getYear() == d.getYear())
			return "yesterday";

		return showNamedDate(d);
	}

	/**
	 * Show the date and time in a nice human-readable format
	 * 
	 * @param d date to represent
	 * @return date and time
	 */
	public static final String showDate(final Date d) {
		return showNamedDate(d) + " " + showTime(d);
	}

	/**
	 * Show a date in a dotted manner (dd.MM.yyyy)
	 * 
	 * @param d date to show
	 * @return the dotted date
	 */
	public static final String showDottedDate(final Date d) {
		return sdfFormats[5].format(d);
	}

	private static final SimpleDateFormat sdfLongDate = new SimpleDateFormat("dd MMMM yyyy");
	
	/**
	 * Show the full month name (11 January 2006)
	 * 
	 * @param d date to show
	 * @return date with full month name
	 */
	public static final String showLongNamedDate(final Date d) {
		synchronized (sdfLongDate){
			return sdfLongDate.format(d);
		}
	}

	private static final SimpleDateFormat sdfShortDate = new SimpleDateFormat("dd MMM yyyy");
	
	/**
	 * Show the abreviated month name (11 Jan 2006)
	 * 
	 * @param d date to show
	 * @return date with short month name
	 */
	public static final String showNamedDate(final Date d) {
		synchronized (sdfShortDate){
			return sdfShortDate.format(d);
		}
	}

	private static final SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm");
	
	/**
	 * Show the time only (12:34)
	 * 
	 * @param d date to show
	 * @return the hours and minutes only
	 */
	public static final String showTime(final Date d) {
		synchronized (sdfTime){
			return sdfTime.format(d);
		}
	}
	
	/**
	 * Show the value in 3 digit groups separated by commas
	 * 
	 * @param l value to show
	 * @return nice string representation
	 */
	public static final String showDottedLong(final long l) {
		return showDottedDouble(l, 0);
	}

	/**
	 * Show the value in 3 digit groups separated by commas, no period point 
	 * 
	 * @param d value to show
	 * @return nice string representation
	 */
	public static final String showDottedDouble(final double d) {
		return showDottedDouble(d, 0);
	}

	/**
	 * Show the value in 3 digit groups separated by commas, with the specified number of decimal places after the 
	 * decimal point 
	 * 
	 * @param d value to show
	 * @param dotplaces number of decimal places after the point
	 * @return nice string representation
	 */
	public static final String showDottedDouble(final double d, final int dotplaces) {
		return showDottedDouble(d, dotplaces, false);
	}

	/**
	 * Show the value in 3 digit groups separated by commas, with the specified number of decimal places after the 
	 * decimal point.
	 * 
	 * @param dbl value to show
	 * @param decimals number of decimal places after the point
	 * @param aproximated whether or not to use "millions" and "bilions" for very large numbers 
	 * @return nice string representation
	 */
	public static final String showDottedDouble(final double dbl, final int decimals, final boolean aproximated) {
		assert decimals>=0;
		
		double d = dbl;
		
		int dotplaces = decimals;
		
		String append = "";
		if (aproximated) {
			if (Math.abs(d) > 1E9) {
				d /= 1E9;
				dotplaces = (dotplaces == 0) ? 2 : dotplaces;
				append = " millions";
			} else if (Math.abs(d) > 1E6) {
				d /= 1E6;
				dotplaces = (dotplaces == 0) ? 2 : dotplaces;
				append = " billions";
			}
		}

		long l = (long) d;
		double f = Math.abs(d) - Math.abs(l);

		String sRez = "";

		if (l == 0)
			sRez = "0";

		while (Math.abs(l) > 0) {
			if (sRez.length() > 0) {
				int i = sRez.indexOf(",");

				if (i < 0)
					i = sRez.length();

				switch (i) {
					case 1:
						sRez = "00" + sRez;
						break;
					case 2:
						sRez = "0" + sRez;
						break;
				}

				sRez = "," + sRez;
			}

			sRez = (Math.abs(l) % 1000) + sRez;
			l = l / 1000;
		}

		if (dotplaces > 0) {
			for (int i = 0; i < dotplaces; i++)
				f *= 10;

			f = Math.round(f);

			String sTemp = "" + (long) f;

			while (sTemp.length() < dotplaces)
				sTemp = "0" + sTemp;

			if ((long) f > 0) {
				sRez += "." + sTemp;
			}
		}

		return (d < 0) ? "-" + sRez + append : sRez + append;
	}

	/**
	 * Convert a time in milliseconds to a human readable interval display
	 * 
	 * @param lInterval interval time in milliseconds
	 * @return human readable interval
	 */
	public final static String toInterval(final long lInterval) {
		String sRez = null;

		long l = lInterval;
		
		if (l <= 0)
			sRez = "-";
		else {
			l /= 1000;

			long s = l % 60;
			l /= 60;
			long m = l % 60;
			l /= 60;
			long h = l % 24;
			l /= 24;
			long d = l;

			if (d > 0)
				sRez = d + "d " + h + ":" + show0((int)m);
			else if (h > 0)
				sRez = h + ":" + show0((int)m);
			else {
				sRez = m + "m";

				if (s > 0)
					sRez += " " + s + "s";
			}
		}

		return sRez;
	}
	
	private static final char[] hexTable = new char[]{'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
	
	/**
	 * Get the hexa digit for this integer value
	 * 
	 * @param val value
	 * @return hexa digit
	 */
	public static final char hexChar(final int val) {
		if (val<0 || val>15)
			throw new IllegalArgumentException("Hex digits are between 0 and 15, "+val+" is not allowed here");

		return hexTable[val];
	}
	
	/**
	 * Get the 2 hexa digit representation of this byte
	 * 
	 * @param val 
	 * @return the 2 hexa digit representation
	 */
	public static final String byteToHex(byte val) {
		return "" + hexChar((val >>> 4) & 0x0F) + hexChar(val & 0x0F);
	}
	
}
