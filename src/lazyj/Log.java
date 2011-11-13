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
import java.util.logging.Level;
import java.util.logging.Logger;

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
	@SuppressWarnings("nls")
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
	
	/**
	 * Use Java logger instead of custom files. This will assume that the Java logger is configured externally.
	 */
	private static boolean useJavaLogger = false;
	
	static {
		String sFolder = Utils.getLazyjConfigFolder();

		ExtProperties pTemp;

		try {
			if (sFolder==null){
				System.err.println("lazyj.Log : system property 'lazyj.config.folder' is not defined, will use Java logger instead"); //$NON-NLS-1$
				useJavaLogger = true;
				pTemp = new ExtProperties();
			}
			else{
				pTemp = new ExtProperties(sFolder, "logging"); //$NON-NLS-1$
				
				if (pTemp.getb("use_java_logger", false)){ //$NON-NLS-1$
					useJavaLogger = true;
				}
				else{
					pTemp.setAutoReload(30*1000);
					pTemp.addObserver(new Observer(){
						@Override
						public void update(final Observable o, final Object arg) {
							reload();
						}			
					});
				}
			}
		}
		catch (Throwable t) {
			pTemp = new ExtProperties();
			
			System.err.println("Cannot load logging properties because : "+t+ '('+t.getMessage()+')'); //$NON-NLS-1$
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
		if (useJavaLogger){
			final Logger logger = Logger.getLogger(sComponent);
			
			final Level level = logger.getLevel();
			
			final int l = level.intValue();
			
			if (l<=Level.FINEST.intValue())
				return Integer.valueOf(FINEST); 
			if (l<=Level.FINER.intValue())
				return Integer.valueOf(FINER);
			if (l<=Level.FINE.intValue())
				return Integer.valueOf(FINE); 
			if (l<=Level.INFO.intValue())
				return Integer.valueOf(INFO); 
			if (l<=Level.WARNING.intValue())
				return Integer.valueOf(WARNING); 
			if (l<=Level.SEVERE.intValue())
				return Integer.valueOf(ERROR); 

			return Integer.valueOf(FATAL);
		}
		
		Integer i = mLevel.get(sComponent);

		if (i == null) {
			final int idx = sComponent.lastIndexOf('.');
			
			final Integer iParent;
			
			if (idx>=0){
				iParent = getLevel(sComponent.substring(0, idx));
			}
			else{
				iParent = Integer.valueOf(logProp.geti("default.level", WARNING)); //$NON-NLS-1$
			}
			
			i = Integer.valueOf(logProp.geti(sComponent + ".level", iParent.intValue())); //$NON-NLS-1$

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
				sParent = logProp.gets("logdir", "/var/log/java"); //$NON-NLS-1$  //$NON-NLS-2$
			}
			
			sDir = logProp.gets(sComponent + ".logdir", sParent); //$NON-NLS-1$
			
			if (!sDir.endsWith("/")) //$NON-NLS-1$
				sDir += '/';

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
		if (useJavaLogger){
			final Logger logger = Logger.getLogger(sComponent);
			return logger.isLoggable(getJavaLoggerLevel(level));
		}
		
		return level <= getLevel(sComponent).intValue();
	}

	/**
	 * Time formatting
	 */
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

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
	 * @param sMessage message to log
	 */
	public static void log(final int level, final String sMessage) {
		final Throwable t = new Throwable();
        final StackTraceElement methodCaller = t.getStackTrace()[1];
        
		log(level, methodCaller.getClassName()+"#"+methodCaller.getMethodName()+":"+methodCaller.getLineNumber(), sMessage); //$NON-NLS-1$ //$NON-NLS-2$
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
			throw new IllegalArgumentException("level must be between 0 (FATAL) and 6 (FINEST)"); //$NON-NLS-1$
		}
		
		if (useJavaLogger){
			final Logger logger = Logger.getLogger(sComponent);
			
			logger.log(getJavaLoggerLevel(level), sMessage);
			
			return;
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
				System.err.println("LazyJ will log to stderr instead of '"+sFile+"' because it cannot write there : "+ioe); //$NON-NLS-1$ //$NON-NLS-2$
				
				pw = new PrintWriter(System.err);
			}
			
			mFiles.put(sFile, pw);
		}

		pw.println(getTime() + " : " + sComponent + " : "+sMessage); //$NON-NLS-1$ //$NON-NLS-2$
		pw.flush();
	}
	
	/**
	 * If Java logging is used, convert the levels to the other system
	 * 
	 * @param level
	 * @return corresponding Java logging level
	 */
	private static final Level getJavaLoggerLevel(final int level){
		switch (level){
			case FATAL: return Level.ALL;
			case ERROR: return Level.SEVERE;
			case WARNING : return Level.WARNING;
			case INFO: return Level.INFO;
			case FINE: return Level.FINE;
			case FINER: return Level.FINER;
			case FINEST: return Level.FINEST;
			default: return Level.OFF;
		}
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

				sExtra.append('\n').append(t.getClass().getName()).append(" : ").append(t.toString()).append(" (").append(t.getMessage()).append(')'); //$NON-NLS-1$ //$NON-NLS-2$
				
				append(sExtra, t.getStackTrace());
			} 
			else
			if (o instanceof Thread) {
				final Thread t = (Thread) o;
				
				sExtra.append('\n').append(t.getClass().getName()).append(" (").append(t.getName()).append(')'); //$NON-NLS-1$
				
				append(sExtra, t.getStackTrace());
			}
			if (o instanceof String) {
				sExtra.append('\n').append((String) o);
			}
			else {
				sExtra.append('\n').append(o.getClass().getName()).append('\n').append(o.toString());
			}
		}
		else{
			sExtra.append("\nNULL"); //$NON-NLS-1$
		}

		log(level, sComponent, sExtra.toString());
	}

	/**
	 * @param sb
	 * @param vst
	 */
	private static void append(final StringBuilder sb, final StackTraceElement[] vst){
		for (StackTraceElement ste : vst)
			sb.append("\n  ").append(ste.toString()); //$NON-NLS-1$		
	}
	
}
