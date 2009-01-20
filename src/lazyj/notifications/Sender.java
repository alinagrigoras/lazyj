/**
 * 
 */
package lazyj.notifications;

import lazyj.ExtProperties;
import lazyj.Log;

/**
 * This is the base class that needs to be extended by any message delivery class.
 * 
 * @author costing
 * @since Nov 16, 2007 (1.0.3)
 */
public abstract class Sender {
	
	/**
	 * Class loader for modules
	 */
	private ClassLoader loader;

	/**
	 * This method is called by {@link Notifier} after an instance of a class implementing this interface is created
	 * with the default constructor. Each implementing class should take what is useful for it from the given
	 * configuration, taking into account (only) the keys with the given prefix.
	 * 
	 * @param prop configuration contents
	 * @param keyPrefix prefix for the subset of options that are relevant to this instance
	 * @return true if the initialization was ok, false if not (missing option etc) 
	 */
	public abstract boolean init(ExtProperties prop, String keyPrefix);
	
	/**
	 * Send the message to the target (mail / instant message / log file etc)
	 * 
	 * @param m message to send
	 * @return true if sending went ok, false if not
	 */
	public abstract boolean send(Message m); 
	
	/**
	 * Give an extra class loader, in case we need to load arbitrary classes
	 * 
	 * @param loader
	 */
	public final void setClassLoader(final ClassLoader loader){
		this.loader = loader;
	}
	
	/**
	 * Get the extra class loader
	 * 
	 * @return extra class loader
	 */
	public final ClassLoader getClassLoader(){
		return this.loader;
	}
	
	/**
	 * Load and create a class instance, with this order of loading:<ul>
	 * <li>extra class loader (set by {@link #setClassLoader(ClassLoader)})</li>
	 * <li>class loader of the current thread</li>
	 * <li>current thread context class loader</li>
	 * <li>class loader of this object instance</li>
	 * <li>JVM default class loader</li>
	 * </ul>
	 * <br>
	 * If none of these succeeds then return <code>null</code>.
	 * 
	 * @param sClass
	 * @return instance or null
	 */
	@SuppressWarnings("nls")
	public final Object getClassInstance(final String sClass){
		if (sClass==null || sClass.length()==0)
			return null;
		
		for (ClassLoader cl: new ClassLoader[]{
				this.loader,
				Thread.currentThread().getClass().getClassLoader(),
				Thread.currentThread().getContextClassLoader(),
				this.getClass().getClassLoader(),
				null}
		){
			try{
				Object o = Class.forName(sClass, true, cl).newInstance();
				Log.log(Log.FINE, "lazyj.notifications.Sender", "Loaded '"+sClass+"' with: "+cl);
				return o;
			}
			catch (Throwable t){
				Log.log(Log.FINE, "lazyj.notifications.Sender", "Failed to load '"+sClass+"' with: "+cl);
			}
		}
		
		Log.log(Log.ERROR, "lazyj.notifications.Sender", "Cannot instantiate '"+sClass+"'");
		
		return null;
	}
	
}
