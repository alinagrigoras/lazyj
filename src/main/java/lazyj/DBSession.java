package lazyj;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Web session wrapper, with a database backend.<br>
 * <br>
 * This class will take the database settings from the file "dbsessions.properties" that is found in the
 * folder specified by the system property "lazyj.config.folder". So make sure you have given the parameter
 * "-Dlazyj.config.folder=/path/to/the/folder" to your java application. Or, as an alternative, you can set
 * this parameter to a meaningful value at the very begining of your application, before it accesses any class
 * from this framework.<br>
 * <br>
 * Configuration parameters:<br><ul>
 * <li>database.enabled=boolean (default false). If true you also have to include here the options, in {@link DBFunctions} format.</li>
 * <li>flush.memory=int (default 5). Time in minutes after which an idle session is flushed to DB, if DB backing is enabled.</li>
 * <li>flush.memory.nodb=int (default 30). Time in minues after which an idle session is completely removed from memory, if DB backing is disabled.</li>
 * <li>flush.db=int (default 120). Time in minutes after which an idle session is completely removed from DB.</li>
 * </ul>
 *  
 * @author costing
 * @since 2006-10-13
 */
public final class DBSession implements Serializable, Delayed {
	/**
	 * Some serial version
	 */
	private static final long	serialVersionUID	= 1L;

	/**
	 * The cookie name to use for session ids.
	 */
	public static final String COOKIE_NAME = "LAZYJ_ID"; //$NON-NLS-1$
	
	/**
	 * Cache active sessions.
	 */
	static final ConcurrentHashMap<String, DBSession>	mSessions		= new ConcurrentHashMap<>();
	
	/**
	 * Database properties.
	 */
	private static final ExtProperties dbProp;
	
	/**
	 * Thread that does the following actions:<br><ul>
	 * <li>extracts delayed flushes from the queue</li>
	 * <li>checks what sessions are inactive and flushes them to the database, then removes them from memory</li>
	 * <li>checks what sessions are still active but haven't been saved to the database for a long time, and does this</li>
	 * <li>removes inactive sessions from the database</li>
	 * </ul>
	 */
	private static final SessionsCleaner cleanerThread = new SessionsCleaner();
	
	/**
	 * Whether or not the sessions have database backing or they are just kept in memory
	 */
	static boolean bDBEnabled = false;
	
	/**
	 * After this many idle minutes the session is flushed to DB.
	 */
	static int iMemoryFlushMinutes = 5;
	
	/**
	 * After this many idle minutes, when the sessions are NOT db-backed, the session is removed from memory
	 */
	static int iMemoryNoDBFlushMinutes = 30;
	
	/**
	 * After this many idle minutes the session is completely removed from DB (if db-backing is enabled)
	 */
	static int iDBFlushMinutes = 120;
	
	static{
		final String s = Utils.getLazyjConfigFolder();
		
		if (s==null){
			System.err.println("lazyj.DBSession : system property 'lazyj.config.folder' is not defined. As a consequence sessions do NOT have a DB backing!"); //$NON-NLS-1$
			dbProp = new ExtProperties();
		}
		else{
			dbProp = new ExtProperties(s, "dbsessions"); //$NON-NLS-1$
			dbProp.addObserver(new Observer(){
				@Override
				public void update(final Observable o, final Object arg) {
					reload();
				}
			});
		}
		
		reload();
		
		cleanerThread.start();
	}
	
	/**
	 * Reload configuration when underlying configuration file has changed.
	 */
	@SuppressWarnings("nls")
	static void reload(){
		final boolean bPrevEnabled = bDBEnabled;
		
		bDBEnabled = dbProp.getb("database.enabled", bDBEnabled);

		if (bDBEnabled && !bPrevEnabled){
			final DBFunctions db = getDB();
		
			if (!db.query("SELECT 2;", true) || db.geti(1)!=2){
				System.err.println("lazyj.DBSession : disabling database backing because I cannot execute the simplest query!");
				bDBEnabled = false;
			}
			
			if (bDBEnabled && db.query("CREATE TABLE sessions (id text, app int, ip text, lastaccess int, username text, fullname text, values text);", true)){
				db.query("CREATE UNIQUE INDEX sessions_pkey ON sessions(id, app, ip);");
				db.query("CREATE INDEX sessions_lastaccess_idx ON sessions(lastaccess);");
			}
			
			db.close();
		}
		
		iMemoryFlushMinutes = dbProp.geti("flush.memory", iMemoryFlushMinutes);
		iMemoryNoDBFlushMinutes = dbProp.geti("flush.memory.nodb", iMemoryNoDBFlushMinutes);
		iDBFlushMinutes = dbProp.geti("flush.db", iDBFlushMinutes);
	}

	/**
	 * Actual session values
	 */
	private final HashMap<String, Serializable>	mValues		= new HashMap<>();

	/**
	 * Session ID
	 */
	private final String						sID;

	/**
	 * Application ID. See {@link ExtendedServlet#getApp()}.
	 */
	private final int							iApp;
	
	/**
	 * Client's IP address
	 */
	private final String						sIP; 

	/**
	 * Flag that tells if this session is IP protected (eg. if the same session id is allowed from several client IP addresses)
	 */
	private final boolean 						bIPProtected;

	/**
	 * Username, if authenticated.
	 */
	private String								sUsername	= null;
	
	/**
	 * Complete name of the authenticated user.
	 */
	private String								sFullname	= null;
	
	/**
	 * User's browser.
	 */
	private String								sUserAgent  = null;
	
	/**
	 * Last access time for this session.
	 */
	long										lLastAccess = System.currentTimeMillis();
	
	/**
	 * Last time the session was saved to the database.
	 */
	long										lLastSaved  = 0;
		
	/**
	 * Last visited page on the site.
	 */
	private String								sLastPage   = null;
	
	/**
	 * When executing delayed database updates, this is the moment in time when the operation should be done.
	 */
	private long								flushTime	= 0;
		
	/**
	 * Pointer to the current page that asked for a session. 
	 */
	private transient ExtendedServlet			tp;
	
	/**
	 * Create a new session ID.
	 * 
	 * @return an unique session ID.
	 */
	private static String generateID() {
		return UUID.randomUUID().toString();
	}
		
	/**
	 * Get a connection to the database, as specified by the configuration properties file. 
	 * 
	 * @return a connection to the database
	 */
	static final DBFunctions getDB(){
		return new DBFunctions(dbProp);
	}
	
	/**
	 * Get the unique ID for this session.
	 * 
	 * @return sessions's unique ID.
	 */
	public String getID() {
		return this.sID;
	}
	
	/**
	 * @param s1
	 * @param s2
	 * @return true if the strings are equal or both are <code>null</code>
	 */
	private static boolean equals(final String s1, final String s2){
		return s1 == null ? s2 == null : s1.equals(s2);
	}
	
	/**
	 * This method should be used when you want for example to force the user to first authenticate itself
	 * then to return it to the page that it tried to access.
	 * 
	 * @param s page to set into the session
	 * @see ExtendedServlet#getCurrentPage()
	 */
	public void setLastPage(final String s) {
		if (!equals(this.sLastPage, s)){
			this.sLastPage = s;
			save(200);
		}
	}
	
	/**
	 * This is the last page that the user tried to access.
	 * 
	 * @return last page that the user tried to access.
	 */
	public String getLastPage(){
		return this.sLastPage;
	}

	/**
	 * If you want to create nice statistics for your site(s) you could track also the user's browser.
	 * Not a very useful information, use it only if you think it's meaningful to you.
	 * 
	 * @param s user's browser, you probably want to use <code>request.getHeader("User-Agent")</code> here.
	 */
	public final void setUserAgent(final String s) {
		if (!equals(this.sUserAgent, s)){
			this.sUserAgent = s;
			save(200);
		}
	}
	
	/**
	 * Get user's browser
	 * 
	 * @return user's browser identification string.
	 */
	public String getUserAgent(){
		return this.sUserAgent;
	}
	
	/**
	 * When the user is authenticated, you should call this method to store the username and the complete name
	 * in the session as well in separate fields in the database. The database info can be later used to 
	 * check if the user is authenticated from other pieces of code that don't use this code base (PHP? ...) 
	 * 
	 * @param sUserAccount account name
	 * @param sUserFullname complete name
	 */
	public void setUser(final String sUserAccount, final String sUserFullname) {
		if (!equals(this.sUsername, sUserAccount) || !equals(this.sFullname, sUserFullname)){
			this.sUsername = sUserAccount;
			this.sFullname = sUserFullname;
		
			save(200);
		}
	}

	/**
	 * Get the account associated with this session.
	 * 
	 * @return account name
	 */
	public String getUsername(){
		return this.sUsername;
	}
	
	/**
	 * Get the full name of the user that is associated with this session. 
	 * 
	 * @return full user name
	 */
	public String getFullname(){
		return this.sFullname;
	}
	
	/**
	 * Get the application for which this session is valid.
	 * 
	 * @return the appliction ID
	 */
	public int getApp(){
		return this.iApp;
	}

	/**
	 * Get the client's IP address
	 * 
	 * @return the IP address
	 */
	public String getClientIP(){
		return this.sIP;
	}
	
	/**
	 * This is the entry point for the session engine. From the page it takes a previously set session id, if any,
	 * the application unique id and the client's IP address. With this information the session is first looked up
	 * in the internal cache, then in the database, and if it is not find in neither of them a new session ID is
	 * generated.<br>
	 * <br>
	 * Sessions are cached in memory for up to 5 minutes. After this interval of inactivity, the session is removed
	 * from memory but kept in the database for two hours. If at some inactivity time the user tries to access the
	 * site and the session is still in the database, the entry will be put again in the memory cache (as expected).<br>
	 * <br>
	 * The session is saved in the database only when there is some activity with it. This means that only after
	 * any of the fields is touched the following actions are taken:<br><ul>
	 * <li>the session id actually sent to the client as a session cookie</li>
	 * <li>the session is put in the memory cache</li>
	 * <li>the session is saved in database, so it can be seen by 3rd party software and can be used to restore the sessions
	 *     in the case of software restarts</li>
	 * </ul>
	 * <br>
	 * If the session that is returned by this method is not written, but only checked for some values, then the
	 * corresponding object / ID will be dropped as soon as the servlet finishes with it. 
	 * 
	 * @param tp the page that is currently beeing executed
	 * @return client's session
	 */
	public static DBSession getSession(final ExtendedServlet tp){
		final String sIP = tp.getHostAddr();
		final int iApp  = tp.getApp();
		final String sID = tp.getCookie(COOKIE_NAME);
		
		final boolean bIPProtection = tp.isSessionIPProtected();
		
		final String sKey = sID + '/' + iApp + (bIPProtection ? '/' + sIP : ""); //$NON-NLS-1$
		
		DBSession dbs = mSessions.get(sKey);
		
		if (dbs==null && bDBEnabled){
			final DBFunctions db = getDB();
		
			try{
				db.query("SELECT values FROM sessions WHERE id='"+Format.escSQL(sID)+"' AND app="+iApp+(bIPProtection ? " AND ip='"+Format.escSQL(sIP)+"'" : "")+";");  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			
				if (db.moveNext()){
					dbs = decode(db.gets("values"), tp.getClass().getClassLoader()); //$NON-NLS-1$
				}
			}
			finally{
				db.close();
			}
		
			if (dbs!=null){
				mSessions.put(sKey, dbs);
			}
		}
		
		if (dbs==null){
			dbs = new DBSession(tp);
		}
		
		dbs.lLastAccess = System.currentTimeMillis();
		dbs.tp = tp;
		return dbs;
	}
	
	/**
	 * Hidden constructor. See {@link #getSession(ExtendedServlet)} for more info about the use of this class.
	 * 
	 * @param servlet current servlet that is under execution
	 */
	private DBSession(final ExtendedServlet servlet) {
		this.tp = servlet;
		
		this.sID = generateID();
		this.sIP = servlet.getHostAddr();
		this.iApp = servlet.getApp();
		this.bIPProtected = servlet.isSessionIPProtected();
	}

	/**
	 * This queue is used to delay database updates. It is useful because in many cases not only a single 
	 * modification is done in the session, but multiple updates happen at once (at login for example).
	 * In this case it is wise to wait a bit before commiting to the database, to do a single operation that will
	 * reflect all the updates.
	 */
	static final DelayQueue<DBSession> dq = new DelayQueue<>();
	
	/**
	 * Cleanup expired sessions
	 * 
	 * @author costing
	 * @since Jan 17, 2009
	 */
	private static final class SessionsCleaner extends Thread {
		
		/**
		 * Just set a name for this thread and make it a deamon thread.
		 */
		public SessionsCleaner(){
			super("lazyj.DBSessions - cleaner / flusher"); //$NON-NLS-1$
			setDaemon(true);
		}
		
		/**
		 * Executes the delayed database updates and from time to time cleans the memory cache and the 
		 * underlying table. 
		 */
		@Override
		public void run(){
			long lLastFlushed = System.currentTimeMillis();
			long lLastCleaned = lLastFlushed;
			
			while (true){
				try{
					final DBSession sess = dq.poll(1, TimeUnit.MINUTES);
					
					if (sess!=null){
						sess.makePersistent();
					}
				}
				catch (final InterruptedException ie){
					// ignore this, process the rest as usual
				}
				
				final long lNow = System.currentTimeMillis();
				
				if (lNow - lLastFlushed > 1000*60){
					final Iterator<Map.Entry<String,DBSession>> it = mSessions.entrySet().iterator();
					
					final long lFlush = lNow - 1000L*60*(bDBEnabled ? iMemoryFlushMinutes : iMemoryNoDBFlushMinutes);
					
					while (it.hasNext()){
						final Map.Entry<String, DBSession> me = it.next();
						
						final DBSession dbs = me.getValue();
						
						// inactive sessions are removed from memory cache, flushing them to disk
						if (dbs.lLastAccess < lFlush){
							dbs.makePersistent();
							it.remove();
							continue;
						}
						
						// make sure lLastAccess is updated in the database so active sessions that are always
						// kept in memory don't expire from the database
						if (dbs.lLastSaved < lNow - 1000*60*5){
							dbs.makePersistent();
						}
					}
					
					lLastFlushed = lNow;
				}
				
				if (lNow - lLastCleaned > 1000*60*10){
					if (bDBEnabled)
						getDB().query("DELETE FROM sessions WHERE lastaccess < extract(epoch from now()-'"+iDBFlushMinutes+" minutes'::interval)::int;"); //$NON-NLS-1$ //$NON-NLS-2$
					
					lLastCleaned = lNow;
				}
			}
		}
		
	}
	
	/**
	 * This method is called after updates to any fields. It will delay the write with the specified amount of
	 * time. If the entry is anyway scheduled to be written to the database, no action is taken. This means that 
	 * the only first delayed request is taken into account, the rest are silently dropped until that first 
	 * request is executed. 
	 * 
	 * @param lTimeout delay period for this save request, can be 0 to force the flush at once
	 */
	private void save(final long lTimeout){
		if (this.tp != null) {
			this.tp.setCookie(COOKIE_NAME, getID());
		}
		
		if (this.lLastSaved == 0 || lTimeout<10){
			makePersistent();
		}
		else{
			if (!dq.contains(this)){
				this.flushTime = System.currentTimeMillis() + lTimeout;
				dq.offer(this);
			}
		}
		
		final String sKey = this.sID + '/' + this.iApp + (this.bIPProtected ? '/' + this.sIP : ""); //$NON-NLS-1$
		
		mSessions.put(sKey, this);
	}
	
	/**
	 * This method actually saves the values in the database.
	 */
	@SuppressWarnings("nls")
	void makePersistent() {
		this.lLastSaved = System.currentTimeMillis();
		
		if (!bDBEnabled)
			return;
		
		try {
			final ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
			final ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);
			oos.flush();
			oos.close();
			baos.flush();
			baos.close();
			
			final String sValue = encodeBytes(baos.toByteArray());

			final DBFunctions db = getDB();

			db.query("UPDATE sessions SET " + 
				"values='" + sValue + "'," + 
				"username=" + formatName(this.sUsername) + "," + 
				"fullname=" + formatName(this.sFullname) + "," + 
				"lastaccess=" + (this.lLastAccess / 1000) + 
				" WHERE id='" + Format.escSQL(this.sID) + "' AND app=" + this.iApp + (this.bIPProtected ? " AND ip='" + Format.escSQL(this.sIP) + '\'' : "") + ';'
			);

			if (db.getUpdateCount()<=0){
				db.query(
					"INSERT INTO sessions (id, app, ip, username, fullname, lastaccess, values) VALUES ("+
						"'" + Format.escSQL(this.sID) + "', "+
						this.iApp+","+
						"'" + Format.escSQL(this.sIP) + "', "+
						formatName(this.sUsername)+","+
						formatName(this.sFullname)+","+
						(this.lLastAccess/1000)+","+
						"'" + sValue + "');"
				);
			}
			
			db.close();
		}
		catch (final Throwable e) {
			Log.log(Log.WARNING, "lazyj.DBSession", "exception saving a session into the db", e);
		}
	}

	/**
	 * @param sName
	 * @return string as SQL string
	 */
	private static final String formatName(final String sName){
		if (sName==null)
			return "null"; //$NON-NLS-1$
		
		return '\''+Format.escSQL(sName)+'\''; 
	}
	
	/**
	 * This method will delete all the information about this session. Use this at <i>logout</i> operations.
	 */
	public void invalidate() {
		if (bDBEnabled){
			final DBFunctions db = getDB();
		
			db.query("DELETE FROM sessions WHERE id='" + Format.escSQL(this.sID) + "' AND app="+this.iApp+(this.bIPProtected ? " AND ip='"+Format.escSQL(this.sIP)+'\'' : "")+';');  //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
			
			db.close();
		}
		
		mSessions.remove(this.sID + '/' + this.iApp + (this.bIPProtected ? '/' + this.sIP : "")); //$NON-NLS-1$
		
		if (this.tp != null){
			this.tp.setCookie(COOKIE_NAME, "", 0); //$NON-NLS-1$
		}
	}
	
	/**
	 * Set a session variable, by associating a value with an unique key. When the information is changed the
	 * session will be flushed to the database and the client will receive the cookie ID.
	 * 
	 * @param sKey unique key, cannot be null.
	 * @param oValue associated value. If null it will just remove a previously set value.
	 */
	public void put(final String sKey, final Serializable oValue) {
		boolean bSave = false;
		
		if (oValue != null){
			bSave = !oValue.equals(this.mValues.put(sKey, oValue));
		}
		else{
			bSave = this.mValues.remove(sKey) != null;
		}
		
		if (bSave){
			save(200);			
		}
		
	}

	/**
	 * Get the object that was previously associated with this key.
	 * 
	 * @param sKey unique key
	 * @return object associated with this key
	 */
	public Serializable get(final String sKey) {
		return this.mValues.get(sKey);
	}

	/**
	 * Get the string value for this key. If the previously associated object was in fact a String, it is returned
	 * as it is. Otherwise the toString() method is called on the existing object. If the entry is <code>null</code>
	 * then the empty string is returned to the caller.
	 * 
	 * @param sKey unique key
	 * @return string representation of this key, never null
	 */
	public String gets(final String sKey) {
		final Object o = get(sKey);

		if (o==null)
			return ""; //$NON-NLS-1$
		
		if (o instanceof String)
			return (String) o;
		
		return o.toString();
	}

	/**
	 * Get the integer value associated with this key, with the default 0.
	 * 
	 * @param sKey unique key
	 * @return integer value associated with this key.
	 */
	public int geti(final String sKey) {
		return geti(sKey, 0);
	}

	/**
	 * Get the integer value associated with this key. If the previously set object is a Number, then
	 * intValue() of it is returned. Otherwise the toString() on that object is tried to be parsed into an integer
	 * value. If this doesn't succeed then the given default value is returned.  
	 * 
	 * @param sKey unique key
	 * @param iDefault default value to return in case of an error
	 * @return the previously set integer value, of the default value in case of an error
	 */
	public int geti(final String sKey, final int iDefault) {
		try {
			final Object o = get(sKey);
			
			if (o == null)
				return iDefault;
			
			if (o instanceof Number)
				return ((Number) o).intValue();
			
			return Integer.parseInt(o.toString());
		} catch (final NumberFormatException e) {
			return iDefault;
		}
	}

	/**
	 * Object stream decoder
	 * 
	 * @author costing
	 * @since Jan 17, 2009
	 */
	private static final class MyObjectInputStream extends ObjectInputStream {
		/**
		 * Class loader, to be able to load objects even if they are in some servlet's zone
		 */
		private final ClassLoader loader;
		
		/**
		 * @param is
		 * @param loader 
		 * @throws IOException
		 */
		public MyObjectInputStream(final InputStream is, final ClassLoader loader) throws IOException {
			super(is);
			
			this.loader = loader;
		}
		
		@Override
		protected Class<?> resolveClass(final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
			try{
				return super.resolveClass(desc);
			}
			catch (final ClassNotFoundException cnfe){
				if (this.loader!=null){
					// we have an alternative class loader, try to use this one, maybe we are lucky this time
					return Class.forName(desc.getName(), false, this.loader);
				}
				
				throw cnfe;
			}
		}
	}
	
	/**
	 * @param s
	 * @param loader
	 * @return the session from DB
	 */
	private static DBSession decode(final String s, final ClassLoader loader) {
		ByteArrayInputStream bais = null;
		ObjectInputStream ois = null;
		
		try {
			bais = new ByteArrayInputStream(decodeString(s));
			ois = new MyObjectInputStream(bais, loader);
			
			final DBSession readObject = (DBSession) ois.readObject();

			return readObject;
		}
		catch (final Throwable e) {
			Log.log(Log.WARNING, "lazyj.DBSession", "exception decoding previously saved value", e); //$NON-NLS-1$ //$NON-NLS-2$
			
			return null;
		}
		finally{
			if (ois!=null){
				try{
					ois.close();
				}
				catch (final IOException ioe){
					// ignore
				}
			}
			
			if (bais!=null){
				try{
					bais.close();
				}
				catch (final IOException ioe){
					// ignore
				}
			}
		}
	}

	/**
	 * Convert a byte array to a String with only letters
	 * 
	 * @param binaryContent array to encode
	 * @return String representation
	 * @see DBSession#decodeString(String)
	 */
	public static final String encodeBytes(final byte[] binaryContent){
		if (binaryContent == null)
			return null;
		
		final char[] chars = new char[binaryContent.length * 2];

		for (int i = 0; i < binaryContent.length; i++) {
			int k = binaryContent[i];
			k += 128;
			chars[i * 2] = (char) ((k / 16) + 'A');
			chars[i * 2 + 1] = (char) ((k % 16) + 'A');
		}
		
		return new String(chars);
	}
	
	/**
	 * Decode a text previously encoded with {@link #encodeBytes(byte[])} to the original byte array
	 * 
	 * @param text string to decode
	 * @return byte array
	 */
	public static final byte[] decodeString(final String text){
		if (text==null)
			return null;
		
		final char[] characters = text.toCharArray();
		final int l = characters.length / 2;
		final byte[] bytes = new byte[l];
		
		for (int i = 0; i < l; i++) {
			final int val = (characters[i * 2] - 'A') * 16 + (characters[i * 2 + 1] - 'A');
			bytes[i] = (byte) val;
			bytes[i] -= 128;
		}
		
		return bytes;
	}
	
	/**
	 * For internal statistics, get the list of all active sessions for a given application. You can
	 * specify here if you want all the active sessions or only the ones that have an associated username.
	 * 
	 * @param iAppId application unique id (see {@link ExtendedServlet#getApp()}). Can be 0 to retrive all known sessions.
	 * @param bOnlyLogged true if you want only the authenticated user, false otherwise
	 * @return a list of active sessions (the ones that are in memory) for this app (or all)  
	 */
	public static final List<DBSession> getSessionsList(final int iAppId, final boolean bOnlyLogged) {
		final List<DBSession> l = new LinkedList<>();

		for (final DBSession so : mSessions.values()) {
			if (iAppId == 0 || iAppId == so.iApp && (!bOnlyLogged || so.sUsername != null))
				l.add(so);
		}

		return l;
	}

	/**
	 * For statistics, get a list of usernames and the pages that they are currently visiting
	 * 
	 * @param iAppId application unique id (see {@link ExtendedServlet#getApp()}). Can be 0 to retrive info from all applications.
	 * @return a list of strings like <code>username|page</code> for all the in-memory sessions.
	 */
	public static final List<String> getPagesList(final int iAppId) {
		// elements are strings, in the form : username|URL

		final List<String> l = new LinkedList<>();

		for (final DBSession so : mSessions.values()) {
			if (iAppId == 0 || iAppId == so.iApp)
				l.add(so.sUsername + '|' + so.sLastPage);
		}

		return l;
	}

	/**
	 * Implementation of {@link Delayed}
	 * @param unit time unit in which to return the value
	 * @return how much time is left until this entry will expire
	 */
	@Override
	public long getDelay(final TimeUnit unit) {
		return unit.convert(this.flushTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Implementation of {@link Delayed}
	 * @param o object to compare to
	 * @return sorting for these objects, to put in front the ones that expire sooner
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
	
	@Override
	public boolean equals(final Object o){
		if (o==null || !(o instanceof DBSession))
			return false;
		
		return compareTo((DBSession) o)==0;
	}
	
	@Override
	public int hashCode(){
		return (int) this.flushTime;
	}
	
	/**
	 * Overriden method to set the calling page to null when deserializing. This field will be set at a later
	 * time by {{@link #getSession(ExtendedServlet)}
	 * 
	 * @return exactly the current object, but with the transient field set to null 
	 */
	public Object readResolve() {
		this.tp = null;
		return this;
	}
	
	/**
	 * Clear cached sessions
	 */
	public static void clear(){
		final Iterator<Map.Entry<String, DBSession>> it = mSessions.entrySet().iterator();
		
		while (it.hasNext()){
			final Map.Entry<String, DBSession> me = it.next();
			
			me.getValue().makePersistent();
			
			it.remove();
		}
	}
	
	/**
	 * Statistics function: get the number of sessions that are kept in the memory cache.
	 * 
	 * @return number of session cache entries.
	 */
	public static int getCacheSize(){
		return mSessions.size();
	}	
}
