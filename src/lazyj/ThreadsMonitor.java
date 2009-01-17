package lazyj;

import java.util.concurrent.DelayQueue;

/**
 * This class is used to monitor servlet threads, killing the ones that run for longer than it was specified.
 * See {@link lazyj.ExtendedServlet#getMaxRunTime()} for more information on how to modify the maximum run time for
 * a servlet.
 * 
 * @author costing
 * @since 2006-10-04
 */
public final class ThreadsMonitor extends Thread {
	/**
	 * Thread watch queue
	 */
	private static final DelayQueue<BoundedThreadContainer> queue = new DelayQueue<BoundedThreadContainer>();
	
	/**
	 * The monitoring thread
	 */
	private static final ThreadsMonitor monitor;
	
	static{
		monitor = new ThreadsMonitor();
		monitor.start();
	}
	
	/**
	 * Set thread name
	 */
	private ThreadsMonitor() {
		super("lazyj.ThreadsMonitor - killing runaway servlets");
		setDaemon(true);
	}
	
	/**
	 * The monitoring thread, takes the next thread (servlet) that ran for longer than expected and kills it
	 */
	@Override
	public void run() {
		while (true) {
			try{
				final BoundedThreadContainer btc = queue.take();
				
				if (btc.getThread().isAlive()){					
					Log.log(Log.WARNING, btc.getPath(), "ThreadsMonitor killing one thread", btc.getThread());
				
					btc.getThread().interrupt();
				}
			}
			catch (Throwable t){
				// ignore all the badness in the world
			}
		}
	}
	
	/**
	 * Register a thread that must be allowed to run but for a limited amount of time.
	 * This is used by the {@link lazyj.ExtendedServlet} wrapper to keep under control the servlets,
	 * but you could use it for any other thread.
	 * 
	 * @param btc the wrapper for the thread to be monitored
	 */
	public static void register(final BoundedThreadContainer btc){
		queue.offer(btc);
	}
	
	/**
	 * Remove a previously added wrapper. Use this when the thread
	 * correctly finishes the work. You should explicitly call this when the work is done
	 * because otherwise you will probably kill something else that could be running later
	 * under that thread, and you don't want this, right? :)
	 * 
	 * @param btc
	 */
	public static void unregister(final BoundedThreadContainer btc){
		queue.remove(btc);
	}
}
