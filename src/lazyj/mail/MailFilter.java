package lazyj.mail;

import java.util.Map;

/**
 * Classes that can alter the contents of an email should implement this interface. 
 * 
 * @author costing
 * @since Sep 15, 2009
 * @since 1.0.6
 * @see Sendmail#registerFilter(MailFilter)
 */
public interface MailFilter {

	/**
	 * Filter the mail contents and the headers. The filters are called just before sending the 
	 * headers to the server so it's still ok to tamper with the headers and/or mail contents.
	 * 
	 * @param mail the original mail
	 * @param headers full set of headers, just before sending them to the server
	 * @param sBody full mail body
	 */
	void filter(Map<String, String> headers, String sBody, Mail mail);
	
}
