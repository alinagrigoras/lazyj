/**
 * 
 */
package lazyj.notifications;

import java.util.Set;
import java.util.TreeSet;

import lazyj.ExtProperties;
import lazyj.LRUMap;

/**
 * Send a message via Yahoo! Messenger. Please make sure you loaded the latest &amp; patched 
 * <a href="http://jymsg9.sourceforge.net/">jymsg9</a> in your classpath. 
 * For this to work you have to specify the following options in the configuration:<br><ul>
 * <li>[prefix]username=<i>yahoo account</i></li>
 * <li>[prefix]password=<i>password for this account</i></li>
 * </ul>
 * <br>
 * You can also specify a list of default recipients with:<br><ul>
 * <li>[prefix].to=<i>list of comma separated YM accounts</i></li>
 * </ul>
 * <br>
 * If you want to also receive back messages you can register a class that implements ymsg.network.event.SessionListener by:<br><ul>
 * <li>[prefix]listener=<i>class name</i></li>
 * </ul>
 * 
 * @author costing
 * @since Nov 17, 2007 (1.0.3)
 */
public class YMSender extends Sender {

	/**
	 * Yahoo! account name
	 */
	private String sAccount = null;
	
	/**
	 * Yahoo! account password
	 */
	private String sPassword = null;
	
	/**
	 * List of Yahoo! messenger ids
	 */
	private Set<String> sDefaultTo = null;
	
	/**
	 * Listen for incoming messages
	 */
	private ymsg.network.event.SessionListener listener = null;
	
	/**
	 * YM connections cache.
	 */
	private static final LRUMap<String, ymsg.network.Session> connPool = new LRUMap<String, ymsg.network.Session>(20);
	
	static{
		System.setProperty("ymsg.debug", "false");
		System.setProperty("ymsg.network.loginTimeout", "30");
	}
	
	/* (non-Javadoc)
	 * @see lazyj.notifications.Sender#init(lazyj.ExtProperties, java.lang.String)
	 */
	@Override
	public boolean init(final ExtProperties prop, final String keyPrefix) {
		this.sAccount = prop.gets(keyPrefix+"username");
		this.sPassword = prop.gets(keyPrefix+"password");
		this.sDefaultTo = Message.listToSet(prop.gets(keyPrefix+"to"));
		
		this.listener = (ymsg.network.event.SessionListener) getClassInstance(prop.gets(keyPrefix+"listener"));
		
		return this.sAccount.length()>0 && this.sPassword.length()>0 && getConnection()!=null;
	}

	/**
	 * Unique key identifying this connection
	 * 
	 * @return key
	 */
	private String getKey(){
		return this.sAccount+"/"+this.sPassword;
	}
	
	/**
	 * Get a new or recycled connection
	 * 
	 * @return connection
	 */
	private ymsg.network.Session getConnection(){
		final String sKey = getKey();
		
		ymsg.network.Session sess = connPool.get(sKey);
		
		if (sess!=null)
			return sess;
		
		try{
			sess =  new ymsg.network.Session();
			
			if (this.listener!=null)
				sess.addSessionListener(this.listener);
			
			sess.login(this.sAccount, this.sPassword);
			connPool.put(sKey, sess);
			return sess;
		}
		catch (Throwable t){
			return null;
		}
	}
	
	/* (non-Javadoc)
	 * @see lazyj.notifications.Sender#send(lazyj.notifications.Message)
	 */
	@Override
	public synchronized boolean send(final Message m) {
		if (m==null)
			return false;
		
		String sMessage = m.sSubject;
		
		if (sMessage==null){
			sMessage = m.sMessage;
		}
		else{
			if (m.sMessage!=null){
				if (m.sMessage.startsWith(sMessage))
					sMessage = m.sMessage;
				else
					sMessage += " : "+m.sMessage;
			}
		}
		
		ymsg.network.Session sess = getConnection();
		
		if (sess==null)
			return false;
		
		final Set<String> accounts = new TreeSet<String>(this.sDefaultTo);
		accounts.addAll(m.sTo);
		
		if (!reallySend(sess, accounts, sMessage)){
			sess = getConnection();
			
			if (sess==null)
				return false;
			
			return reallySend(sess, accounts, sMessage);
		}
		
		return true;
	}

	/**
	 * Send the message
	 * 
	 * @param sess
	 * @param accounts
	 * @param message
	 * @return true if sending was ok
	 */
	private boolean reallySend(final ymsg.network.Session sess, final Set<String> accounts, final String message){
		try{
			for (String sDest: accounts)
				sess.sendMessage(sDest, message);
			
			return true;
		}
		catch (Throwable t1){
			connPool.remove(getKey());
			
			try{
				sess.logout();
			}
			catch (Throwable t2){
				// ignore
			}
			
			return false;
		}
	}
	
}
