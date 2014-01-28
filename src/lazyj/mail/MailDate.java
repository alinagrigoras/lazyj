package lazyj.mail;

import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;

import lazyj.Format;

/**
 * Decode/encode the dates from the mail headers with various format fallbacks. It also provides several
 * methods of converting the date to string for various purposes.
 * 
 * @author costing
 * @since 2006-10-06
 */
public final class MailDate implements Comparable<MailDate>{
	
	/**
	 * day of the month
	 */
	public int					day;
	
	/**
	 * month
	 */
	public int month;
	
	/**
	 * year
	 */
	public int  year;
	
	/**
	 * day of week 
	 */
	public int  dow;
	
	/**
	 * hour in day
	 */
	public int  hour;
	
	/**
	 * minute in hour
	 */
	public int min;
	
	/**
	 * second in minute
	 */
	public int sec;

	/**
	 * offset to GMT
	 */
	public String				sDeplasareGMT;
	
	/**
	 * local time zone
	 */
	public String sLocalZone;

	/**
	 * mail date
	 */
	private Date				date;

	/**
	 * original string
	 */
	private String				sOrigDate;

	/**
	 * Short names for the days of the week
	 */
	@SuppressWarnings("nls")
	static final String			sShortDows[]		= { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };

	/**
	 * Long names for the days of the week
	 */
	@SuppressWarnings("nls")
	static final String			sLongDows[]			= { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };

	/**
	 * Long names for the months
	 */
	@SuppressWarnings("nls")
	static final String			sLongMonths[]		= { "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December" };

	/**
	 * Short names for the months
	 */
	@SuppressWarnings("nls")
	static final String			sShortMonths[]		= { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

	/**
	 * Short names for the days of the week, in lower case
	 */
	static final String			sLowerShortDows[]	= new String[7];

	/**
	 * Long names for the days of the week, in lower case
	 */
	static final String			sLowerLongDows[]	= new String[7];

	/**
	 * Long names for the months, in lower case
	 */
	static final String			sLowerLongMonths[]	= new String[12];

	/**
	 * Short names for the months, in lower case
	 */
	static final String			sLowerShortMonths[]	= new String[12];

	static {
		int i;
		for (i = 0; i < 7; i++) {
			sLowerShortDows[i] = sShortDows[i].toLowerCase(Locale.getDefault());
			sLowerLongDows[i] = sLongDows[i].toLowerCase(Locale.getDefault());
		}

		for (i = 0; i < 12; i++) {
			sLowerShortMonths[i] = sShortMonths[i].toLowerCase(Locale.getDefault());
			sLowerLongMonths[i] = sLongMonths[i].toLowerCase(Locale.getDefault());
		}
	}

	/**
	 * zero everything
	 */
	private void initLocalData() {
		this.day = 0;
		this.month = 0;
		this.year = 0;
		this.dow = 0;
		this.hour = 0;
		this.min = 0;
		this.sec = 0;
		this.sDeplasareGMT = ""; //$NON-NLS-1$
		this.sLocalZone = ""; //$NON-NLS-1$
	}

	/**
	 * Construct an object based on a Date
	 * 
	 * @param dParam Date object to build upon
	 */
	@SuppressWarnings("deprecation")
	public MailDate(final Date dParam) {
		this.year = dParam.getYear() + 1900;
		this.month = dParam.getMonth();
		this.day = dParam.getDate();
		this.dow = dParam.getDay();
		this.hour = dParam.getHours();
		this.min = dParam.getMinutes();
		this.sec = dParam.getSeconds();
		this.sOrigDate = dParam.toString();
	}

	/**
	 * Parse time of day
	 * 
	 * @param sHMS
	 */
	private void processHMS(final String sHMS) {
		final StringTokenizer st = new StringTokenizer(sHMS, ": "); //$NON-NLS-1$
		try {
			this.hour = Integer.parseInt(st.nextToken());
			this.min = Integer.parseInt(st.nextToken());
			this.sec = 0;
			if (st.hasMoreTokens())
				this.sec = Integer.parseInt(st.nextToken());
		} catch (final NumberFormatException e) {
			// Log.log(4,"MailDate","processHMS","", "HMS string: " + sHMS);
		}
	}

	/**
	 * Other parser
	 * 
	 * @param data
	 */
	@SuppressWarnings("deprecation")
	private void oldProcessor(final String data) {
		try {
			int i;
			int iType;
			final StringTokenizer st = new StringTokenizer(data.toLowerCase(Locale.getDefault()), " ,()"); //$NON-NLS-1$
			this.sOrigDate = data;
			String s;

			s = st.nextToken();
			this.dow = -1;
			for (i = 0; i < 7; i++)
				if (sLowerLongDows[i].startsWith(s)) {
					this.dow = i;
					break;
				}

			iType = 1;
			if (this.dow >= 0) { // altfel inseamna ca incepea fara ziua saptamanii
				s = st.nextToken();
				iType = 2;
			}

			for (i = 0; i < 12; i++)
				if (sLowerLongMonths[i].startsWith(s)) {
					iType = 3;
					break;
				}

			// iType possible values
			// 1 : 29 Nov 2000 23:12:33 -0000
			// 2 : Fri, 8 Dec 2000 09:19:11 +0200 (EET)
			// 3 : Mon Dec 18 14:29:25 2000
			// Wed Dec 20 16:38:00 GMT+02:00 2000

			if (iType < 3) {
				this.day = Integer.parseInt(s);

				s = st.nextToken();
				this.month = -1;
				for (i = 0; i < 12; i++)
					if (sLowerShortMonths[i].startsWith(s)) {
						this.month = i;
						break;
					}

				this.year = Integer.parseInt(st.nextToken());

				processHMS(st.nextToken());
			} else {
				this.month = i;
				this.day = Integer.parseInt(st.nextToken());

				processHMS(st.nextToken());

				final String sTemp = st.nextToken();
				try {
					this.year = Integer.parseInt(sTemp);
				} catch (final Exception e) {
					this.sDeplasareGMT = sTemp;
					this.year = Integer.parseInt(st.nextToken());
				}
			}

			if (st.hasMoreTokens())
				this.sDeplasareGMT = st.nextToken();
			if (st.hasMoreTokens())
				this.sLocalZone = st.nextToken();

			if (this.year < 30)
				this.year += 2000;
			else if (this.year < 100)
				this.year += 1900;

			this.date = new Date(this.year - 1900, this.month, this.day, this.hour, this.min, this.sec);

			this.year = this.date.getYear() + 1900;
			this.month = this.date.getMonth();
			this.day = this.date.getDate();
			this.dow = this.date.getDay();
			this.hour = this.date.getHours();
			this.min = this.date.getMinutes();
			this.sec = this.date.getSeconds();
		} catch (final Throwable e) {
			// Log.log(Log.WARNING, Log.COMMON, "MailDate", "oldProcessor", data, e.getMessage());
		}
	}

	/**
	 * Constructor based on a String representation of a date. Since this class is designed for
	 * mail-related use, it expects here typical mail date strings, like "Fri, 6 Oct 2006 15:07:29
	 * +0200".
	 * 
	 * @param data
	 *            the string representation of the date
	 */
	@SuppressWarnings("deprecation")
	public MailDate(final String data) {
		initLocalData();

		Date dateParse = new Date();
		this.year = dateParse.getYear() + 1900;
		this.month = dateParse.getMonth();
		this.day = dateParse.getDate();
		this.dow = dateParse.getDay();
		this.hour = dateParse.getHours();
		this.min = dateParse.getMinutes();
		this.sec = dateParse.getSeconds();

		this.sOrigDate = data;

		try {
			dateParse = new Date(data);
			this.year = dateParse.getYear() + 1900;
			this.month = dateParse.getMonth();
			this.day = dateParse.getDate();

			this.dow = dateParse.getDay();
			this.hour = dateParse.getHours();
			this.min = dateParse.getMinutes();
			this.sec = dateParse.getSeconds();
		} catch (final Throwable e) {
			oldProcessor(data);
		}
	}

	/**
	 * Copy constructor.
	 * 
	 * @param mdParam
	 */
	@SuppressWarnings("deprecation")
	public MailDate(final MailDate mdParam) {
		this.day = mdParam.day;
		this.month = mdParam.month;
		this.year = mdParam.year;

		this.dow = mdParam.dow;
		this.hour = mdParam.hour;
		this.min = mdParam.min;
		this.sec = mdParam.sec;
		this.sDeplasareGMT = mdParam.sDeplasareGMT;
		this.sLocalZone = mdParam.sLocalZone;
		this.sOrigDate = (mdParam.sOrigDate != null) ? mdParam.sOrigDate : (new Date(this.year - 1900, this.month, this.day, this.hour, this.min, this.sec)).toString();
	}

	/**
	 * Show a nifty date, such as:<br>
	 * "12 Jul 2003"<br>
	 * "23 September"<br>
	 * "Wednesday 24"<br>
	 * "Yesterday 11:24"
	 * 
	 * @return nifty date format, sometimes with time
	 */
	@SuppressWarnings({ "deprecation", "nls" })
	@Override
	public String toString() {
		final Date dCurrent = new Date();
		String sResult;

		if (dCurrent.getYear() + 1900 != this.year) {
			sResult = this.day + " " + sShortMonths[this.month] + " " + this.year;
		} else if (dCurrent.getMonth() != this.month) {
			sResult = this.day + " " + sLongMonths[this.month];
		} else if (((dCurrent.getDate() < this.day - 1) || (dCurrent.getDate() > this.day + 1)) && (this.dow >= 0)) {
			sResult = sLongDows[this.dow] + " " + Format.show0(this.day);
		} else {
			if (dCurrent.getDate() > this.day)
				sResult = "Yesterday";
			else if (dCurrent.getDate() == this.day)
				sResult = "Today";
			else
				sResult = "Tomorrow";

			sResult += " " + Format.show0(this.hour) + ":" + Format.show0(this.min);
		}

		return sResult;
	}

	/**
	 * The same as {@link #toString()}, but always having the hour:time at the end
	 * 
	 * @return nifty date + time format
	 */
	@SuppressWarnings("deprecation")
	public String toString2() {
		final String toString = toString();
		
		if (Math.abs((new Date()).getDate() - this.day) > 2) {
			return toString + ' ' + Format.show0(this.hour) + ':' + Format.show0(this.min);
		}
		
		return toString;
	}

	/**
	 * A default {@link java.util.Date#toString()} for the current date
	 * 
	 * @return default Date formatting for this date
	 */
	@SuppressWarnings("deprecation")
	public String toFullString() {
		return (new Date(this.year - 1900, this.month, this.day, this.hour, this.min, this.sec)).toString();
	}

	/**
	 * The representation from the standard mail : "Fri, 6 Oct 2006 11:11:08 +0200"
	 * 
	 * @return mail date representation for this date
	 */
	@SuppressWarnings("deprecation")
	public String toMailString() {
		// Wed, 16 Jan 2002 21:11:19 +0200
		String sResult = sShortDows[this.dow] + ", " + this.day + ' ' + sShortMonths[this.month] + ' ' + this.year + ' ' + Format.show0(this.hour) + ':' + Format.show0(this.min) + ':' + Format.show0(this.sec); //$NON-NLS-1$

		if ((this.sDeplasareGMT != null) && (this.sDeplasareGMT.length() > 0))
			sResult += ' ' + this.sDeplasareGMT;
		else {
			final Date d = new Date();
			int o = d.getTimezoneOffset();
			
			char sign = '-';
			
			if (o < 0){
				o = -o;
				sign = '+';
			}
			
			o /= 60;

			sResult += " "+sign+(o<10?"0":"") + o + "00"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

		return sResult;
	}

	/**
	 * Method to compare two dates. Trivial, but necessary :)
	 * @param mdParam another MailDate to compare to
	 */
	@Override
	public int compareTo(final MailDate mdParam) {
		if (this.year > mdParam.year)
			return 1;
		if (this.year < mdParam.year)
			return -1;
		if (this.month > mdParam.month)
			return 1;
		if (this.month < mdParam.month)
			return -1;
		if (this.day > mdParam.day)
			return 1;
		if (this.day < mdParam.day)
			return -1;
		if (this.hour > mdParam.hour)
			return 1;
		if (this.hour < mdParam.hour)
			return -1;
		if (this.min > mdParam.min)
			return 1;
		if (this.min < mdParam.min)
			return -1;
		if (this.sec > mdParam.sec)
			return 1;
		if (this.sec < mdParam.sec)
			return -1;
		return 0;
	}

	/**
	 * Check if two dates are equal.
	 * 
	 * @param mdParam
	 *            other object
	 * @return true, if the given object is a MailDate, with the same time, false otherwise
	 */
	@Override
	public boolean equals(final Object mdParam) {
		if (mdParam instanceof MailDate)
			return (compareTo((MailDate)mdParam) == 0);
		
		return false;
	}

	/**
	 * Hash code for this object
	 * @return the same hash as the coresponding Date object
	 */
	@Override
	public int hashCode() {
		return getDate().hashCode();
	}

	/**
	 * Get the contents of this object in a {@link java.util.Date} format.
	 * 
	 * @return the Date object with the same value
	 */
	@SuppressWarnings("deprecation")
	public Date getDate() {
		return new Date(this.year - 1900, this.month, this.day, this.hour, this.min, this.sec);
	}
	
	/**
	 * Testing code
	 * 
	 * @param args command line arguments
	 */
	public static void main(final String[] args) {
		final MailDate md = new MailDate(new Date());
		
		System.err.println(md.toString());
		System.err.println(md.toString2());
		System.err.println(md.toFullString());
		System.err.println(md.toMailString());
	}
}