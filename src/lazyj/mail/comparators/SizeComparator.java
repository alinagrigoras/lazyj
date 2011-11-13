package lazyj.mail.comparators;


import java.io.Serializable;
import java.util.Comparator;

import lazyj.mail.MailHeader;

/**
 * A MailHeader comparator that sorts the mails by the size.
 * 
 * @author costing
 * @since 2006-10-13
 */
public class SizeComparator implements Comparator<MailHeader>, Serializable {
	/**
	 * ignored
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Sorting method : by the size of the mail 
	 * 
	 * @param o1 first mail
	 * @param o2 second mail
	 * @return difference between the mail sizes
	 */	@Override
	public int compare(final MailHeader o1, final MailHeader o2) {
		return o1.iMailSize - o2.iMailSize;
	}
}
