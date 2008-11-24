package lazyj.viewers;

/**
 * Executes "/usr/jserv/portal/bin/smtp_spamfilter/check_outgoing.sh" to check if an email is a spam or not.
 * 
 * @author costing
 * @since Oct 15, 2006
 */
public class SPAMViewer extends Viewer {

	/**
	 * The input mail to check if it is spam or not
	 * 
	 * @param o input to check
	 * @see Viewer#Viewer(Object)
	 */
	public SPAMViewer(Object o) {
		super(o);
	}

	/**
	 * This just calls {@link Viewer#getProgramOutput(String)} for "/usr/jserv/portal/bin/smtp_spamfilter/check_outgoing.sh" that will
	 * check if the original input (an email body) is a spam or not.
	 * 
	 * @return the result of the check
	 */
	@Override
	public String getString() {
		return getProgramOutput("/usr/jserv/portal/bin/smtp_spamfilter/check_outgoing.sh");
	}

}