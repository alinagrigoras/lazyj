package lazyj.mail.comparators;


import java.io.Serializable;
import java.util.Comparator;

import lazyj.mail.MailDate;
import lazyj.mail.MailHeader;

/**
 * A MailHeader comparator that sorts the mails by the sending date.
 * 
 * @author costing
 * @since 2006-10-13
 */
public class DateComparator implements Comparator<MailHeader>, Serializable {
	private static final long	serialVersionUID	= -5048796807009198965L;
	
	/**
	 * Sorting method : by the date in the header 
	 * 
	 * @param o1 first mail
	 * @param o2 second mail
	 * @return sending dates compared result (see {@link MailDate#compareTo(MailDate)})
	 */
	public int compare(MailHeader o1, MailHeader o2) {
		return o1.mdDate.compareTo(o2.mdDate);
	}
}
