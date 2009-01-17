package lazyj;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Map.Entry;
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
	static final HashMap<String, LinkedList<DBConnection>>			hmConn				= new HashMap<String, LinkedList<DBConnection>>();

	/**
	 * Synchronization object for sensitive parts
	 */
	static final Object												oConnLock			= new Object();

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
	public static final ConcurrentHashMap<String, AtomicInteger>	chmQueryCount		= new ConcurrentHashMap<String, AtomicInteger>();

	/**
	 * For statistics: total time to execute the queries on each of the connections.
	 */
	public static final ConcurrentHashMap<String, AtomicLong>		chmQueryTime		= new ConcurrentHashMap<String, AtomicLong>();

	/**
	 * Configuration options
	 */
	final ExtProperties												prop;
	
	/**
	 * Create a connection to the database using the parameters in this properties file. The
	 * following keys are extracted:<br>
	 * <ul>
	 * <li><b>driver</b> : (required) one of org.postgresql.Driver, com.mysql.jdbc.Driver or com.microsoft.jdbc.sqlserver.SQLServerDriver</li>
	 * <li><b>host</b> : (optional) server's ip address, defaults to 127.0.0.1</li>
	 * <li><b>port</b> : (optional) tcp port to connect to on the <i>host</i>, if it is missing the default port for each database type is used</li>
	 * <li><b>database</b> : (required) name of the database to connect to</li>
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
	 * <li><a href="http://jdbc.postgresql.org/documentation/83/connect.html" target=_blank>PostgreSQL</a>:
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
		this.prop = configProperties;
		this.dbc = null;
		this.first = false;
		this.bIsUpdate = true;
		this.rsRezultat = null;
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
		this(configProperties);

		query(sQuery);
	}

	/**
	 * From the current connections try to find out if there is any one of them that is free
	 * 
	 * @param sConn
	 * @return a free connection, or null
	 */
	private final DBConnection getFreeConnection(final String sConn) {
		synchronized (oConnLock) {
			LinkedList<DBConnection> ll = hmConn.get(sConn);

			if (ll != null) {
				for (DBConnection dbt : ll) {
					if (dbt.canUse()) {
						dbt.use();
						return dbt;
					}
				}
			} else {
				ll = new LinkedList<DBConnection>();
				hmConn.put(sConn, ll);
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
		return this.prop.gets("driver") + "/" + this.prop.gets("host", "127.0.0.1") + "/" + this.prop.gets("port") + "/" + this.prop.gets("database") + "/" + this.prop.gets("user") + "/" + this.prop.gets("password");
	}

	/**
	 * Check if this connection is done to a PostgreSQL database (if we are using the PG JDBC driver)
	 * 
	 * @return true if the connection is done to a PostgreSQL database
	 */
	public boolean isPostgreSQL(){
		return this.prop!=null && this.prop.gets("driver").toLowerCase(Locale.getDefault()).indexOf("postgres")>=0;
	}
	
	/**
	 * Check if this connection is done to a MySQL database (if we are using the MySQL JDBC driver)
	 * 
	 * @return true if the connection is done to a MySQL database
	 */
	public boolean isMySQL(){
		return this.prop!=null && this.prop.gets("driver").toLowerCase(Locale.getDefault()).indexOf("mysql")>=0;
	}
	
	/**
	 * Initialize a database connection. First it will try to take a free one from the pool. If there is no free connection it will
	 * try to establish a new one, only if there are less than 50 connections to this particular database in total. 
	 * 
	 * @return <code>true</code> if the connection was established and <code>this.dbc</code> can be used, <code>false</code> if not.
	 */
	private final boolean connect() {
		final String sConn = getKey();

		for (int i = 0; i < 3; i++) {
			this.dbc = getFreeConnection(sConn);

			if (this.dbc != null)
				return true;

			synchronized (oConnLock) {
				final LinkedList<DBConnection> ll = hmConn.get(sConn);

				if (ll.size() < 50) {
					this.dbc = new DBConnection(this.prop, sConn);
					if (this.dbc.canUse()) {
						this.dbc.use();
						ll.add(this.dbc);
						return true;
					}
					this.dbc.close();
					this.dbc = null;
				}
			}

			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
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
		System.setProperty("PGDATESTYLE", "ISO");
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
	 * DBFunctions db = new DBFunctions(prop);
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
		 * Establish a new connection. Cannot be called directly, you have to use {@link DBFunctions#getConnection()} for example.
		 * 
		 * @param prop    connection properties
		 * @param _sConn  connection key
		 */
		DBConnection(final ExtProperties prop, final String _sConn) {
			this.conn = null;
			this.iBusy = 0;

			lOpened++;

			this.sConn = _sConn;

			final String driver = prop.gets("driver");

			try {
				Class.forName(driver);
			} catch (Exception e) {
				System.err.println("Cannot find driver '" + driver + "' : " + e + " (" + e.getMessage() + ")");
				this.iBusy = 3;
				return;
			}

			/*
			 * See here for JDBC URL examples:
			 * http://www.petefreitag.com/articles/jdbc_urls/
			 */
			
			try {
				final StringBuilder connection = new StringBuilder("jdbc:");

				final boolean isMySQL = driver.indexOf("mysql") >= 0;
				final boolean isPostgreSQL = driver.indexOf("postgres") >= 0;
				final boolean isMSSQL = driver.indexOf("sqlserver") >= 0; 
				
				if (isMySQL)
					connection.append("mysql:");
				else if (isPostgreSQL)
					connection.append("postgresql:");
				else if (isMSSQL)
					connection.append("microsoft:sqlserver:");
				else {
					// UNKNOWN DRIVER
					this.iBusy = 3;
					return;
				}

				connection.append("//").append(prop.gets("host", "127.0.0.1"));

				final String sPort = prop.gets("port"); 
				
				if (sPort.length() > 0)
					connection.append(':').append(sPort);

				if (isMySQL || isPostgreSQL)
					connection.append('/').append(prop.gets("database"));
				else
				if (isMSSQL)
					connection.append(";databaseName=").append(prop.gets("database"));

				this.conn = DriverManager.getConnection(connection.toString(), prop.getProperties());
				this.iBusy = 1;
			} catch (SQLException e) {
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
			return this.iBusy == 1;
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
				} catch (Exception e) {
					System.err.println("DBConnection: cannot close " + this.sConn + " because : " + e + " (" + e.getMessage() + ")");
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
				} catch (Exception e) {
					System.err.println("DBConnection: cannot close " + this.sConn + " on finalize because : " + e + " (" + e.getMessage() + ")");
				}
			}
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
			super("lazyj.DBFunctions: cleanup thread");
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
			int iRunCount = 0;
			long now;
			LinkedList<DBConnection> ll;
			int iIdleCount;
			Iterator<DBConnection> it;
			DBConnection dbc;
			boolean bIdle;
			boolean bClose;
			int iTotalCount;
			int iUnclosed = 0;
			int iClosedToGC = 0;

			while (!this.bShouldStop) {
				now = System.currentTimeMillis();

				iTotalCount = 0;

				synchronized (oConnLock) {
					for (Entry<String, LinkedList<DBConnection>> me : hmConn.entrySet()) {
						ll = me.getValue();

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
									System.err.println("DB2: Not closing busy connection");
									iUnclosed++;
								}

								it.remove();
								if (bIdle) // if it was idle but i decided to remove it
									iIdleCount--;
							}
						}

						iTotalCount += ll.size();
					}

					iRunCount++;
				}

				// when we remove connection make sure the resources are really freed by JVM 
				if (iClosedToGC > 20) {
					iClosedToGC = 0;
					System.gc();
					Thread.yield();
					System.gc();
					Thread.yield();
				}

				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
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
			} catch (Throwable e) {
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
	 * Override the default destructor to properly close any resources in use.
	 */

	@Override
	protected void finalize() {
		if (this.rsRezultat != null) {
			try {
				this.rsRezultat.close();
			} catch (Throwable t) {
				// ignore this
			}
		}

		if (this.stat != null) {
			try {
				this.stat.close();
			} catch (Throwable t) {
				// ignore this
			}
		}
	}

	
	/**
	 * Execute a query.
	 * 
	 * @param sQuery SQL query to execute
	 * @return <code>true</code> if the query succeeded, <code>false</code> if there was an error (connection or syntax).
	 * @see DBFunctions#query(String, boolean)
	 */
	public boolean query(final String sQuery) {
		return query(sQuery, false);
	}

	/**
	 * Execute an error and as an option you can force to ignore any errors, no to log them if you
	 * expect a query to fail.
	 * 
	 * @param sQuery
	 *            query to execute
	 * @param bIgnoreErrors
	 *            true if you want to hide any errors
	 * @return true if the query succeeded, false if there was an error
	 */
	public final boolean query(final String sQuery, final boolean bIgnoreErrors) {
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
			} catch (Throwable e) {
				// ignore this
			}

			this.rsRezultat = null;
		}

		if (this.stat != null) {
			try {
				this.stat.close();
			} catch (Throwable e) {
				// ignore this
			}

			this.stat = null;
		}

		this.bIsUpdate = false;
		this.iUpdateCount = -1;
		this.first = false;
		
		final long lStartTime = System.currentTimeMillis();

		if (!connect()) {
			try {
				throw new SQLException("connection failed");
			} catch (Exception e) {
				Log.log(Log.ERROR, "lazyj.DBFunctions", sConnection + " --> cannot connect for query : \n" + sQuery, e);
			}

			al.addAndGet(System.currentTimeMillis() - lStartTime);

			return false;
		}
		
		try {
			this.stat = this.dbc.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			if (this.stat.execute(sQuery, Statement.NO_GENERATED_KEYS)) {
				this.rsRezultat = this.stat.getResultSet();
			}
			else {
				this.bIsUpdate = true;
				this.iUpdateCount = this.stat.getUpdateCount();

				this.stat.close();
				this.stat = null;
			}

			if (!this.bIsUpdate) {
				this.first = true;
				try {
					if (!this.rsRezultat.next())
						this.first = false;
				} catch (Exception e) {
					this.first = false;
				}
			} else
				this.first = false;

			this.dbc.free();

			return true;
		} catch (Exception e) {
			this.rsRezultat = null;
			this.first = false;

			final String s = e.getMessage();

			if (!bIgnoreErrors && s.indexOf("duplicate key") < 0 && s.indexOf("drop table") < 0) {
				Log.log(Log.ERROR, "lazyj.DBFunctions", sConnection + " --> Error executing '" + sQuery + "'", e);
				// in case of an error, close the connection
				this.dbc.close();
			} else {
				// if the error is expected, or not fatal, silently free the connection for later
				// use
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
		} catch (Throwable t) {
			Log.log(Log.ERROR, "lazyj.DBFunctions", "count()", t);
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
		catch (Throwable t){
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
		catch (Throwable t){
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
		catch (Throwable t){
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
			} catch (Exception e) {
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
		return gets(sColumnName, "");
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
			String sTemp = this.rsRezultat.getString(sColumnName);
			return (sTemp == null || this.rsRezultat.wasNull()) ? sDefault : sTemp.trim();
		} catch (Throwable e) {
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
		return gets(iColumn, "");
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
		} catch (Exception e) {
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
			final Date d = this.rsRezultat.getDate(sColumnName);

			if (d != null)
				return d;
		} catch (Exception e) {
			// ignore this
		}

		try {
			final Date d = Format.parseDate(this.rsRezultat.getString(sColumnName).trim());

			if (d != null)
				return d;
		} catch (Exception e) {
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
			final Date d = this.rsRezultat.getDate(iColumn);

			if (d != null)
				return d;
		} catch (Exception e) {
			// ignore this
		}

		try {
			final Date d = Format.parseDate(this.rsRezultat.getString(iColumn).trim());

			if (d != null)
				return d;
		} catch (Exception e) {
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
		} catch (Exception e) {
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
		} catch (Exception e) {
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
		} catch (Throwable e) {
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
		} catch (Throwable e) {
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
		} catch (Exception e) {
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
		} catch (Exception e) {
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
		} catch (Throwable e) {
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
		} catch (Throwable e) {
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
	 * Extract a PostgreSQL array into a Collection of String objects
	 * 
	 * @param sColumn column name
	 * @return the values in the array, as Strings
	 * @since 1.0.3
	 */
	public final Collection<String> getStringArray(final String sColumn){
		return decode(gets(sColumn));
	}
	
	/**
	 * Extract a PostgreSQL array into a Collection of String objects
	 * 
	 * @param iColumn column index
	 * @return the values in the array, as Strings
	 * @since 1.0.3
	 */
	public final Collection<String> getStringArray(final int iColumn){
		return decode(gets(iColumn));
	}
	
	/**
	 * Extract a PostgreSQL array into a Collection of Integer objects
	 * 
	 * @param sColumn column name
	 * @return the values in the array, as Integers
	 * @since 1.0.3
	 */
	public final Collection<Integer> getIntArray(final String sColumn){
		return decodeToInt(gets(sColumn));
	}
	
	/**
	 * Extract a PostgreSQL array into a Collection of Integer objects
	 * 
	 * @param iColumn column index
	 * @return the values in the array, as Integers
	 * @since 1.0.3
	 */
	public final Collection<Integer> getIntArray(final int iColumn){
		return decodeToInt(gets(iColumn));
	}	
	
	/**
	 * Convert each entry from an array to Integer.
	 * 
	 * @param sValue
	 * @return collection of integers
	 * @since 1.0.3
	 */
	private static Collection<Integer> decodeToInt(final String sValue){
		final Collection<String> lValues = decode(sValue);
		
		final ArrayList<Integer> l = new ArrayList<Integer>(lValues.size());
		
		for (String s: lValues){
			try{
				l.add(Integer.valueOf(s));
			}
			catch (NumberFormatException nfe){
				// ignore
			}
		}
		
		return l;
	}
	
	/**
	 * Given an array in PostgreSQL format, convert it to a Java array of Strings.
	 * 
	 * @param sValue
	 * @return collection of strings
	 * @since 1.0.3
	 */
	private static Collection<String> decode(final String sValue){
		if (sValue==null || sValue.length()<2 || sValue.charAt(0)!='{' || sValue.charAt(sValue.length()-1)!='}')
			return new ArrayList<String>(0);
		
		final StringTokenizer st = new StringTokenizer(sValue.substring(1, sValue.length()-1), ",");
		
		final ArrayList<String> l = new ArrayList<String>(st.countTokens());
		
		while (st.hasMoreTokens()){
			String s = st.nextToken();
			
			if (s.charAt(0)=='"'){
				while ((s.length()<2 || s.charAt(s.length()-1)!='"' || s.charAt(s.length()-2)=='\\') && st.hasMoreTokens()){
					s += "," + st.nextToken();
				}
				
				s = s.substring(1, s.length()-1).replace("\\\"", "\"").replace("\\\\", "\\");
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
		
		for (Object o: array){
			String s = o.toString();
			s = Format.replace(s, "\"", "\\\"");
			s = Format.escJS(s);
			
			if (sb.length()>0)
				sb.append(',');
			
			sb.append('"').append(s).append('"');
		}
		
		return "'{"+sb.toString()+"}'";
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
		} catch (Exception e) {
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
				vs[i] = rsmd.getColumnName(i);

			return vs;
		} catch (Throwable e) {
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

		synchronized (oConnLock) {
			for (LinkedList<DBConnection> ll : hmConn.values())
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
		final HashMap<String, Integer> hm = new HashMap<String, Integer>();

		synchronized (oConnLock) {
			for (Entry<String, LinkedList<DBConnection>> me : hmConn.entrySet())
				hm.put(me.getKey(), Integer.valueOf(me.getValue().size()));
		}

		return hm;
	}
}
