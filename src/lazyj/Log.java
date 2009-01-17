package lazyj;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logging facility. Provides package- and class-based logging level and destination folder.
 * 
 * @author costing
 * @since 2006-10-16
 */
public final class Log {

	/**
	 * logging settings
	 */
	private static final ExtProperties logProp;

	/**
	 * Constant for the FATAL log level
	 */
	public final static int FATAL = 0;

	/**
	 * Constant for the ERROR log level
	 */
	public final static int ERROR = 1;

	/**
	 * Constant for the WARNING log level
	 */
	public final static int WARNING = 2;

	/**
	 * Constant for the INFO log level
	 */
	public final static int INFO = 3;

	/**
	 * Constant for the FINE log level
	 */
	public final static int FINE = 4;
	
	/**
	 * Constant for the FINER log level
	 */
	public final static int FINER = 5;
	
	/**
	 * Constant for the FINEST log level
	 */
	public final static int FINEST = 6;

	/**
	 * File names for each level
	 */
	private static final String[] sFiles = new String[] { "fatal", "error",	"warning", "info", "fine", "finer", "finest"};

	/**
	 * Cache open log files
	 */
	private static final Map<String, PrintWriter> mFiles = new ConcurrentHashMap<String, PrintWriter>();

	/**
	 * Cache log levels
	 */
	private static final Map<String, Integer> mLevel = new ConcurrentHashMap<String, Integer>();

	/**
	 * Cache target log dir
	 */
	private static final Map<String, String> mDirs = new ConcurrentHashMap<String, String>();
	
	/**
	 * Reload configuration properties
	 */
	static final void reload(){
		mLevel.clear();
		mFiles.clear();
		mDirs.clear();
	}
	
	static {
		String sFolder = Utils.getLazyjConfigFolder();

		ExtProperties pTemp;

		try {
			if (sFolder==null){
				System.err.println("lazyj.Log : system property 'lazyj.config.folder' is not defined.");
				pTemp = new ExtProperties();
			}
			else{
				pTemp = new ExtProperties(sFolder, "logging");
				pTemp.setAutoReload(30*1000);
				pTemp.addObserver(new Observer(){
					public void update(final Observable o, final Object arg) {
						reload();
					}			
				});
			}
		}
		catch (Throwable t) {
			pTemp = new ExtProperties();
			
			System.err.println("Cannot load logging properties because : "+t+ " ("+t.getMessage()+")");
			t.printStackTrace();
		}

		logProp = pTemp;

		reload();
	}

	
	/**
	 * Get the current logging level for a component
	 * 
	 * @param sComponent component to get the level for
	 * @return the logging level
	 */
	public static Integer getLevel(final String sComponent){
		Integer i = mLevel.get(sComponent);

		if (i == null) {
			final int idx = sComponent.lastIndexOf('.');
			
			final Integer iParent;
			
			if (idx>=0){
				iParent = getLevel(sComponent.substring(0, idx));
			}
			else{
				iParent = Integer.valueOf(logProp.geti("default.level", WARNING));
			}
			
			i = Integer.valueOf(logProp.geti(sComponent + ".level", iParent.intValue()));

			mLevel.put(sComponent, i);
		}
		
		return i;
	}

	/**
	 * For a given component, get the folder where the log files should be put.
	 * 
	 * @param sComponent component (class hierarchy)
	 * @return the target log dir
	 */
	public static String getLogDir(final String sComponent){
		String sDir = mDirs.get(sComponent);
		
		if (sDir == null){
			final int idx = sComponent.lastIndexOf('.');
			
			final String sParent;
			
			if (idx>=0){
				sParent = getLogDir(sComponent.substring(0, idx));
			}
			else{
				sParent = logProp.gets("logdir", "/var/log/java");
			}
			
			sDir = logProp.gets(sComponent + ".logdir", sParent);
			
			if (!sDir.endsWith("/"))
				sDir += "/";

			mDirs.put(sComponent, sDir);
		}
		
		return sDir;
	}
	
	/**
	 * Check if a message of a given level for a given component will be actually logged. If it would not
	 * be logged then it makes no sense to try to log the message in the first place. You should use the logger
	 * with constructions like this when the log message is complicated to build:<br>
	 * <code><pre>
	 * if (Log.isLoggable(Log.DEBUG, "class.name")){
	 *     Log.log(Log.DEBUG, "class.name", "Some complicated message here : "+object.toString());
	 * }
	 * </pre></code>
	 * 
	 * @param level desired logging level
	 * @param sComponent component
	 * @return true if the message will be logged, false otherwise
	 */
	public static boolean isLoggable(final int level, final String sComponent) {
		return level <= getLevel(sComponent).intValue();
	}

	/**
	 * Time formatting
	 */
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * @return time
	 */
	private static final String getTime(){
		synchronized (sdf){
			return sdf.format(new Date());
		}
	}
	
	/**
	 * Log a message of a given severity for a given component
	 * 
	 * @param level severity of the message. Check the constants in this class for possible values.
	 * @param sComponent component name. Usually "servlet_zone/servlet_name", but could be the class name or something else you want
	 * @param sMessage message to log
	 */
	public static void log(final int level, final String sComponent, final String sMessage) {
		if (level < 0 || level > FINEST) {
			throw new IllegalArgumentException("level must be between 0 (FATAL) and 6 (FINEST)");
		}

		if (!isLoggable(level, sComponent))
			return;

		final String sFile = getLogDir(sComponent) + sFiles[level];

		PrintWriter pw = mFiles.get(sFile);

		if (pw == null) {
			try {
				pw = new PrintWriter(new FileWriter(sFile, true), true);
			}
			catch (IOException ioe) {
				System.err.println("LazyJ will log to stderr instead of '"+sFile+"' from now on because "+ioe);
				
				pw = new PrintWriter(System.err);
			}
			
			mFiles.put(sFile, pw);
		}

		pw.println(getTime() + " : " + sComponent + " : "+sMessage);
		pw.flush();
	}

	/**
	 * Log a message of a given severity for a given component, with an attached object.
	 * 
	 * @param level severity of the message. Check the constants in this class for possible values.
	 * @param sComponent component name. Usually "servlet_zone/servlet_name", but could be the class name or something else you want
	 * @param sMessage message to log
	 * @param o object to attach, it is intended for use with a Throwable here, but works for any other objects too by invoking the .toString() method on them
	 */
	public static void log(final int level, final String sComponent, final String sMessage, final Object o) {
		final StringBuilder sExtra = new StringBuilder(1000);

		sExtra.append(sMessage);

		if (o != null) {
			if (o instanceof Throwable) {
				final Throwable t = (Throwable) o;

				sExtra.append("\n").append(t.getClass().getName()).append(" : ").append(t.toString()).append(" (").append(t.getMessage()).append(")");
				
				append(sExtra, t.getStackTrace());
			} 
			else
			if (o instanceof Thread) {
				final Thread t = (Thread) o;
				
				sExtra.append("\n").append(t.getClass().getName()).append(" (").append(t.getName()).append(")");
				
				append(sExtra, t.getStackTrace());
			}
			if (o instanceof String) {
				sExtra.append("\n").append((String) o);
			}
			else {
				sExtra.append("\n").append(o.getClass().getName()).append("\n").append(o.toString());
			}
		}
		else{
			sExtra.append("\nNULL");
		}

		log(level, sComponent, sExtra.toString());
	}

	/**
	 * @param sb
	 * @param vst
	 */
	private static void append(final StringBuilder sb, final StackTraceElement[] vst){
		for (StackTraceElement ste : vst)
			sb.append("\n  ").append(ste.toString());		
	}
	
}
