package lazyj.mail.comparators;


import java.io.Serializable;
import java.util.Comparator;

import lazyj.mail.MailHeader;

/**
 * A MailHeader comparator that sorts the mails by their subjects.
 * 
 * @author costing
 * @since 2006-10-13
 */
public class SubjectComparator implements Comparator<MailHeader>, Serializable {
	private static final long	serialVersionUID	= -2220706698627319395L;

	/**
	 * Sorting method : by the subjects 
	 * 
	 * @param o1 first mail
	 * @param o2 second mail
	 * @return subjects compared result (as trimmed strings in lower case)
	 */
	public int compare(final MailHeader o1, final MailHeader o2) {
		final String s1 = o1.sSubject.trim();
		final String s2 = o2.sSubject.trim();

		return (-1) * s1.compareToIgnoreCase(s2);
	}
}
