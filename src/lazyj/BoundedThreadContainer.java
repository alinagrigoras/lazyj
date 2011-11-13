/**
 * Wrapper class for the threads that represent active requests combined with the maximum time until which
 * the servlet is allowed to run. After the expiration time the thread is forcefully stopped.
 */
package lazyj;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * @author costing
 * @since 2006-10-15
 */
final class BoundedThreadContainer implements Delayed {

	/**
	 * Timestamp when this thread will have to die
	 */
	private final long	lExpires;

	/**
	 * Thread to watch
	 */
	private final Thread	t;

	/**
	 * What is the servlet path, for the logs to have some meaning
	 */
	private final String	sPath;

	/**
	 * Create a watcher for a thread that will let it run for the maximum specified amount of time from now.
	 * 
	 * @param time maximum run time for this thread, in seconds
	 * @param thread thread to monitor
	 * @param sServletPath the path of the servlet that is executed, or some other arbitrary string to put in the logs when the thread is killed.
	 */
	public BoundedThreadContainer(final int time, final Thread thread, final String sServletPath) {
		this.lExpires = System.currentTimeMillis() + time * 1000L;
		this.t = thread;
		this.sPath = sServletPath;
	}

	/**
	 * Get the path (servlet) that the thread was executing. Used for logging the kill events.
	 * 
	 * @return the path of the servlet
	 */
	String getPath(){
		return this.sPath;
	}

	/**
	 * Package protected method to get the thread that is monitored
	 * 
	 * @return the thread that is monitored
	 */
	Thread getThread(){
		return this.t;
	}
	
	/**
	 * Implementation of the Delayed interface
	 * 
	 * @param unit the reference time unit
	 * @return the remaining execution time
	 */
	@Override
	public long getDelay(final TimeUnit unit) {
		return unit.convert(this.lExpires - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
	}

	/**
	 * Implementation of the Delayed interface
	 * 
	 * @param o object to compare to
	 * @return which one expires first
	 */
	@Override
	public int compareTo(final Delayed o) {
		final long lDiff = getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS);
		
		if (lDiff>0)
			return 1;
		
		if (lDiff<0)
			return -1;
		
		return 0;
	}
	
	/**
	 * Override the default equals() method
	 */
	@Override
	public boolean equals(final Object o){
		if (o==null || !(o instanceof Delayed))
			return false;
		
		return compareTo((Delayed) o)==0;
	}
	
	/**
	 * Override the default equals() method
	 */
	@Override
	public int hashCode(){
		return (int) this.lExpires;
	}
}
