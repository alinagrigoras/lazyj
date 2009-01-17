/**
 * 
 */
package lazyj.notifications;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import lazyj.ExtProperties;
import lazyj.Log;

/**
 * Notification hub. The actual senders are defined in a {@link ExtProperties} object. The structure must be:<br>
 * <code>
 * senders=<i>N</i>
 * </code><br>
 * <br>
 * for (I in 1..N)<br>
 * <code>
 * &nbsp;&nbsp;<b>sender_<i>I</i>.class=<i>classname</i></b><br>
 * &nbsp;&nbsp;sender_<i>I</i>.key1=value1<br>
 * &nbsp;&nbsp;...
 * </code><br>
 * <br>
 * The class must be an instance of {@link Sender}. See subclasses of {@link Sender} for details
 * on what keys and values are expected for each.<br>
 * <br>
 * If you want the code to react to file changes please make sure {@link ExtProperties#setAutoReload(long)} is called
 * for the instance you give here.
 * 
 * @author costing
 * @since Nov 16, 2007 (1.0.3)
 */
public final class Notifier implements Observer {

	/**
	 * Configuration options
	 */
	private final ExtProperties propConfig;
	
	/**
	 * What are the actual notification methods
	 */
	private final ArrayList<Sender> senders = new ArrayList<Sender>();
	
	/**
	 * Class loader for the modules
	 */
	private ClassLoader loader = null;
	
	/**
	 * @param prop
	 */
	public Notifier(final ExtProperties prop){
		this(prop, null);
	}
	
	/**
	 * If you want to load arbitrary classes from one of the senders you should use this method, giving as argument 
	 * <code>this.getClass().getClassLoader()</code>. You should also use this constructor if you want to
	 * load custom {@link Sender} classes that reside inside a webapp zone (or anywhere else where it is not
	 * accessible through the default class loader).
	 * 
	 * @param prop
	 * @param loader
	 */
	public Notifier(final ExtProperties prop, final ClassLoader loader){
		this.propConfig = prop;
		this.propConfig.addObserver(this);
		this.loader = loader;
		
		reload();
	}
	
	/**
	 * Re-parse configuration
	 */
	private synchronized final void reload(){
		this.senders.clear();
		
		final int iSenders = this.propConfig.geti("senders", 0);
		
		for (int i=0; i<iSenders; i++){
			final Sender s = getSender(this.propConfig, i);
			
			if (s!=null && s.init(this.propConfig, "sender_"+i+".")){
				this.senders.add(s);
			}
		}		
	}

	/**
	 * @param prop
	 * @param idx
	 * @return sender class for this configuration index
	 */
	private Sender getSender(final ExtProperties prop, final int idx) {
		final String sClass = prop.gets("sender_"+idx+".class");
		
		for (ClassLoader cl: new ClassLoader[]{
				this.loader,
				Thread.currentThread().getClass().getClassLoader(),
				Thread.currentThread().getContextClassLoader(),
				this.getClass().getClassLoader(),
				null}
		){
			try{
				final Sender s = (Sender) Class.forName(sClass, true, cl).newInstance();
				s.setClassLoader(this.loader);
				
				Log.log(Log.FINE, "lazyj.notifications.Notifier", "Loaded '"+sClass+"' with: "+cl);
				
				return s;
			}
			catch (Throwable t){
				Log.log(Log.FINE, "lazyj.notifications.Notifier", "Failed to load '"+sClass+"' with: "+cl);
			}
		}
		
		Log.log(Log.ERROR, "lazyj.notifications.Notifier", "Cannot instantiate '"+sClass+"'");
		return null;
	}
	
	/**
	 * Send a message though all the defined channels
	 * 
	 * @param m
	 * @return true if the sending was successful with all the defined senders
	 */
	public synchronized boolean send(final Message m){
		boolean bOk = true;
		
		for (Sender s: this.senders){
			bOk = s.send(m) && bOk;
		}
		
		return bOk;
	}

	/* (non-Javadoc)
	 * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
	 */
	public void update(final Observable o, final Object arg) {
		reload();
	}

}
