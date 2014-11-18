package lazyj;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Wrapper for JDBC connections and collection of useful functions related to database connections.
 * It is also a connection pool, recycling previously established sessions and closing the idle
 * ones.
 * 
 * @author costing
 * @since Oct 15, 2006
 */
public class DBFunctions {

	/**
	 * List of connections for each known target
	 */
	static final HashMap<String, LinkedList<DBConnection>>			hmConn				= new HashMap<>();
	
	/**
	 * Was this the first row ?
	 */
	private boolean													first;

	/**
	 * Flag to indicate if the last query was an update or a select one
	 */
	private boolean													bIsUpdate;

	/**
	 * Current database connection
	 */
	private DBConnection											dbc;

	/**
	 * Current ResultSet
	 */
	private ResultSet												rsRezultat;

	/**
	 * Number of opened connection
	 */
	static volatile long											lOpened				= 0;

	/**
	 * Number of closed connections
	 */
	static volatile long											lClosed				= 0;

	/**
	 * Number of closed connections on object deallocation (lost ones ?!)
	 */
	static volatile long											lClosedOnFinalize	= 0;

	/**
	 * Total number of executed queries
	 */
	private static volatile long									lQueryCount			= 0;

	/**
	 * For statistics: how many queries were executed on each connection.
	 */
	public static final ConcurrentHashMap<String, AtomicInteger>	chmQueryCount		= new ConcurrentHashMap<>();

	/**
	 * For statistics: total time to execute the queries on each of the connections.
	 */
	public static final ConcurrentHashMap<String, AtomicLong>		chmQueryTime		= new ConcurrentHashMap<>();

	/**
	 * Configuration options
	 */
	final Properties												prop;
	
	/**
	 * JDBC driver class name
	 */
	final String													driver;
	
	/**
	 * JDBC connection string
	 */
	final String													jdbcConnectionString;
	
	/**
	 * Unique key identifying this connection in the map
	 */
	final String													uniqueKey;
	
	/**
	 * Set to <code>true</code> to signal that the following query is read-only and, if available, a slave could be used to execute it
	 */
	private boolean													readOnlyQuery = false;
	
	/**
	 * Create a connection to the database using the parameters in this properties file. The
	 * following keys are extracted:<br>
	 * <ul>
	 * <li><b>driver</b> : (required) one of org.postgresql.Driver, com.mysql.jdbc.Driver or com.microsoft.jdbc.sqlserver.SQLServerDriver</li>
	 * <li><b>url</b> : (required) full JDBC URL. If found it would be preferred instead of the following keys (database, host and port)</li>
	 * <li><b>database</b> : (required; alternative) name of the database to connect to</li>
	 * <li><b>host</b> : (optional) server's ip address, defaults to 127.0.0.1</li>
	 * <li><b>port</b> : (optional) tcp port to connect to on the <i>host</i>, if it is missing the default port for each database type is used</li>
	 * <li><b>user</b> : (recommended) supply this account name when connecting</li>
	 * <li><b>password</b> : (recommended) password for the account</li>
	 * </ul>
	 * Any other keys will be passed as arguments to the driver. You might be interested in:<br>
	 * <ul>
	 * <li><a href="http://dev.mysql.com/doc/refman/5.1/en/connector-j-reference-configuration-properties.html" target=_blank>MySQL</a>:
	 * <ul>
	 * <li><b>connectTimeout</b> : timeout in milliseconds for a new connection, default is 0 infinite)</li>
	 * <li><b>useCompression</b> : true/false, default false</li>
	 * </ul>
	 * </li>
	 * <li><a href="http://jdbc.postgresql.org/documentation/93/connect.html" target=_blank>PostgreSQL</a>:
	 *   <ul>
	 *     <li><b>ssl</b> : present=true for now</li>
	 *     <li><b>charSet</b> : string</li>
	 *   </ul>
	 * </li>
	 * </ul>
	 * check the provided links for more information.
	 * 
	 * @param configProperties
	 *            connection options
	 */
	public DBFunctions(final ExtProperties configProperties) {
		this(configProperties.getProperties());
	}
	
	/**
	 * @param configProperties
	 * @see #DBFunctions(ExtProperties)
	 */
	public DBFunctions(final Properties configProperties){
		this.prop = configProperties;
		this.dbc = null;
		this.first = false;
		this.bIsUpdate = true;
		this.rsRezultat = null;
		
		this.driver = this.prop.getProperty("driver"); //$NON-NLS-1$
		this.jdbcConnectionString = propToJDBC(this.prop);
		this.uniqueKey = getKey();
	}

	/**
	 * Create a connection to the database using the parameters in this properties file, then
	 * execute the given query.
	 * 
	 * @param configProperties
	 *            connection parameters
	 * @param sQuery
	 *            query to execute after connecting
	 * @see DBFunctions#DBFunctions(ExtProperties)
	 */
	public DBFunctions(final Properties configProperties, final String sQuery){
		this(configProperties);
		
		query(sQuery);
	}
	
	/**
	 * Create a connection to the database using the parameters in this properties file, then
	 * execute the given query.
	 * 
	 * @param configProperties
	 *            connection parameters
	 * @param sQuery
	 *            query to execute after connecting
	 * @see DBFunctions#DBFunctions(ExtProperties)
	 */
	public DBFunctions(final ExtProperties configProperties, final String sQuery) {
		this(configProperties.getProperties());

		query(sQuery);
	}
	
	/**
	 * If you already have the full JDBC connection URL, connect like this.
	 * 
	 * @param driverClass JDBC driver class name
	 * @param jdbcURL JDBC connection URL
	 * @see DBFunctions#DBFunctions(String, String, Properties)
	 */
	public DBFunctions(final String driverClass, final String jdbcURL){
		this(driverClass, jdbcURL, null);
	}
	
	/**
	 * If you already have the full JDBC connection URL, connect like this
	 * 
	 * @param driverClass JDBC driver class name
	 * @param jdbcURL full JDBC connection URL
	 * @param configProperties extra configuration options. Can be <code>null</code> if the URL has everything in it
	 * @see #DBFunctions(ExtProperties)
	 */
	public DBFunctions(final String driverClass, final String jdbcURL, final Properties configProperties){
		this.driver = driverClass;
		this.jdbcConnectionString = jdbcURL;
		this.prop = configProperties;
		this.uniqueKey = jdbcURL;
	}

	/**
	 * If you already have the full JDBC connection URL, connect like this.
	 * 
	 * @param driverClass JDBC driver class name
	 * @param jdbcURL full JDBC connection URL
	 * @param configProperties extra configuration options
	 * @param sQuery query to execute 
	 * @see #DBFunctions(ExtProperties)
	 */	
	public DBFunctions(final String driverClass, final String jdbcURL, final Properties configProperties, final String sQuery){
		this(driverClass, jdbcURL, configProperties);
		
		query(sQuery);
	}
	
	/**
	 * From the current connections try to find out if there is any one of them that is free
	 * 
	 * @param sConn
	 * @return a free connection, or null
	 */
	private final static DBConnection getFreeConnection(final String sConn) {
		LinkedList<DBConnection> ll;
		
		synchronized (hmConn) {
			ll = hmConn.get(sConn);

			if (ll==null){
				ll = new LinkedList<>();
				hmConn.put(sConn, ll);
				
				return null;
			}
		}
		
		synchronized (ll) {
			for (final DBConnection dbt : ll) {
				if (dbt.canUse()) {
					dbt.use();
					return dbt;
				}
			}
		}

		return null;
	}

	/**
	 * Build a unique key
	 * 
	 * @return unique key
	 */
	private String getKey() {
		return this.jdbcConnectionString;
	}
	
	/**
	 * Signal that the following query is read-only and, if available, a database slave could be used to execute it. 
	 * 
	 * @param readOnly if <code>true</code> then the query can potentially go to a slave, if <code>false</code> then only the master can execute it 
	 * @return previous value of the read-only flag
	 */
	public boolean setReadOnly(final boolean readOnly){
		boolean previousValue = this.readOnlyQuery;
		
		this.readOnlyQuery = readOnly;
		
		return previousValue;
	}

	/**
	 * Get the current value of the read-only flag
	 * 
	 * @return read-only flag
	 */
	public boolean isReadOnly(){
		return this.readOnlyQuery;
	}
	
	/**
	 * Check if this connection is done to a PostgreSQL database (if we are using the PG JDBC driver)
	 * 
	 * @return true if the connection is done to a PostgreSQL database
	 */
	public boolean isPostgreSQL(){
		return this.jdbcConnectionString.indexOf("postgres")>=0; //$NON-NLS-1$
	}
	
	/**
	 * Check if this connection is done to a MySQL database (if we are using the MySQL JDBC driver)
	 * 
	 * @return true if the connection is done to a MySQL database
	 */
	public boolean isMySQL(){
		return this.jdbcConnectionString.indexOf("mysql")>=0; //$NON-NLS-1$
	}
	
	/**
	 * Reason why the last connect() attempt failed
	 */
	private String sConnectFailReason = null;
	
	/**
	 * Get the reason why the last connect() attempt has failed.
	 * 
	 * @return reason, if there is any, or <code>null</code> if the connection actually worked
	 */
	public String getConnectFailReason(){
		return this.sConnectFailReason;
	}
		
	/**
	 * Initialize a database connection. First it will try to take a free one from the pool. If there is no free connection it will
	 * try to establish a new one, only if there are less than 100 connections to this particular database in total. 
	 * 
	 * @return <code>true</code> if the connection was established and <code>this.dbc</code> can be used, <code>false</code> if not.
	 */
	private final boolean connect() {
		for (int i = 0; i < 3; i++) {
			this.dbc = getFreeConnection(this.uniqueKey);

			if (this.dbc != null){
				this.sConnectFailReason = null;
				
				return true;
			}
			
			final LinkedList<DBConnection> ll;
			
			synchronized (hmConn) {
				ll = hmConn.get(this.uniqueKey);
			}

			boolean addNew = false;
			
			synchronized (ll){
				if (ll.size() < 100) {
					addNew = true;
				}
			}
			
			if (addNew){
				this.dbc = new DBConnection(this.driver, this.jdbcConnectionString, this.prop, this.uniqueKey);
				
				if (this.dbc.canUse()) {
					this.sConnectFailReason = null;
					
					this.dbc.use();
					
					synchronized (ll){
						ll.add(this.dbc);
					}
					
					return true;
				}
				
				this.sConnectFailReason = "Cannot establish new DB connection"; //$NON-NLS-1$
				
				this.dbc.close();
				this.dbc = null;
			}
			else{
				this.sConnectFailReason = "There are already 100 established connections to the DB, refusing to establish another one"; //$NON-NLS-1$
			}

			try {
				Thread.sleep(50);
			} catch (final InterruptedException e) {
				// ignore improbable interruption
			}
		}

		return false;
	}
	
	/**
	 * Get a raw database connection wrapper. Remember to <b>always</b> {@link DBConnection#free()} or {@link DBConnection#close()} at the end of the section where you use it!
	 * 
	 * @return database connection wrapper or <code>null</code> if a connection cannot be established
	 * @see DBConnection
	 */
	public final DBConnection getConnection(){
		if (connect())
			return this.dbc;
		
		return null;
	}

	static {
		System.setProperty("PGDATESTYLE", "ISO"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Build a JDBC URL connection string for a bunch of parameters
	 * 
	 * @param prop
	 * @return JDBC URL connection string, or <code>null</code> if for any reason it cannot be built (unknown driver?)
	 */
	public static final String propToJDBC(final Properties prop){
		final String url = prop.getProperty("url", "");  //$NON-NLS-1$//$NON-NLS-2$
		
		if (url.startsWith("jdbc:")) //$NON-NLS-1$
			return url;
		
		/*
		 * See here for JDBC URL examples:
		 * http://www.petefreitag.com/articles/jdbc_urls/
		 */			
		final StringBuilder connection = new StringBuilder("jdbc:"); //$NON-NLS-1$

		final String driver = prop.getProperty("driver", ""); //$NON-NLS-1$ //$NON-NLS-2$
		
		final boolean isMySQL = driver.indexOf("mysql") >= 0; //$NON-NLS-1$
		final boolean isPostgreSQL = driver.indexOf("postgres") >= 0; //$NON-NLS-1$
		final boolean isMSSQL = driver.indexOf("sqlserver") >= 0;  //$NON-NLS-1$
		
		if (isMySQL)
			connection.append("mysql:"); //$NON-NLS-1$
		else if (isPostgreSQL)
			connection.append("postgresql:"); //$NON-NLS-1$
		else if (isMSSQL)
			connection.append("microsoft:sqlserver:"); //$NON-NLS-1$
		else {
			// UNKNOWN DRIVER
			return null;
		}

		connection.append("//").append(prop.getProperty("host", "127.0.0.1"));  //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$

		final String sPort = prop.getProperty("port");  //$NON-NLS-1$
		
		if (sPort!=null && sPort.length() > 0)
			connection.append(':').append(sPort);

		if (isMySQL || isPostgreSQL)
			connection.append('/').append(prop.getProperty("database", ""));  //$NON-NLS-1$//$NON-NLS-2$
		else
		if (isMSSQL)
			connection.append(";databaseName=").append(prop.getProperty("database", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
		return connection.toString();
	}

	/**
	 * Wrapper around a raw database connection. You cannot create this object directly and you <b>must</b> free the connections properly otherwise
	 * you will run in big trouble.<br>
	 * <br>
	 * Here is a sample code:<br>
	 * <br>
	 * <code><pre>
	 * // set the connection parameters <br>
	 * ExtProperties dbProp = new ExtProperties(); 
	 * dbProp.set("driver", "org.postgresql.Driver");	// mandatory
	 * dbProp.set("database", "somedb"); 				// mandatory
	 * dbProp.set("host", "127.0.0.1");					// defaults to 127.0.0.1 if missing
	 * dbProp.set("port", "5432");						// DB-dependend default if missing
	 * dbProp.set("user", "username"); 					// recommended
	 * dbProp.set("password", "*****"); 				// recommended
	 * // you can also set here various other configuration options that the JDBC driver will look at
	 * 
	 * DBFunctions db = new DBFunctions(dbProp);
	 * 
	 * DBFunctions.DBConnection conn = db.getConnection();
	 * 
	 * if (conn==null) return;
	 * 
	 * Statement stat = null;
	 * ResultSet rs = null;
	 * 
	 * try{
	 *      stat = conn.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
	 *      stat.execute(someQuery, Statement.NO_GENERATED_KEYS);
	 *      
	 *      rs = stat.getResultSet();
	 *      
	 *      // ..................
	 *      
	 *      rs.close();
	 *      rs = null;
	 *      
	 *      stat.close();
	 *      stat = null;
	 * }
	 * catch (Exception e){ ... }
	 * <b>finally</b>{
	 * 		if (rs!=null){
	 * 			// close
	 * 			try{
	 * 				rs.close();
	 * 			}
	 * 			catch (Exception e){
	 * 			}
	 * 		}
	 * 		
	 * 		if (stat!=null){
	 * 			try{
	 * 				stat.close();
	 * 			}
	 * 			catch (Exception e){
	 * 			}
	 * 		}
	 * 		
	 * 		<b>conn.free();</b>
	 * }
	 * </pre></code>
	 * 
	 * @author costing
	 * @since Jan 17, 2009
	 * @see DBFunctions#DBFunctions(ExtProperties)
	 */
	public static final class DBConnection {

		/**
		 * Actual JDBC connection
		 */
		private Connection		conn;

		/**
		 * 0 - not connected 1 - ready 2 - in use 3 - error or disconnected
		 */
		int						iBusy;

		/**
		 * When this cached connection was last used
		 */
		long					lLastAccess;

		/**
		 * Connection key
		 */
		private final String	sConn;
		
		/**
		 * Description for this connection, set by an outside entity.
		 */
		private String description = null;
		
		/**
		 * Establish a new connection. Cannot be called directly, you have to use {@link DBFunctions#getConnection()} for example.
		 * 
		 * @param prop    connection properties
		 * @param _sConn  connection key
		 */
		DBConnection(final Properties prop, final String _sConn) {
			this.sConn = _sConn;

			final String driver = prop.getProperty("driver"); //$NON-NLS-1$
			
			if (driver==null){
				this.iBusy = 3;
				return;
			}
			
			final String sURL = propToJDBC(prop);
			
			if (sURL==null){
				this.iBusy=3;
				return;
			}
			
			init(driver, sURL, prop);
		}

		/**
		 * Other constructor type, based on the driver class and the full JDBC URL
		 * 
		 * @param driverClass driver class name
		 * @param jdbcURL pre-built JDBC URL
		 * @param prop other connection properties
		 * @param connDescr some description for logging
		 */
		DBConnection(final String driverClass, final String jdbcURL, final Properties prop, final String connDescr){
			this.sConn = connDescr;
			
			init(driverClass, jdbcURL, prop);
		}
		
		/**
		 * Initialize the connection
		 * 
		 * @param driverClass
		 * @param jdbcURL
		 * @param prop
		 */
		private final void init(final String driverClass, final String jdbcURL, final Properties prop){
			this.iBusy = 0;

			lOpened++;

			final Driver driver;
			
			try {
				driver = (Driver) Class.forName(driverClass).newInstance();
			} catch (final Throwable e) {
				System.err.println("Cannot find driver '" + driverClass + "' : " + e + " (" + e.getMessage() + ")");    //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$
				this.iBusy = 3;
				return;
			}
			
			try{
				this.conn = driver.connect(jdbcURL, prop!=null ? prop : new Properties());
				this.iBusy = 1;
				
				setDescription(this.conn.toString());
			}
			catch (final SQLException e){
				// cannot establish a connection
				Log.log(Log.ERROR, "lazyj.DBFunctions", "Cannot connect to the target database", e);  //$NON-NLS-1$//$NON-NLS-2$
				
				this.iBusy = 3;
			}
		}
		
		/**
		 * Get the established JDBC connection for direct access to the database.
		 * 
		 * @return the JDBC connection
		 */
		public final Connection getConnection() {
			return this.conn;
		}

		/**
		 * Find out if this connection is free to use
		 * 
		 * @return true if free, false if busy or other error
		 */
		public final boolean canUse() {
			if (this.iBusy == 1){
				if (System.currentTimeMillis() - this.lLastAccess > 1000*60){
					boolean isValid = false;
					
					try{
						isValid = this.conn.isValid(10);
					}
					catch (final SQLException sqle){
						// ignore
					}
					
					if (!isValid)
						close();
					
					return isValid;
				}
				
				return true;
			}

			return false;
		}

		/**
		 * Use this connection, by marking it as busy and setting the last access time to the
		 * current time.
		 * 
		 * @return true if the connection was free and could be used, false if it was not available
		 */
		public final boolean use() {
			if (this.iBusy == 1) {
				this.iBusy = 2;

				this.lLastAccess = System.currentTimeMillis();

				return true;
			}

			return false;
		}

		/**
		 * Mark a previously used connection as free to be used by somebody else
		 * 
		 * @return true if the connection was in use and was freed, false if the connection was in
		 *         other state
		 */
		public final boolean free() {
			if (this.iBusy == 2) {
				this.iBusy = 1;
				return true;
			}
			close();
			return false;
		}

		/**
		 * Really close a connection to the database
		 */
		public final void close() {
			if (this.conn != null) {
				lClosed++;

				try {
					this.conn.close();
				} catch (final Exception e) {
					System.err.println("DBConnection: cannot close " + this.sConn + " (descr: "+getDescription()+") because : " + e + " (" + e.getMessage() + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				}

				this.conn = null;
			}

			this.iBusy = 3;
		}

		/**
		 * On object deallocation make sure that the connection is properly closed.
		 */
		@Override
		protected final void finalize() {
			if (this.conn != null) {
				try {
					this.conn.close();
					lClosedOnFinalize++;
				} catch (final Exception e) {
					System.err.println("DBConnection: cannot close " + this.sConn + " on finalize because : " + e + " (" + e.getMessage() + ")");  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$
				}
			}
		}

		/**
		 * Set the description to an arbitrary string to be used when debugging a problem.
		 * 
		 * @param description the description to set
		 */
		public void setDescription(final String description) {
			this.description = description;
		}

		/**
		 * Get the current description
		 * 
		 * @return the description
		 */
		public String getDescription() {
			return this.description;
		}
	}

	/**
	 * @author costing
	 * @since 15.12.2006
	 */
	static class CleanupThread extends Thread {

		/**
		 * Create the thread with some name to display in the stack trace
		 */
		public CleanupThread(){
			super("lazyj.DBFunctions: cleanup thread"); //$NON-NLS-1$
		}
		
		/**
		 * Flag to stop the cleanup thread. Used only when the machine stops or smth ...
		 */
		boolean	bShouldStop	= false;

		/**
		 * Connection cleaner thread, periodically checks for too many idle connection and closes
		 * them.
		 */
		@Override
		public void run() {
			long now;
			int iIdleCount;
			Iterator<DBConnection> it;
			DBConnection dbc;
			boolean bIdle;
			boolean bClose;
			int iClosedToGC = 0;

			while (!this.bShouldStop) {
				now = System.currentTimeMillis();

				synchronized (hmConn) {
					for (final Entry<String, LinkedList<DBConnection>> me : hmConn.entrySet()) {
						final LinkedList<DBConnection> ll = me.getValue();

						synchronized (ll){
							iIdleCount = 0;
							it = ll.iterator();
	
							while (it.hasNext()) {
								dbc = it.next();
	
								bIdle = (dbc.iBusy == 1);
	
								if (bIdle)
									iIdleCount++;
	
								// - in use for more than 2 min, such a query takes too long and we should remove the connection from the pool
								// - limit the number of idle connections
								// - any connection left in an error state by a query
								bClose = (dbc.iBusy == 2 && (now - dbc.lLastAccess > 1000 * 60 * 2)) || 
										 (bIdle && (iIdleCount > 5 || now - dbc.lLastAccess > 1000 * 60 * 5)) ||
										 (dbc.iBusy != 2 && dbc.iBusy != 1); // error case
	
								if (bClose) {
									iClosedToGC++;
	
									if (dbc.iBusy != 2) { // force connection close only if the object
										// is not in use
										dbc.close();
									} else {
										System.err.println("DBFunctions: Not closing busy connection (description: "+dbc.getDescription()+')'); //$NON-NLS-1$
									}
	
									it.remove();
									if (bIdle) // if it was idle but i decided to remove it
										iIdleCount--;
								}
							}
						}
					}
				}

				// when we remove connection make sure the resources are really freed by JVM 
				if (iClosedToGC > 20) {
					iClosedToGC = 0;
					System.gc();
				}

				try {
					Thread.sleep(2000);
				} catch (final InterruptedException e) {
					// ignore an interruption
				}
			}
		}

	}

	/**
	 * Cleanup thread
	 */
	private static CleanupThread	tCleanup	= null;

	static {
		startThread();
	}
	

	/**
	 * Start the cleanup thread. Should not be called externally since it is called automatically at
	 * the first use of this class.
	 */
	static public final synchronized void startThread() {
		if (tCleanup == null) {
			tCleanup = new CleanupThread();
			try {
				tCleanup.setDaemon(true);
			} catch (final Throwable e) {
				// it's highly unlikely for an exception to occur here
			}
			tCleanup.start();
		}
	}

	/**
	 * Signal the thread that it's time to stop. You should only call this when the JVM is about to
	 * shut down, and not even then it's necessary to do so.
	 */
	
	static public final synchronized void stopThread() {
		if (tCleanup != null) {
			tCleanup.bShouldStop = true;
			tCleanup = null;
		}
	}

	/**
	 * How many rows were changed by the last update query
	 */
	private int	iUpdateCount	= -1;

	/**
	 * Get the number of rows that were changed by the previous query.
	 * 
	 * @return number of changed rows, can be negative if the query was not an update one
	 */
	
	/**
	 * Get the number of rows affected by the last SQL update query.
	 * 
	 * @return number of rows
	 */
	public final int getUpdateCount() {
		return this.iUpdateCount;
	}

	/**
	 * Last SQL Statement
	 */
	private Statement	stat	= null;

	/**
	 * Explicitly close the allocated resources 
	 */
	public void close(){
		if (this.rsRezultat != null) {
			try {
				this.rsRezultat.close();
			} catch (final Throwable t) {
				// ignore this
			}
			
			this.rsRezultat = null;
		}

		if (this.stat != null) {
			try {
				this.stat.close();
			} catch (final Throwable t) {
				// ignore this
			}
			
			this.stat = null;
		}		
	}
	
	/**
	 * Override the default destructor to properly close any resources in use.
	 */
	@Override
	protected void finalize() {
		close();
	}
	
	/**
	 * Execute a query.
	 * 
	 * @param sQuery SQL query to execute
	 * @return <code>true</code> if the query succeeded, <code>false</code> if there was an error (connection or syntax).
	 * @see DBFunctions#query(String, boolean,Object...)
	 */
	public boolean query(final String sQuery) {
		return query(sQuery, false);
	}
	
	/**
	 * Statement constant for either enabling or disabling returning the IDs generated by INSERT queries
	 */
	private int generatedKeyRequest = Statement.NO_GENERATED_KEYS;
	
	/**
	 * Enable the fetching of the last generated ID
	 * 
	 * @param enabled set to <code>true</code> to be able to do getLastGeneratedKey()
	 * @return the previous setting
	 * @see #getLastGeneratedKey()
	 */
	public boolean setLastGeneratedKey(final boolean enabled){
		final boolean prev = this.generatedKeyRequest == Statement.RETURN_GENERATED_KEYS;
		
		this.generatedKeyRequest = enabled ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS;
		
		return prev;
	}
	
	/**
	 * Last generated key, if 
	 */
	private String lastGeneratedKey = null;
	
	/**
	 * Get the last generated key, after {@link #setLastGeneratedKey(boolean)} was called with <code>true</code>
	 * 
	 * @return the last generated key, as Integer, or <code>null</code> if there is any problem (or {@link #setLastGeneratedKey(boolean)} was not generated)
	 * @see #setLastGeneratedKey(boolean)
	 */
	public Integer getLastGeneratedKey(){
		if (this.lastGeneratedKey==null)
			return null;
		
		try{
			return Integer.valueOf(this.lastGeneratedKey);
		}
		catch (final NumberFormatException nfe){
			return null;
		}
	}
	
	/**
	 * Get the last generated key, after {@link #setLastGeneratedKey(boolean)} was called with <code>true</code>
	 * 
	 * @return the last generated key, as Long, or <code>null</code> if there is any problem (or {@link #setLastGeneratedKey(boolean)} was not generated)
	 * @see #setLastGeneratedKey(boolean)
	 */
	public Long getLastGeneratedKeyLong(){
		if (this.lastGeneratedKey==null)
			return null;
		
		try{
			return Long.valueOf(this.lastGeneratedKey);
		}
		catch (final NumberFormatException nfe){
			return null;
		}
	}
	
	/**
	 * Execute an error and as an option you can force to ignore any errors, no to log them if you
	 * expect a query to fail.
	 * 
	 * @param sQuery
	 *            query to execute, can be a full query or a prepared statement in which case the values to the columns should be passed as well
	 * @param bIgnoreErrors
	 *            <code>true</code> if you want to hide any errors
	 * @param values values to set to the prepared statement
	 * @return true if the query succeeded, false if there was an error
	 */
	public final boolean query(final String sQuery, final boolean bIgnoreErrors, final Object... values) {
		lQueryCount++;

		final String sConnection = getKey();

		AtomicInteger ai = chmQueryCount.get(sConnection);
		AtomicLong al = null;
		if (ai == null) {
			ai = new AtomicInteger(1);
			chmQueryCount.put(sConnection, ai);

			al = new AtomicLong(0);
			chmQueryTime.put(sConnection, al);
		} else {
			ai.incrementAndGet();

			al = chmQueryTime.get(sConnection);
		}

		if (al == null) {
			al = new AtomicLong(0);
			chmQueryTime.put(sConnection, al);
		}
		
		if (this.rsRezultat != null) {
			try {
				this.rsRezultat.close();
			} catch (final Throwable e) {
				// ignore this
			}

			this.rsRezultat = null;
		}

		if (this.stat != null) {
			try {
				this.stat.close();
			} catch (final Throwable e) {
				// ignore this
			}

			this.stat = null;
		}

		this.bIsUpdate = false;
		this.iUpdateCount = -1;
		this.first = false;
		this.lastGeneratedKey = null;
		
		final long lStartTime = System.currentTimeMillis();

		if (!connect()) {
			try {
				throw new SQLException("connection failed"); //$NON-NLS-1$
			} catch (final Exception e) {
				Log.log(Log.ERROR, "lazyj.DBFunctions", sConnection + " --> cannot connect for query because "+getConnectFailReason()+" : \n" + sQuery, e);  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
			}

			al.addAndGet(System.currentTimeMillis() - lStartTime);

			return false;
		}
		
		boolean wasReadOnly = false;
		
		try {
			final boolean execResult;
			
			wasReadOnly = this.dbc.getConnection().isReadOnly();
			
			this.dbc.getConnection().setReadOnly(isReadOnly());
			
			if (values!=null && values.length>0){
				final PreparedStatement prepStat = this.dbc.getConnection().prepareStatement(sQuery, this.generatedKeyRequest);
			
				for (int i=0; i<values.length; i++)
					prepStat.setObject(i+1, values[i]);
				
				this.stat = prepStat;
				
				execResult = prepStat.execute();
			}
			else{
				this.stat = this.dbc.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
				
				execResult = this.stat.execute(sQuery, this.generatedKeyRequest);
			}	

			if (execResult) {
				this.rsRezultat = this.stat.getResultSet();
			}
			else {
				this.bIsUpdate = true;
				this.iUpdateCount = this.stat.getUpdateCount();

				if (this.generatedKeyRequest == Statement.RETURN_GENERATED_KEYS){
					this.rsRezultat = this.stat.getGeneratedKeys();
					
					if (this.rsRezultat!=null && this.rsRezultat.next()){
						this.lastGeneratedKey = gets(1, null);
					}
					
					this.rsRezultat = null;
				}
				
				this.stat.close();
				this.stat = null;
			}

			if (!this.bIsUpdate) {
				this.first = true;
				try {
					if (!this.rsRezultat.next())
						this.first = false;
				} catch (final Exception e) {
					this.first = false;
				}
			} else
				this.first = false;
			
			this.dbc.getConnection().setReadOnly(wasReadOnly);

			this.dbc.free();

			return true;
		} catch (final Exception e) {
			this.rsRezultat = null;
			this.first = false;

			final String s = e.getMessage();

			if (!bIgnoreErrors && s.indexOf("duplicate key") < 0 && s.indexOf("drop table") < 0) {  //$NON-NLS-1$//$NON-NLS-2$
				Log.log(Log.ERROR, "lazyj.DBFunctions", sConnection + " --> Error executing '" + sQuery + "'", e);  //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$
				// in case of an error, close the connection
				this.dbc.close();
			} else {
				try{
					this.dbc.getConnection().setReadOnly(wasReadOnly);
				}
				catch (final SQLException sqle){
					// ignore
				}
				
				// if the error is expected, or not fatal, silently free the connection for later use
				this.dbc.free();
			}

			return false;
		} finally {
			al.addAndGet(System.currentTimeMillis() - lStartTime);
		}
	}
	

	/**
	 * Get the number of rows that were selected by the previous query.
	 * 
	 * @return number of rows, or -1 if the query was not a select one or there was an error
	 */
	public final int count() {
		if (this.bIsUpdate || this.rsRezultat == null)
			return -1;

		try {
			final int pos = this.rsRezultat.getRow();

			final boolean bFirst = this.rsRezultat.isBeforeFirst();
			final boolean bLast = this.rsRezultat.isAfterLast();

			this.rsRezultat.last();

			final int ret = this.rsRezultat.getRow();

			if (bFirst)
				this.rsRezultat.beforeFirst();
			else if (bLast)
				this.rsRezultat.afterLast();
			else if (pos <= 0)
				this.rsRezultat.first();
			else
				this.rsRezultat.absolute(pos);

			return ret;
		} catch (final Throwable t) {
			Log.log(Log.ERROR, "lazyj.DBFunctions", "count()", t); //$NON-NLS-1$ //$NON-NLS-2$
			return -1;
		}
	}
	

	/**
	 * Get the current position in the result set
	 * 
	 * @return current position, -1 if there was an error
	 * @see ResultSet#getRow()
	 */
	public final int getPosition(){
		try{
			return this.rsRezultat.getRow();
		}
		catch (final Throwable t){
			return -1;
		}
	}
	
	
	/**
	 * Jump an arbitrary number of rows.
	 * 
	 * @param count can be positive or negative
	 * @return true if the jump was possible, false if not
	 * @see ResultSet#relative(int)
	 */
	public final boolean relative(final int count){
		try{
			final boolean bResult = this.rsRezultat.relative(count);
			
			if (bResult)
				this.first = false;
			
			return bResult;
		}
		catch (final Throwable t){
			return false;
		}
	}
	
	
	/**
	 * Jump to an absolute position in the result set
	 * 
	 * @param position new position
	 * @return true if the positioning was possible, false otherwise
	 * @see ResultSet#absolute(int)
	 */
	public final boolean absolute(final int position){
		try{
			final boolean bResult = this.rsRezultat.absolute(position);
			
			if (bResult)
				this.first = false;
			
			return bResult;
		}
		catch (final Throwable t){
			return false;
		}
	}
	
	
	/**
	 * Jump to the next row in the result
	 * 
	 * @return true if there is a next entry to jump to, false if not
	 */
	public final boolean moveNext() {
		if (this.bIsUpdate)
			return false;

		if (this.first) {
			this.first = false;
			return true;
		}

		if (this.rsRezultat != null) {
			try {
				if (!this.rsRezultat.next())
					return false;

				return true;
			} catch (final Exception e) {
				return false;
			}
		}

		return false;
	}
	
	/**
	 * Get the contents of a column from the current row based on its name. By default will return "" if there is any problem (column
	 * missing, value is null ...)
	 * 
	 * @param sColumnName column name
	 * @return value, defaulting to ""
	 *  
	 * @see #gets(String, String)
	 * @see #gets(int)
	 * @see #gets(int, String)
	 */
	public final String gets(final String sColumnName) {
		return gets(sColumnName, ""); //$NON-NLS-1$
	}
	
	/**
	 * Get the contents of a column from the current row based on its name. It will return the given default if there is any problem
	 * (column missing, value is null ...)
	 * 
	 * @param sColumnName column name
	 * @param sDefault default value to return if the column doesn't exist or is <code>null</code>
	 * @return value for the column with the same name from the current row
	 * 
	 * @see #gets(String)
	 * @see #gets(int)
	 * @see #gets(int, String)
	 */
	public final String gets(final String sColumnName, final String sDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return sDefault;

		try {
			final String sTemp = this.rsRezultat.getString(sColumnName);
			return (sTemp == null || this.rsRezultat.wasNull()) ? sDefault : sTemp.trim();
		} catch (final Throwable e) {
			return sDefault;
		}
	}

	/**
	 * Get the string value of a column. By default will return "" if there is any problem (column
	 * missing, value is null ...)
	 * 
	 * @param iColumn
	 *            column number (1 = first column of the result set)
	 * @return the value for the column
	 */

	/**
	 * Get the contents of a column from the current row based on its position
	 * 
	 * @param iColumn column count
	 * @return value
	 * 
	 * @see #gets(String)
	 * @see #gets(String, String)
	 * @see #gets(int, String)
	 */
	public final String gets(final int iColumn) {
		return gets(iColumn, ""); //$NON-NLS-1$
	}

	/**
	 * Get the contents of a column from the current row based on its position. It will return the given default if there is any problem
	 * (column missing, value is null ...)
	 * 
	 * @param iColumn position (1 = first column of the result set)
	 * @param sDefault default value to return if the column doesn't exist or is <code>null</code>
	 * @return value in the DB or the default value
	 * 
	 * @see #gets(String)
	 * @see #gets(String, String)
	 * @see #gets(int)
	 */
	public final String gets(final int iColumn, final String sDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return sDefault;
		try {
			final String sTemp = this.rsRezultat.getString(iColumn);
			return sTemp != null ? sTemp : sDefault;
		} catch (final Exception e) {
			return sDefault;
		}
	}
	
	/**
	 * Get the column contents converted to Date. Will return the given default Date if there is a
	 * problem parsing the column.
	 * 
	 * @param sColumnName column name
	 * @return date, never <code>null</code> but maybe the current time if the contents cannot be converted to Date
	 * 
	 * @see Format#parseDate(String)
	 * @see #getDate(String, Date)
	 * @see #getDate(int)
	 * @see #getDate(int, Date)
	 */
	public final Date getDate(final String sColumnName) {
		return getDate(sColumnName, new Date());
	}
	
	/**
	 * Get the column contents converted to Date. Will return the given default Date if there is a
	 * problem parsing the column.
	 * 
	 * @param sColumnName column name
	 * @param dDefault default value to return if the contents in db cannot be parsed to Date
	 * @return date from db, or the default value
	 * 
	 * @see Format#parseDate(String)
	 * @see #getDate(String)
	 * @see #getDate(int)
	 * @see #getDate(int, Date)
	 */
	public final Date getDate(final String sColumnName, final Date dDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return dDefault;

		try {
			final Date d = this.rsRezultat.getTimestamp(sColumnName);

			if (d != null)
				return d;
		} catch (final Exception e) {
			// ignore this
		}

		try {
			final Date d = Format.parseDate(this.rsRezultat.getString(sColumnName).trim());

			if (d != null)
				return d;
		} catch (final Exception e) {
			// ignore this
		}

		return dDefault;
	}

	/**
	 * Get the column contents converted to Date. Will return the current date/time as default if
	 * there is a problem parsing the column.
	 * 
	 * @param iColumn column number ( 1 = first column of the result set )
	 * @return date from db, or the default value
	 * 
	 * @see Format#parseDate(String)
	 * @see #getDate(String)
	 * @see #getDate(String, Date)
	 * @see #getDate(int, Date)
	 */
	public final Date getDate(final int iColumn) {
		return getDate(iColumn, new Date());
	}

	/**
	 * Get the value of a column as a Date object. Will return the given default Date if there is a
	 * problem parsing the column.
	 * 
	 * @param iColumn
	 *            column number ( 1 = first column of the result set )
	 * @param dDefault
	 *            default value to return in case of an error at parsing
	 * @return a Date representation of this column
	 * 
	 * @see Format#parseDate(String)
	 * @see #getDate(String)
	 * @see #getDate(String, Date)
	 * @see #getDate(int)
	 */
	public final Date getDate(final int iColumn, final Date dDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return dDefault;

		try {
			final Date d = this.rsRezultat.getTimestamp(iColumn);

			if (d != null)
				return d;
		} catch (final Exception e) {
			// ignore this
		}

		try {
			final Date d = Format.parseDate(this.rsRezultat.getString(iColumn).trim());

			if (d != null)
				return d;
		} catch (final Exception e) {
			// ignore this
		}

		return dDefault;
	}

	/**
	 * Get the value of this column as int. Will return the current date/time as default if
	 * there is a problem parsing the column.
	 * 
	 * @param sColumnName column
	 * @return value as int, or 0 if there is a problem parsing
	 * @see #geti(String, int)
	 * @see #geti(int)
	 * @see #geti(int, int)
	 */
	public final int geti(final String sColumnName) {
		return geti(sColumnName, 0);
	}

	/**
	 * Get the value of this column as int, returning the default value if the conversion is not possible.
	 * 
	 * @param sColumnName column name
	 * @param iDefault default value to return
	 * @return the value in the db or the given default if there is a problem parsing
	 * @see #geti(String)
	 * @see #geti(int)
	 * @see #geti(int, int)
	 */
	public final int geti(final String sColumnName, final int iDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return iDefault;
		try {
			final int iTemp = this.rsRezultat.getInt(sColumnName);
			return this.rsRezultat.wasNull() ? iDefault : iTemp;
		} catch (final Exception e) {
			return iDefault;
		}
	}
	
	/**
	 * Get the value of this column as int, returning the default value of 0 if the conversion is not possible.
	 * 
	 * @param iColumn column position
	 * @return the value in the db or 0 if there is a problem parsing
	 * @see #geti(String, int)
	 * @see #geti(String)
	 * @see #geti(int, int)
	 */
	public final int geti(final int iColumn) {
		return geti(iColumn, 0);
	}
	

	/**
	 * Get the integer value of a column. Will return the given default value if the column value
	 * cannot be parsed into an integer.
	 * 
	 * @param iColumn
	 *            column number
	 * @param iDefault
	 *            default value to return in case of a parsing error
	 * @return the integer value of this column
	 * @see #geti(String, int)
	 * @see #geti(int)
	 * @see #geti(String)
	 */
	public final int geti(final int iColumn, final int iDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return iDefault;
		try {
			final int iTemp = this.rsRezultat.getInt(iColumn);
			if (this.rsRezultat.wasNull())
				return iDefault;
			return iTemp;
		} catch (final Exception e) {
			return iDefault;
		}
	}

	/**
	 * Get the long value of a column. Will return 0 by default if the column value cannot be parsed
	 * into a long.
	 * 
	 * @param sColumnName
	 *            column name
	 * @return the long value of this column
	 * @see #getl(String, long)
	 * @see #getl(int)
	 * @see #getl(int, long)
	 */
	public final long getl(final String sColumnName) {
		return getl(sColumnName, 0);
	}

	/**
	 * Get the long value of a column. Will return the given default value if the column value
	 * cannot be parsed into a long.
	 * 
	 * @param sColumnName
	 *            column name
	 * @param lDefault
	 *            default value to return in case of a parsing error
	 * @return the long value of this column
	 * @see #getl(String)
	 * @see #getl(int)
	 * @see #getl(int, long)
	 */
	public final long getl(final String sColumnName, final long lDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return lDefault;
		try {
			final long lTemp = this.rsRezultat.getLong(sColumnName);
			return this.rsRezultat.wasNull() ? lDefault : lTemp;
		} catch (final Throwable e) {
			return lDefault;
		}
	}

	/**
	 * Get the long value of a column. Will return 0 by default if the column value cannot be parsed
	 * into a long.
	 * 
	 * @param iColCount
	 *            column count
	 * @return the long value of this column
	 * @see #getl(String, long)
	 * @see #getl(String)
	 * @see #getl(int, long)
	 */
	public final long getl(final int iColCount) {
		return getl(iColCount, 0);
	}

	/**
	 * Get the long value of a column. Will return the given default value if the column value
	 * cannot be parsed into a long.
	 * 
	 * @param iColCount
	 *            column count
	 * @param lDefault
	 *            default value to return in case of a parsing error
	 * @return the long value of this column
	 * @see #getl(String, long)
	 * @see #getl(int)
	 * @see #getl(String)
	 */
	public final long getl(final int iColCount, final long lDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return lDefault;
		try {
			final long lTemp = this.rsRezultat.getLong(iColCount);
			return this.rsRezultat.wasNull() ? lDefault : lTemp;
		} catch (final Throwable e) {
			return lDefault;
		}
	}
	
	/**
	 * Get the float value of a column. Will return 0 by default if the column value cannot be
	 * parsed into a float.
	 * 
	 * @param sColumnName
	 *            column name
	 * @return the float value of this column
	 */
	public final float getf(final String sColumnName) {
		return getf(sColumnName, 0);
	}

	/**
	 * Get the float value of a column. Will return the given default value if the column value
	 * cannot be parsed into a float.
	 * 
	 * @param sColumnName
	 *            column name
	 * @param fDefault
	 *            default value to return in case of a parsing error
	 * @return the float value of this column
	 */
	public final float getf(final String sColumnName, final float fDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return fDefault;
		try {
			final float fTemp = this.rsRezultat.getFloat(sColumnName);
			return this.rsRezultat.wasNull() ? fDefault : fTemp;
		} catch (final Exception e) {
			return fDefault;
		}
	}

	
	/**
	 * Get the float value of a column. Will return 0 by default if the column value cannot be
	 * parsed into a float.
	 * 
	 * @param iColumn 
	 *            column position
	 * @return the float value of this column
	 */
	public final float getf(final int iColumn) {
		return getf(iColumn, 0);
	}

	/**
	 * Get the float value of a column. Will return the given default value if the column value
	 * cannot be parsed into a float.
	 * 
	 * @param iColumn
	 *            column position
	 * @param fDefault
	 *            default value to return in case of a parsing error
	 * @return the float value of this column
	 */
	public final float getf(final int iColumn, final float fDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return fDefault;
		try {
			final float fTemp = this.rsRezultat.getFloat(iColumn);
			return this.rsRezultat.wasNull() ? fDefault : fTemp;
		} catch (final Exception e) {
			return fDefault;
		}
	}
	
	/**
	 * Get the double value of a column. Will return 0 by default if the column value cannot be
	 * parsed into a double.
	 * 
	 * @param sColumnName
	 *            column name
	 * @return the double value of this column
	 */
	public final double getd(final String sColumnName) {
		return getd(sColumnName, 0);
	}

	/**
	 * Get the double value of a column. Will return 0 by default if the column value cannot be
	 * parsed into a double.
	 * 
	 * @param sColumnName
	 *            column name
	 * @param dDefault
	 *            default value to return in case of a parsing error
	 * @return the double value of this column
	 */
	public final double getd(final String sColumnName, final double dDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return dDefault;
		try {
			final double dTemp = this.rsRezultat.getDouble(sColumnName);
			return this.rsRezultat.wasNull() ? dDefault : dTemp;
		} catch (final Throwable e) {
			return dDefault;
		}
	}
	
	/**
	 * Get the double value of a column. Will return 0 by default if the column value cannot be
	 * parsed into a double.
	 * 
	 * @param iColumn
	 *            column position
	 * @return the double value of this column
	 */
	public final double getd(final int iColumn) {
		return getd(iColumn, 0);
	}

	/**
	 * Get the double value of a column. Will return 0 by default if the column value cannot be
	 * parsed into a double.
	 * 
	 * @param iColumn
	 *            column position
	 * @param dDefault
	 *            default value to return in case of a parsing error
	 * @return the double value of this column
	 */
	public final double getd(final int iColumn, final double dDefault) {
		if ((this.dbc == null) || this.rsRezultat == null)
			return dDefault;
		try {
			final double dTemp = this.rsRezultat.getDouble(iColumn);
			return this.rsRezultat.wasNull() ? dDefault : dTemp;
		} catch (final Throwable e) {
			return dDefault;
		}
	}
	
	/**
	 * Get the boolean value of a column
	 * 
	 * @param sColumn column name
	 * @param bDefault default value
	 * @return true/false, obviously :)
	 * @see Utils#stringToBool(String, boolean)
	 * @see #getb(int, boolean)
	 */
	public final boolean getb(final String sColumn, final boolean bDefault){
		return Utils.stringToBool(gets(sColumn), bDefault);
	}

	/**
	 * Get the boolean value of a column
	 * 
	 * @param iColumn column index
	 * @param bDefault default value
	 * @return true/false, obviously :)
	 * @see Utils#stringToBool(String, boolean)
	 * @see #getb(String, boolean)
	 */
	public final boolean getb(final int iColumn, final boolean bDefault){
		return Utils.stringToBool(gets(iColumn), bDefault);	}
	
	/**
	 * Get the raw bytes of this column
	 * 
	 * @param iColumn
	 * @return the bytes of this column
	 */
	public final byte[] getBytes(final int iColumn){
		if ((this.dbc == null) || this.rsRezultat == null)
			return null;
		
		try{
			return this.rsRezultat.getBytes(iColumn);
		}
		catch (final Throwable e){
			// ignore
		}
		
		return null;
	}
	
	/**
	 * Get the raw bytes of this column
	 * 
	 * @param columnName
	 * @return the bytes of this column
	 */
	public final byte[] getBytes(final String columnName){
		if ((this.dbc == null) || this.rsRezultat == null)
			return null;
		
		try{
			return this.rsRezultat.getBytes(columnName);
		}
		catch (final Throwable e){
			// ignore
		}
		
		return null;
	}
	
	/**
	 * Extract a PostgreSQL array into a Collection of StriG96Lng objects
	 * 
	 * @param sColumn column name
	 * @return the values in the array, as Strings
	 * @since 1.0.3
	 */
	public final List<String> getStringArray(final String sColumn){
		return decode(gets(sColumn));
	}
	
	/**
	 * Extract a PostgreSQL array into a Collection of String objects
	 * 
	 * @param iColumn column index
	 * @return the values in the array, as Strings
	 * @since 1.0.3
	 */
	public final List<String> getStringArray(final int iColumn){
		return decode(gets(iColumn));
	}
	
	/**
	 * Extract a PostgreSQL array into a Collection of Integer objects
	 * 
	 * @param sColumn column name
	 * @return the values in the array, as Integers
	 * @since 1.0.3
	 */
	public final List<Integer> getIntArray(final String sColumn){
		return decodeToInt(gets(sColumn));
	}
	
	/**
	 * Extract a PostgreSQL array into a Collection of Integer objects
	 * 
	 * @param iColumn column index
	 * @return the values in the array, as Integers
	 * @since 1.0.3
	 */
	public final List<Integer> getIntArray(final int iColumn){
		return decodeToInt(gets(iColumn));
	}	
	
	/**
	 * Convert each entry from an array to Integer.
	 * 
	 * @param sValue
	 * @return the members of the database array, as list of Integer objects
	 * @since 1.0.3
	 */
	public static List<Integer> decodeToInt(final String sValue){
		final List<String> lValues = decode(sValue);
		
		final ArrayList<Integer> l = new ArrayList<>(lValues.size());
		
		for (final String s: lValues){
			try{
				l.add(Integer.valueOf(s));
			}
			catch (final NumberFormatException nfe){
				// ignore
			}
		}
		
		return l;
	}
	
	/**
	 * Given an array in PostgreSQL format, convert it to a Java array of Strings.
	 * 
	 * @param sValue
	 * @return the members of the database array, as list of String objects
	 * @since 1.0.3
	 */
	public static List<String> decode(final String sValue){
		if (sValue==null || sValue.length()<2 || sValue.charAt(0)!='{' || sValue.charAt(sValue.length()-1)!='}')
			return Collections.emptyList();
		
		final StringTokenizer st = new StringTokenizer(sValue.substring(1, sValue.length()-1), ","); //$NON-NLS-1$
		
		final ArrayList<String> l = new ArrayList<>(st.countTokens());
		
		while (st.hasMoreTokens()){
			String s = st.nextToken();
			
			if (s.charAt(0)=='"'){
				while ((s.length()<2 || s.charAt(s.length()-1)!='"' || s.charAt(s.length()-2)=='\\') && st.hasMoreTokens()){
					s += ',' + st.nextToken();
				}
				
				s = s.substring(1, s.length()-1).replace("\\\"", "\"").replace("\\\\", "\\");   //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$
			}
			
			l.add(s);
		}
		
		return l;
	}
	
	/**
	 * Generate a PostgreSQL array representation of the given one-dimensional collection.
	 * For details consult the <a href="http://www.postgresql.org/docs/8.2/static/arrays.html">documentation</a>.
	 * 
	 * @param array
	 * @return a string encoding of the values
	 * @since 1.0.3
	 */
	public static String encodeArray(final Collection<?> array){
		final StringBuilder sb = new StringBuilder();
		
		for (final Object o: array){
			String s = o.toString();
			s = Format.replace(s, "\"", "\\\"");  //$NON-NLS-1$//$NON-NLS-2$
			s = Format.escJS(s);
			
			if (sb.length()>0)
				sb.append(',');
			
			sb.append('"').append(s).append('"');
		}
		
		return "'{"+sb.toString()+"}'"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Get the meta information for the current query. You can look at this structure to extract
	 * column names, types and so on.
	 * 
	 * @return the meta information for the current query.
	 */
	public final ResultSetMetaData getMetaData() {
		if ((this.dbc == null) || this.rsRezultat == null)
			return null;

		try {
			return this.rsRezultat.getMetaData();
		} catch (final Exception e) {
			// ignore this
		}

		return null;
	}
	
	/**
	 * A shortcut to find out the column names for this query
	 * 
	 * @return an array of column names
	 */
	public final String[] getColumnNames() {
		final ResultSetMetaData rsmd = getMetaData();

		if (rsmd == null)
			return new String[0];

		try {
			final int count = rsmd.getColumnCount();

			final String vs[] = new String[count];

			for (int i = 1; i <= count; i++)
				vs[i-1] = rsmd.getColumnName(i);

			return vs;
		} catch (final Throwable e) {
			return new String[0];
		}
	}

	/**
	 * Statistics : the total number of opened connection to the databases until now.
	 * 
	 * @return total number of opened connections.
	 */
	public static final long getOpenedConnectionsCount() {
		return lOpened;
	}

	/**
	 * Statistics : the total number of closed connection to the databases until now.
	 * 
	 * @return total number of closed connections.
	 */
	public static final long getClosedConnectionsCount() {
		return lClosed;
	}

	/**
	 * Statistics : the total number of closed connection to the databases executed when the object
	 * is deallocated.
	 * 
	 * @return total number of closed connections on object deallocation.
	 */
	public static final long getClosedOnFinalizeConnectionsCount() {
		return lClosedOnFinalize;
	}

	/**
	 * Statistics : get the total number of executed queries.
	 * 
	 * @return number of executed queries.
	 */
	public static final long getQueryCount() {
		return lQueryCount;
	}

	/**
	 * Statistics : get the number of connections currently established
	 * 
	 * @return the number of active connections
	 */
	public static final long getActiveConnectionsCount() {
		long lCount = 0;

		synchronized (hmConn) {
			for (final LinkedList<DBConnection> ll : hmConn.values())
				lCount += ll.size();
		}

		return lCount;
	}

	/**
	 * Statistics : get the number of connections per each unique key
	 * 
	 * @return a map of key - number of active connections
	 */
	public static final HashMap<String, Integer> getActiveConnections() {
		final HashMap<String, Integer> hm = new HashMap<>();

		synchronized (hmConn) {
			for (final Entry<String, LinkedList<DBConnection>> me : hmConn.entrySet())
				hm.put(me.getKey(), Integer.valueOf(me.getValue().size()));
		}

		return hm;
	}
	
	/**
	 * Get the SQL INSERT statement that would generate the current row with all the columns (their aliases more precisely).
	 * 
	 * @param sTable table name
	 * @return the INSERT statement, or <code>null</code> if any problem
	 */
	public final String getEquivalentInsert(final String sTable){
		if ((this.dbc == null) || this.rsRezultat == null)
			return null;
		
		return getEquivalentInsert(sTable, getColumnNames());
	}
	
	/**
	 * Get the SQL INSERT statement that would generate the current row, for the given list of columns
	 * 
	 * @param sTable table name
	 * @param columns what column names are to be taken into account
	 * @return the INSERT statement, or <code>null</code> if there was any problem
	 */
	public final String getEquivalentInsert(final String sTable, final String[] columns){
		return getEquivalentInsert(sTable, columns, null);
	}
	
	/**
	 * Convert the result set in a column name -> value mapping
	 * 
	 * @return the column name -> value mapping
	 */
	public final Map<String, Object> getValuesMap(){
		final ResultSetMetaData meta = getMetaData();
		
		if (meta==null)
			return null;
		
		try{
			final int count = meta.getColumnCount();
			
			final Map<String, Object> ret = new HashMap<>(count);
			
			for (int i = 1; i <= count; i++){
				final String columnName = meta.getColumnName(i);
				
				ret.put(columnName, this.rsRezultat.getObject(i));
			}
			
			return ret;
		}
		catch (final SQLException e){
			return null;
		}
	}
	
	/**
	 * Get the SQL INSERT statement that would generate the current row, for the given list of columns
	 * 
	 * @param sTable table name
	 * @param columns what column names are to be taken into account. Non-existing column names are ignored.
	 * @param overrides value overrides. Column names that don't exist in the columns selection are appended to the output.
	 * @return the INSERT statement, or <code>null</code> if there was any problem
	 */
	@SuppressWarnings("nls")
	public final String getEquivalentInsert(final String sTable, final String[] columns, final Map<String, ?> overrides){
		final ResultSetMetaData meta = getMetaData();
		
		if (meta==null)
			return null;
		
		final StringBuilder sb = new StringBuilder("INSERT INTO ");
		
		sb.append(sTable).append(" (");

		final StringBuilder sbValues = new StringBuilder(" VALUES (");
		
		final List<String> columnNames = Arrays.asList(getColumnNames());

		boolean bFirst = true;
		
		for (final String column : columns) {
			final int idx = columnNames.indexOf(column);
			
			if (idx<0)
				continue;
			
			if (!bFirst){
				sb.append(',');
				sbValues.append(',');
			}
			else
				bFirst = false;
			
			sb.append(Format.escSQL(column));
			
			final String sValue;
			
			if (overrides!=null && overrides.containsKey(column)){
				final Object o = overrides.get(column);
				
				sValue = o!=null ? o.toString() : null;	 
			}
			else
				sValue = gets(idx+1, null);

			if (sValue==null){
				sbValues.append("null");
				continue;
			}
			
			final int iType;
			
			try{
				 iType = meta.getColumnType(idx+1);
			}
			catch (final SQLException sqle){
				return null;
			}
			
			if (
					iType == Types.CHAR || iType == Types.NCHAR || iType == Types.VARCHAR || iType == Types.NVARCHAR || iType == Types.LONGVARCHAR || iType == Types.LONGNVARCHAR ||
					iType == Types.DATE || iType == Types.TIME || iType == Types.TIMESTAMP ||
					iType == Types.BLOB || iType == Types.CLOB || iType == Types.NCLOB || 
					iType == Types.BINARY || iType == Types.VARBINARY || iType == Types.LONGVARBINARY ||
					iType == Types.JAVA_OBJECT ||iType == Types.SQLXML
			)
			{
				sbValues.append('\'').append(Format.escSQL(sValue)).append('\''); 
			}
			else{
				sbValues.append(Format.escSQL(sValue));
			}
		}
		
		if (overrides!=null){
			for (final Map.Entry<String, ?> me: overrides.entrySet()){
				if (columnNames.contains(me.getKey()))
					continue;
								
				if (!bFirst){
					sb.append(',');
					sbValues.append(',');
				}
				else
					bFirst = false;
				
				sb.append(Format.escSQL(me.getKey()));
				
				final Object o = me.getValue();
				
				if (o==null){
					sbValues.append("null");
				}
				else{
					final String s = o.toString();
					
					if (!(o instanceof Number))
						sbValues.append('\'').append(Format.escSQL(s)).append('\'');
					else
						sbValues.append(Format.escSQL(s));
				}
			}
		}
		
		sb.append(')').append(sbValues).append(");"); //$NON-NLS-1$
		
		return sb.toString();
	}
	
	/**
	 * SQL date format
	 */
	private static final DateFormat SQL_DATE = new SimpleDateFormat("yyyy-MM-DD HH:mm:ss.SSS"); //$NON-NLS-1$
	
	/**
	 * Get the value formatted for SQL statements
	 * 
	 * @param o value to format
	 * @return formatted string, depending on the object type
	 */
	@SuppressWarnings("nls")
	private static String getFormattedValue(final Object o){
		if (o==null){
			return "null";
		}
	
		if (o instanceof String || o instanceof StringBuilder || o instanceof StringBuffer){
			return "'"+Format.escSQL((String) o)+"'";
		}
		
		if (o instanceof Number){
			return o.toString();
		}
		
		if (o instanceof Date){
			synchronized (SQL_DATE){
				return "'"+Format.escSQL(SQL_DATE.format((Date) o))+"'";
			}
		}
		
		return "'"+Format.escSQL(o.toString())+"'";
	}
	
	/**
	 * Create an INSERT statement for these values
	 * 
	 * @param tableName table name
	 * @param values column - value mapping
	 * @return the SQL statement, or <code>null</code> if there was any problem
	 */
	@SuppressWarnings("nls")
	public static String composeInsert(final String tableName, final Map<String, ?> values){
		if (tableName==null || values==null)
			return null;
		
		final StringBuilder sb = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
		
		final StringBuilder sbValues = new StringBuilder(") VALUES (");
		
		boolean bFirst = true;
		
		for (final Map.Entry<String, ?> me: values.entrySet()){
			final String sKey = me.getKey();
			
			if (sKey==null || sKey.length()==0)
				continue;
		
			if (bFirst){
				bFirst = false;
			}
			else{
				sb.append(',');
				sbValues.append(',');
			}
			
			sb.append(Format.escSQL(sKey));
			
			sbValues.append(getFormattedValue(me.getValue()));
		}
		
		sb.append(sbValues).append(")");
		
		return sb.toString();
	}
	
	/**
	 * Compose an UPDATE SQL statement 
	 * 
	 * @param tableName table name
	 * @param values column - value mapping
	 * @param primaryKeys the set of primary keys from the values map
	 * @return the UPDATE statement
	 */
	@SuppressWarnings("nls")
	public static String composeUpdate(final String tableName, final Map<String, ?> values, final Collection<String> primaryKeys){
		if (tableName==null || values==null)
			return null;
		
		final StringBuilder sb = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
		
		final StringBuilder sbWhere = new StringBuilder();
		
		boolean bFirst = true;
		
		for (final Map.Entry<String, ?> me: values.entrySet()){
			final String sKey = me.getKey();
			
			if (sKey==null || sKey.length()==0)
				continue;
			
			if (primaryKeys!=null && primaryKeys.contains(sKey)){
				if (sbWhere.length()==0)
					sbWhere.append(" WHERE ");
				else
					sbWhere.append(" AND ");
				
				sbWhere.append(Format.escSQL(sKey));
				
				final Object value = me.getValue();
				
				if (value==null)
					sbWhere.append(" IS NULL");
				else
					sbWhere.append('=').append(getFormattedValue(value));
				
				continue;
			}
			
			if (bFirst)
				bFirst = false;
			else
				sb.append(',');
		
			sb.append(Format.escSQL(sKey)).append('=').append(getFormattedValue(me.getValue()));
		}
		
		if (sbWhere.length()>0)
			sb.append(sbWhere);
		
		return sb.toString();
	}
}
