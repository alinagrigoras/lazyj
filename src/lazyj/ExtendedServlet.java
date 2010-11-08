package lazyj;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.StringTokenizer;

import javax.servlet.SingleThreadModel;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUtils;

import com.oreilly.servlet.MultipartRequest;

/**
 * Probably the most important class in the world :)<br><br>
 * This class is a wrapper over the original HttpServlet, adding caching and run time limit for the servlets.
 * There are also other useful functions, like {@link #initMultipartRequest(String, int)} for uploading files,
 * getX (s/i/f/d) for parsing the parameters and getting the correct value,  {@link #getCookie(String)},
 * {@link #setCookie(String, String)}, {@link #log(int, String)}
 * 
 * @author costing
 * @since 2006-10-02
 * @see RequestWrapper
 */
public abstract class ExtendedServlet extends HttpServlet implements SingleThreadModel {

	/**
	 * Some serial code
	 */
	private static final long serialVersionUID = 1L;

	/** This is when the server was started */
	public static final long		lServerStarted	= System.currentTimeMillis();
	
	/** Cache hit statistics : the number of hits in the page cache*/
	private static volatile long	lHit 			= 0;	
	
	/** Cache hit statistics : the number of accesses to the cache*/
	private static volatile long	lTotal 			= 0;
	
	/** Framework statistics: the number of servlet calls */
	private static volatile long	lRequests		= 0;
	
	/* Request-related variables */
	/** The original request object */
	protected HttpServletRequest	request			= null;

	/** The original response object */
	protected HttpServletResponse	response		= null;

	/** OutputStream to the client */
	protected OutputStream			osOut			= null;

	/** PrintWriter to the client. Same as osOut, but other interface */
	protected PrintWriter			pwOut			= null;

	/** Current servlet that is executed */
	protected String				sPage			= getClass().getName();

	/** The zone of the currently executed servlet */
	protected String				sZone			= "<unknown>"; //$NON-NLS-1$

	/** Object used for file uploading. See {@link #initMultipartRequest(String, int)}. */
	protected MultipartRequest	mpRequest		= null;

	/** Remember whether or not there was a redirect executed by the servlet */
	protected boolean				bRedirect 		= false;

	/** Servlets can treat GET and POST differently based on the method they override or by looking at this variable */
	protected boolean				bGet			= false;

	/** Current session */
	public DBSession				dbs				= null;

	/**
	 * Request wrapper that does all the work
	 */
	private RequestWrapper			rw				= null;
	
	/**
	 * Content type, set by {@link #setContentType(String)}, used to put again the same content type when serving content from cache
	 */
	String sContentType = null;
	
	/** 
	 * Override this method to return any value that would alter the cache.
	 * Usually you will want to return here the username, if the user is logged.
	 * Or the value of some cookie, if it alters the contents of the caching ...
	 * 
	 * @return some string that is appended to the caching key. 
	 */
	protected String getCacheKeyModifier() {
		return ""; //$NON-NLS-1$
	}

	/**
	 * Statistics: get the total number of accesses to pages that could be cached
	 * @return the total number of pages served through this framework
	 */
	public static final long getCacheAccesses() {
		return lTotal;
	}

	/**
	 * Statistics: get the number of cache hits
	 * @return the number of pages served from a memory cache by this framework
	 */
	public static final long getCacheHits() {
		return lHit;
	}

	/**
	 * Statistics: get the server uptime
	 * @return Server uptime, as formatted string
	 */
	public static final String getServerUptime() {
		return Format.toInterval(System.currentTimeMillis() - lServerStarted);
	}

	/**
	 * Framework statistics: get the total number of requests serviced by the framework.
	 * 
	 * @return total number of requests to servlets that extend the framework
	 */
	public static final long getFrameworkRequests(){
		return lRequests;
	}

	/**
	 * Method to specify the maximum life time of the cached contents.
	 * You should override this method to return the number of seconds for which
	 * you know that the contents will not change and it's safe to serve the users
	 * the same contents.<br>
	 * <br>
	 * This method returns 0 in the default implementation, meaning that the caching is by default
	 * disabled for all the servlets.
	 * 
	 * @return number of seconds for which the contents is valid, default is 0 (cache disabled)
	 */
	protected long getCacheTimeout() {
		return 0; // default cache policy : disabled
	}
	
	/**
	 * Method to specify the domain for which the cookies will be set by default (session cookie included).
	 * You should override this method to return something like ".acasa.ro".
	 * 
	 * @return the domain for which the cookies will be set by default
	 */
	protected String getDomain() {
		assert false : "Override getDomain() to return a meaningfull value for your application"; //$NON-NLS-1$
		return ".unset.domain.do"; //$NON-NLS-1$
	}
	
	/**
	 * Method to specify the maximum run time for a page. After this much time (in seconds) the thread
	 * that runs the page is forcefully killed. Override this method if you want to allow a maximum 
	 * execution time different than the default 60 seconds.<br>
	 * <br>
	 * In <b>special</b> (read very very unlikely cases) you can return 0 to disable the threads monitoring.
	 * This means that this thread is allowed to run forever, and if it does something bad, nobody will
	 * catch it. So, you've been warned ...
	 *  
	 *  
	 * @return the maximum run time, in seconds, for the current page.
	 */
	protected int getMaxRunTime() {
		return 60;
	}

	/**
	 * A servlet must implement at least this method.
	 */
	public abstract void execGet();

	/**
	 * By default POST request are executed with the same {@link #execGet()} function.
	 * If you want to execute a different code on POST than on GET you can override this
	 * function or you can check the {@link #bGet} variable.
	 */
	public void execPost() {
		execGet();
	}

	/**
	 * Get the application number. Each major application should have a different, unique, identifier.
	 * This is to distinguish between the sessions, which ones are for which application, when creating statistics.
	 * See {@link #getOnlineUsers()} for example.
	 * 
	 * @return application unique number
	 */
	protected int getApp() {
		assert false : "Override getApp() to return a meaningfull value for your application"; //$NON-NLS-1$
		return 0;
	}

	/**
	 * Flag to set whether or not the session is valid only when used from the first IP that generated the session.
	 * In some cases (multiple load-balancing http proxies) this schema might not work. 
	 * 
	 * @return true by default, but can be overriden by actual implementations
	 */
	public boolean isSessionIPProtected(){
		return true;
	}
	
	
	/**
	 * Verify if the page is cacheable.<br>
	 * <br>If it is cacheable then:<br>
	 * <li> if the page is in cache, send the cached content to the client
	 * <li> if the page is not in cache, create a wrapper of the output stream, that will intercept the 
	 * 		generated output and on close will save the contents in the cache for later reuse 
	 * @return true if the page is to be executed, false if it was served from cache
	 */
	private boolean masterInit() {
		final long lTimeout = getCacheTimeout(); 
		
		if ((lTimeout > 0) && (this.bGet == true)) {
			log(Log.FINEST, "cacheable request : "+lTimeout); //$NON-NLS-1$
			
			final String sKey = PageCache.getCacheKey(this.request, getCacheKeyModifier());

			lTotal++;

			final CachingStructure cs = PageCache.getCache(sKey);

			if (cs != null) { // it's ok, i can write the cache content to the output
				lHit++;
				
				log(Log.FINEST, "serving request from cache : "+cs.length()); //$NON-NLS-1$

				this.response.setContentType(cs.sContentType);
				this.response.setHeader("Content-Language", "en"); //$NON-NLS-1$ //$NON-NLS-2$
				this.response.setContentLength(cs.length());
				
				RequestWrapper.setCacheTimeout(this.response, (int) ((cs.lGenerated+cs.lifetime-System.currentTimeMillis())/1000));

				try {
					this.osOut.write(cs.getContent());
				} catch (IOException e) {
					// ignore
				}
				
				try {
					this.osOut.flush();
				} catch (IOException e) {
					// ignore
				}
				
				try {
					this.osOut.close();
				}
				catch (Exception e) {
					// ignore
				}

				return false;
			}
			log(Log.FINEST, "generating the contents for key = '"+sKey+'\''); //$NON-NLS-1$
			
			this.osOut = new StringBufferOutputStream(sKey, lTimeout);
		} else { // this request cannot be cached, do nothing
			log(Log.FINEST, "uncacheable request"); //$NON-NLS-1$
			RequestWrapper.setNotCache(this.response);
		}
		
		return true;
	}
	
	/**
	 * If you set the content type to something else than "text/html; charset=UTF-8" and you use the caching mechanism
	 * you should use this method instead of directly calling <code>response.setContentType</code>
	 * 
	 * @param sContentType
	 */
	public void setContentType(final String sContentType){
		this.sContentType = sContentType;
		
		this.response.setContentType(sContentType);
	}

	/**
	 * Wrapper of the output stream, used by the caching mechanism to make a copy of the contents
	 * that is sent to the browser and populate the cache with it
	 * 
	 * @author costing
	 * @since always
	 */
	private final class StringBufferOutputStream extends OutputStream {
		/**
		 * Servlet original output stream
		 */
		private final OutputStream			origos;

		/**
		 * Request key
		 */
		private final String				sKey;

		/**
		 * Where to put the contents
		 */
		private final ByteArrayOutputStream	baos;

		/**
		 * For how long will we cache this newly generated contents
		 */
		private final long					lTimeout;
		
		
		/**
		 * The only constructor, takes the page unique key and the content lifetime
		 * 
		 * @param _sKey page unique key
		 * @param _lTimeout content lifetime in seconds
		 */
		public StringBufferOutputStream(final String _sKey, final long _lTimeout) {
			this.origos = ExtendedServlet.this.osOut;
			ExtendedServlet.this.pwOut = new PrintWriter(this);
			this.sKey = _sKey;
			this.lTimeout = _lTimeout;
			this.baos = new ByteArrayOutputStream(32 * 1024); // average page size
		}

		/**
		 * Override the write function to store the values in the internal ByteArrayOutputStream
		 * 
		 *  @param b data to write
		 */
		@Override
		public void write(int b) throws IOException {
			this.baos.write(b);
		}

		/**
		 * When the content generation is finished and the data is sent to the client, save the contents
		 * in the internal cache too.
		 */
		@Override
		public void close() throws IOException{
			ExtendedServlet.this.pwOut.flush();
			
			if (!ExtendedServlet.this.response.containsHeader("Location") && !ExtendedServlet.this.bRedirect) { //$NON-NLS-1$
				long lExpires = this.lTimeout * 1000; 
				
				if (CachingStructure.bonus.get(this.sKey)!=null)
					lExpires += this.lTimeout * 1000;
				
				final byte[] buff = this.baos.toByteArray();
				
				final CachingStructure cs = new CachingStructure(this.sKey, buff, lExpires, ExtendedServlet.this.sContentType);  

				PageCache.put(cs);
				
				ExtendedServlet.this.response.setContentLength(buff.length);
				RequestWrapper.setCacheTimeout(ExtendedServlet.this.response, (int) (lExpires/1000));
				
				this.origos.write(buff);
			}
			else {
				this.origos.write(this.baos.toByteArray());
			}

			this.origos.flush();
			this.origos.close();
		}

	}


	/**
	 * Get the value of a parameter as a string.
	 * 
	 * @param sParam the parameter name
	 * @return the string value of this parameter, never null ("" is returned in the worst case)
	 */
	public final String gets(final String sParam) {
		return this.rw.gets(sParam);
	}
	
	/**
	 * Get the value of a parameter as a string.
	 * 
	 * @param sParam the parameter name
	 * @param sDefault default value to return in case the value is not defined or is the empty string
	 * @return the string value of this parameter
	 */
	public final String gets(final String sParam, final String sDefault){
		return this.rw.gets(sParam, sDefault);
	}
	

	/**
	 * Get the value of a parameter as an integer value. If there is a parsing error then return the given default value. 
	 * 
	 * @param sParam the name of the parameter
	 * @param defaultVal default value to return in case of an error
	 * @return parsed value of the parameter, or the defaultValue if parameter is missing or is not an integer value representation
	 */
	public final int geti(final String sParam, final int defaultVal) {
		return this.rw.geti(sParam, defaultVal);
	}
	

	/**
	 * Get the value of a parameter as an integer value. If there is a parsing error then the value 0 will be returned. 
	 * 
	 * @param sParam name of the parameter
	 * @return parsed value of the request parameter, or 0 if there is any error in parsing, parameter not existing etc
	 */
	public final int geti(final String sParam) {
		return this.rw.geti(sParam);
	}
	
	
	/**
	 * Get the value of a parameter as a long value. If there is a parsing error then return the given default value. 
	 * 
	 * @param sParam the name of the parameter
	 * @param defaultVal default value to return in case of an error
	 * @return parsed value of the parameter, or the defaultValue if parameter is missing or is not a long value representation
	 */
	public final long getl(final String sParam, final long defaultVal) {
		return this.rw.getl(sParam, defaultVal);
	}
	

	/**
	 * Get the value of a parameter as a long value. If there is a parsing error then the value 0 will be returned. 
	 * 
	 * @param sParam name of the parameter
	 * @return parsed value of the request parameter, or 0 if there is any error in parsing, parameter not existing etc
	 */
	public final long getl(final String sParam) {
		return this.rw.getl(sParam);
	}
	
	
	/**
	 * Get the value of a parameter as a float value. If there is a parsing error then return the given default value. 
	 * 
	 * @param sParam the name of the parameter
	 * @param defaultVal default value to return in case of an error
	 * @return parsed value of the parameter, or the defaultValue if parameter is missing or is not a float value representation
	 */
	public final float getf(final String sParam, final float defaultVal) {
		return this.rw.getf(sParam, defaultVal);
	}
	

	/**
	 * Get the value of a parameter as a float value. If there is a parsing error then the value 0 will be returned. 
	 * 
	 * @param sParam name of the parameter
	 * @return parsed value of the request parameter, or 0 if there is any error in parsing, parameter not existing etc
	 */
	public final float getf(final String sParam) {
		return this.rw.getf(sParam);
	}
	

	/**
	 * Get the value of a parameter as a double value. If there is a parsing error then return the given default value. 
	 * 
	 * @param sParam the name of the parameter
	 * @param defaultVal default value to return in case of an error
	 * @return parsed value of the parameter, or the defaultValue if parameter is missing or is not a double value representation
	 */
	public final double getd(final String sParam, final double defaultVal) {
		return this.rw.getd(sParam, defaultVal);
	}
	

	/**
	 * Get the value of a parameter as a double value. If there is a parsing error then the value 0 will be returned. 
	 * 
	 * @param sParam name of the parameter
	 * @return parsed value of the request parameter, or 0 if there is any error in parsing, parameter not existing etc
	 */
	public final double getd(final String sParam) {
		return this.rw.getd(sParam);
	}
	
	/**
	 * Get all the values of a parameter.
	 * 
	 * @param sParam
	 * @return values array, possibly empty but never null
	 */
	public final String[] getValues(final String sParam){
		return this.rw.getValues(sParam);
	}

	/**
	 * Wrapper method for the actual redirect, that keeps track if there was such an opperation.
	 * This is done in order to disable the caching in this case. 
	 * 
	 * @param sURL the URL to send the browser to
	 * @return true if the operation was successfully, false if there was an error
	 */
	protected final boolean redirect(final String sURL) {
		try {
			this.bRedirect = true; // disable caching no matter what happens
			this.response.sendRedirect(sURL);
			return true;
		} catch (Exception e) {
			Log.log(Log.FATAL, "lazyj.ExtendedServlet", "ServletExtension (" + this.sZone + '/' + this.sPage + "), redirect('" + sURL + "'), exception: " + e);  //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
			return false;
		}
	}
	

	/**
	 * Produce safe SQL strings. Wrapper for {@link lazyj.Format#escSQL(String)} call.
	 * 
	 * @param s the string to escape
	 * @return the SQL-safe representation
	 */
	public static final String esc(final String s) {
		return Format.escSQL(s);
	}
	

	/**
	 * Produce save HTML strings. Wrapper for {@link lazyj.Format#escHtml(String)} call.
	 * 
	 * @param s the string to escape
	 * @return the HTML-safe representation
	 */
	public static final String escHtml(final String s) {
		return Format.escHtml(s);
	}
	


	/**
	 * Get the value of a specific cookie
	 * 
	 * @param sName the name of the cookie
	 * @return the value of the cookie, or "" if there was an error (cookie not existing ...)
	 */
	public final String getCookie(final String sName) {
		return this.rw.getCookie(sName);
	}
	

	/**
	 * Send a session cookie to the browser. The cookie is made available to the domain returned by the {@link #getDomain()} function
	 * 
	 * @param sName name of the cookie
	 * @param sValue value
	 * @return true on success, false on error
	 */
	public boolean setCookie(final String sName, final String sValue) {
		return setCookie(sName, sValue, -1);
	}
	
	
	/**
	 * Send a cookie to the browser. The cookie is made available to the domain returned by the {@link #getDomain()} function
	 * 
	 * @param sName name of the cookie
	 * @param sValue value
	 * @param iAge lifetime of the cookie, in seconds, relative to "now". Negative=session cookie. Zero=delete this cookie.
	 * @return true on success, false on error
	 */
	public boolean setCookie(final String sName, final String sValue, final int iAge) {
		return setCookie(sName, sValue, getDomain(), iAge);
	}
	

	/**
	 * Send a cookie to the browser.
	 * 
	 * @param sName name of the cookie
	 * @param sValue value
	 * @param sDomain the domain for which this cookie is set
	 * @param iAge lifetime of the cookie, in seconds, relative to "now". Negative=session cookie. Zero=delete this cookie.
	 * @return true on success, false on error
	 */
	public boolean setCookie(final String sName, final String sValue, final String sDomain, final int iAge) {
		try {
			final Cookie c = new Cookie(sName, sValue);

			c.setMaxAge(iAge);

			c.setDomain(sDomain);
			c.setPath("/"); //$NON-NLS-1$
			c.setSecure(false);

			this.response.addCookie(c);

			return true;
		} catch (Exception e) {
			log(Log.WARNING, "setCookie exception", e); //$NON-NLS-1$

			return false;
		}
	}
	

	/**
	 * This is a hook provided for the servlets as a generic way to initialize their local variables, do
	 * authentication and so on. Generally this is implemented in each zone's local master servlet and 
	 * checks for user's session. You probably want to override this.
	 * 
	 * Use this to initialize only the variables used in {@link #getCacheTimeout()} and {@link #getCacheKeyModifier()}.
	 * For IO-related variables (eg pMaster) use {@link #zoneInit()}.
	 */
	protected void doInit(){
		// override this to do something interesting
	}
	

	/**
	 * Override this function to initialize local servlet or zone variables (pMaster for example)
	 */
	protected void zoneInit(){
		// override this to do something interesting
	}
	
	
	/** Original request from servlet engine comes through here. It will be handled internally.
	 *  
	 * @param req servlet request 
	 * @param resp servlet response
	 */ 
	@Override
	public final void doGet(final HttpServletRequest req, final HttpServletResponse resp) {
		execute(req, resp, true);
	}
	

	/** Original request from servlet engine comes through here. It will be handled internally.
	 *  
	 * @param req servlet request 
	 * @param resp servlet response
	 */
	@Override
	public final void doPost(final HttpServletRequest req, final HttpServletResponse resp) {
		execute(req, resp, false);
	}
	
	/**
	 * Remember if the Servlet API is 2.1+ 
	 */
	private static volatile boolean bServlet21Plus = true;

	/**
	 * Set the name of the current thread.
	 * 
	 * @param sName new name
	 * @return old thread name
	 */
	private static String setName(final String sName){
		String sOldName;

		try{
			sOldName = Thread.currentThread().getName();
		}
		catch (Throwable t){
			sOldName = null;
		}
		
		try {
			Thread.currentThread().setName(sName);
		}
		catch (Throwable t){
			// ignore
		}		
		
		return sOldName;
	}
	
	/**
	 * This internal method actually executes the request by executing the following steps:<br><ul>
	 * <li>Set the {@link #request} and {@link #response} variables to the actual current request.</li>
	 * <li>Determine the zone and the page(servlet) and put them into {@link #sZone}, {@link #sPage}.</li>
	 * <li>Count the accesses to the framework.</li>
	 * <li>Registers the current thread into {@link ThreadsMonitor} to be killed if it runs for more than {@link #getMaxRunTime()}</li>
	 * <li>Calls the {@link #executeRequest()} method</li>
	 * <li>Unregisters the current thread from {@link ThreadsMonitor}</li>
	 * </ul><br>
	 * Any error thrown by the execution will be logged under "lazyj.ExtendedServlet".<br>
	 * <br>
	 * This method is synchronized to prevent broken servlet containers that ignore the SingleThreadModel
	 * specification to mess up with the actions. 
	 * 
	 * @param req original request object
	 * @param resp original response object
	 * @param isGet true if the request was GET, false if the request was POST
	 */
	private final synchronized void execute(final HttpServletRequest req, final HttpServletResponse resp, final boolean isGet) {
		this.bGet = isGet;
		
		this.request = req;
		this.response = resp;
		
		this.rw = new RequestWrapper(this.request);
		
		lRequests++;
		
		final String sPath = this.request.getServletPath();
		
		if (bServlet21Plus){
			try{
				// If we are running under Servlet API 2.1+ we have this method
				// Since we cannot invoke it directly, we have to introspect the request object
				final Method m = this.request.getClass().getDeclaredMethod("getContextPath"); //$NON-NLS-1$
				
				if (m!=null){
					this.sZone = (String) m.invoke(this.request);
					this.sPage = sPath;
					
					if (this.sZone!=null && this.sZone.startsWith("/")) //$NON-NLS-1$
						this.sZone = this.sZone.substring(1);
					
					if (this.sPage!=null && this.sPage.startsWith("/")) //$NON-NLS-1$
						this.sPage = this.sPage.substring(1);
				}
			}
			catch (NoSuchMethodException nsme){
				bServlet21Plus = false;
				this.sZone = null;
				this.sPage = null;
			}
			catch (Throwable _){
				this.sZone = null;
				this.sPage = null;
			}
		}
		
		if (!bServlet21Plus || this.sZone==null){
			try{
		
				final StringTokenizer st = new StringTokenizer(sPath, "/"); //$NON-NLS-1$

				this.sZone = st.nextToken();
			
				if (st.hasMoreTokens())
					this.sPage = st.nextToken();
				else{
					this.sPage = this.sZone;
					this.sZone = ""; //$NON-NLS-1$
				}
			}
			catch (Throwable t){
				this.sZone = null;
				this.sPage = null;				
			}
		}

		this.sZone = StringFactory.get(this.sZone);
		this.sPage = StringFactory.get(this.sPage);					
		
		final String sOldName = setName("lazyj.ExtendedServlet("+this.sZone+"/"+this.sPage+") : ACTIVE ("+this.request.getRemoteAddr()+")");  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		
		FrameworkStats.addPage(this.sZone, this.sPage);

		this.response.setHeader("P3P", "CP=\"NOI DSP COR LAW CURa DEVa TAIa PSAa PSDa OUR BUS UNI COM NAV\""); //$NON-NLS-1$ //$NON-NLS-2$

		final long lMaxRunTime = getMaxRunTime();
		
		final BoundedThreadContainer btc = lMaxRunTime > 0 ? new BoundedThreadContainer(getMaxRunTime(), Thread.currentThread(), this.sZone + '/' + this.sPage) : null;

		if (btc!=null)
			ThreadsMonitor.register(btc);
		
		try {
			executeRequest();
		}
		catch (Throwable t) {
			Log.log(Log.FATAL, "lazyj.ExtendedServlet", "Execution exception: "+(isGet?"GET":"POST")+" "+this.sZone+'/'+this.sPage, t); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}
		finally{
			if (btc!=null)
				ThreadsMonitor.unregister(btc);
		}
		
		// set back the original thread name 
		setName(sOldName);
	}
	

	/**
	 * This method should be called when the servlet expects a file upload from the client. It will initialize the
	 * {@link #mpRequest} object. If the file is too big or there is another problem uploading the file then the
	 * method will return false, if everything is ok then it will return true.
	 * 
	 * @param sTempDir temporary folder for the files that are uploaded
	 * @param iFileSizeLimit maximum size of a file
	 * @return true if the request is indeed a file upload, the file size is &lt;= iFileSizeLimit and the weather outside is fine  
	 */
	protected final boolean initMultipartRequest(final String sTempDir, final int iFileSizeLimit) {
		this.mpRequest = this.rw.initMultipartRequest(sTempDir, iFileSizeLimit);
		
		return this.mpRequest!=null;
	}

	/**
	 * This internal method is another wrapper for the execution. It will do the following:<br><ul>
	 * <li>initialize the session ({@link #dbs})
	 * <li>initialize the {@link #osOut} and {@link #pwOut} streams
	 * <li>execute {@link #masterInit()} to check if the page is in cache or not
	 * <li>call {@link #doInit()} to initialize servlets' local variables, if it is the case
	 * <li>log any Exception produced by the actual execution ({@link #execGet()}, {@link #execPost()}
	 * <li>close the streams
	 * <li>delete any temporary uploaded files
	 */
	private void executeRequest() {
		try {
			this.mpRequest = null;
			
			this.sContentType = "text/html; charset=UTF-8"; //$NON-NLS-1$
			
			this.dbs = DBSession.getSession(this);

			this.osOut = this.response.getOutputStream();
			this.pwOut = new PrintWriter(this.osOut);
		} catch (Exception e) {
			log(Log.ERROR, "exception geting the streams", e); //$NON-NLS-1$
		}
		
		doInit();
		
		if (!masterInit())
			return;
		
		zoneInit();

		try {
			if (this.bGet)
				execGet();
			else
				execPost();
		} catch (Exception e) {
			log(Log.ERROR, "Execution exception (get:" + this.bGet + ')', e); //$NON-NLS-1$
		}

		try {
			this.osOut.flush();
			this.osOut.close();
		} catch (IOException e) {
			// ignore
		}

		// after executing the request clean up the temporary files
		if (this.mpRequest != null)
			try {
				final Enumeration<?> e = this.mpRequest.getFileNames();

				while (e.hasMoreElements()) {
					final String sFieldName = (String) e.nextElement();
					
					try {
						this.mpRequest.getFile(sFieldName).delete();
					} catch (Exception ee) {
						// ignore
					}
				}
			}
			catch (Throwable t) {
				System.err.println("ThreadedPage.run(): cannot delete from multipartrequest: " + t + '(' + t.getMessage() + ')'); //$NON-NLS-1$
			}
	}
	

	/**
	 * Get the number of online users for the current application.
	 * See {@link #getApp()} for this.
	 * 
	 * @return the number of online users (active sessions)
	 */
	protected int getOnlineUsers() {
		final int app = getApp();

		if (app > 0)
			return DBSession.getSessionsList(app, false).size();
		
		return 0;
	}
	

	/**
	 * Get the nice host name for the client that made the current request.
	 * If possible this will return the reversed DNS name, or if this is not available the plain IP address.
	 *   
	 * @return client's address
	 */
	public final String getHostName() {
		String hostName = ""; //$NON-NLS-1$
		try {
			final InetAddress host = InetAddress.getByName(this.request.getRemoteAddr());
			
			hostName = host.getHostName();
			if (hostName == null)
				hostName = this.request.getRemoteAddr();
		} catch (Throwable e) {
			hostName = this.request.getRemoteAddr();
		}

		return hostName;
	}
	
	
	/**
	 * Get the client host address (IP).
	 * 
	 * @return the client's IP address
	 */
	public final String getHostAddr(){
		return this.request.getRemoteAddr();
	}

    /**
     * Compose the full URL to the current page. Use this when you want for example to force the user
     * to first authenticate, and when this operation succeeds to return it to the page it was initially
     * trying to access. 
     * 
     * @return the URL of the current page
     * @see DBSession#setLastPage(String)
     */
    public final String getCurrentPage() {
		final Enumeration<?> e = this.request.getParameterNames();

		final StringBuffer sb = HttpUtils.getRequestURL(this.request);

		boolean bFirst = true;

		sb.append('?');

		String sParam;
		String vs[];

		while (e != null && e.hasMoreElements()) {
			sParam = (String) e.nextElement();
			vs = this.request.getParameterValues(sParam);

			for (int i = 0; i < vs.length; i++) {
				if (!bFirst)
					sb.append('&');

				sb.append(Format.encode(sParam) + '=' + Format.encode(vs[i]));

				bFirst = false;
			}
		}

		String sCurrentPage = sb.toString();

		if (sCurrentPage.endsWith("?")) //$NON-NLS-1$
			sCurrentPage = sCurrentPage.substring(0, sCurrentPage.length() - 1);

		return sCurrentPage;
	}

	
	/**
	 * Override the method provided by the Java Servlet Api to log messages as DEBUG
	 * 
	 * @see javax.servlet.GenericServlet#log(java.lang.String)
	 * @see #log(int, String)
	 */
	@Override
	public void log(final String sMessage){
		log(Log.FINE, sMessage);
	}
	
	
	/**
	 * Log messages with a given level of the problem. See {@link Log} for possible logging levels.
	 * 
	 * @param level problem level
	 * @param sMessage error message
	 */
	public void log(final int level, final String sMessage){
		Log.log(level, this.sZone+'.'+this.sPage, sMessage);
	}
	
	
	/**
	 * Special logging for exceptions. It can be used to log any other objects too, but if the passed object
	 * is not a Throwable instance it will simply call toString() for it and that's it.
	 * 
	 * @param level problem level
	 * @param sMessage error message
	 * @param o object to be logged, special case when the object is a Throwable instance
	 */
	public void log(final int level, final String sMessage, final Object o){
		Log.log(level, this.sZone+'.'+this.sPage, sMessage, o);
	}
	
	
	/**
	 * Find out whether or not the current page is able to log this level of debug. You should check for this
	 * function's result prior to actually logging something big or something that would require some 
	 * processing power.
	 * 
	 * @param iLevel desired logging level
	 * @return true if a message at this level would be logged, false if not
	 */
	public boolean isLoggable(final int iLevel){
		return Log.isLoggable(iLevel, this.sZone+'.'+this.sPage);
	}
	
}
