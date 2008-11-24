/**
 * 
 */
package lazyj.notifications;

import java.util.Collection;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

/**
 * The messages are passed by the application through a {@link Notifier}, to be delivered by the implementations
 * of {@link Sender} specified in the configuration of the Notifier. 
 * 
 * @author costing
 * @since Nov 16, 2007 (1.0.3)
 */
public class Message {
	/**
	 * Sender of this message. Can be null if a default sender is defined in the Notifier.
	 */
	public String sFrom;
	
	/**
	 * Recipients of this message. Any default recipients from the Notifier will be added to this set.
	 */
	public Set<String> sTo = new TreeSet<String>();
	
	/**
	 * Subject / short version of the message
	 */
	public String sSubject;
	
	/**
	 * Message to be sent.
	 */
	public String sMessage;
	
	/**
	 * @param from Sender of this message. Can be null if a default sender is defined in the Notifier.
	 * @param to Recipient of this message. Can be null if a default set of recipients is defined in the Notifier.
	 * @param subject Subject / short version of the message.
	 * @param message Message to be sent.
	 */
	public Message(final String from, final String to, final String subject, final String message){
		this.sFrom = from;
		this.sSubject = subject;
		
		if (to!=null)
			this.sTo.add(to);
		
		this.sMessage = message;
	}
	
	/**
	 * @param from Sender of this message. Can be null if a default sender is defined in the Notifier.
	 * @param to Recipients of this message. Any default recipients from the Notifier will be added to this set.
	 * @param subject Subject / short version of the message.
	 * @param message Message to be sent.
	 */
	public Message(final String from, final Collection<String> to, final String subject, final String message){
		this.sFrom = from;
		this.sSubject = subject;
		
		if (to!=null)
			this.sTo.addAll(to);
		
		this.sMessage = message;
	}
	
	/**
	 * Get the list of recipients as a comma-separated string.
	 * 
	 * @return comma-separated list of recipients
	 */
	public String getToAsList(){
		return setToList(this.sTo);
	}
	
	/**
	 * Convert a set of strings into a comma-separated string
	 * 
	 * @param set
	 * @return comma-separated list
	 */
	public static String setToList(final Set<String> set){
		if (set==null || set.size()==0)
			return "";
		
		final StringBuilder sb = new StringBuilder();
		
		for (String s: set){
			if (sb.length()>0)
				sb.append(',');
			
			sb.append(s);
		}
		
		return sb.toString();		
	}
	
	@Override
	public String toString(){
		return this.sFrom+"->"+this.sTo+" ("+this.sSubject+") : "+this.sMessage;
	}
	
	/**
	 * Split a comma-separated list into a set of distinct names
	 * 
	 * @param s
	 * @return set of unique names from this comma-separated list of names
	 */
	public static Set<String> listToSet(final String s){
		final Set<String> set = new TreeSet<String>();
		
		if (s==null)
			return set;
		
		final StringTokenizer st = new StringTokenizer(s, ",");
		
		while (st.hasMoreTokens()){
			set.add(st.nextToken());
		}
		
		return set;
	}
}
