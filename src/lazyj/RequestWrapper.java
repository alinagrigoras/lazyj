/**
 * 
 */
package lazyj;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.oreilly.servlet.MultipartRequest;

/**
 * This utility class is adapted for JSP use, offering easy access to the parameters and file uploading.
 * 
 * @author costing
 * @since Oct 16, 2007 (1.0.2)
 */
public final class RequestWrapper {

	/**
	 * The actual servlet request that we are wrapping around
	 */
	private final HttpServletRequest request;
	
	/** Object used for file uploading. See {@link #initMultipartRequest(String, int)}. */
	private MultipartRequest	mpRequest = null;
	
	/**
	 * Initialize the class with a {@link HttpServletRequest}
	 * 
	 * @param request
	 */
	public RequestWrapper(final HttpServletRequest request){
		this.request = request;
	}
	
	/**
	 * This method should be called when the servlet expects a file upload from the client. It will initialize the
	 * internal mlRequest object. If the file is too big or there is another problem uploading the file then the
	 * method will return false, if everything is ok then it will return true.
	 * 
	 * @param sTempDir temporary folder for the files that are uploaded
	 * @param iFileSizeLimit maximum size of a file
	 * @return true if the request is indeed a file upload, the file size is &lt;= iFileSizeLimit and the weather outside is fine  
	 */
	public MultipartRequest initMultipartRequest(final String sTempDir, final int iFileSizeLimit) {
		try {
			this.mpRequest = new MultipartRequest(this.request, sTempDir, iFileSizeLimit);
		} catch (IOException ioe) {
			Log.log(Log.FINE, "lazyj.RequestWrapper", "initMultipartRequest", ioe); //$NON-NLS-1$ //$NON-NLS-2$
			this.mpRequest = null;
		}
				
		return this.mpRequest;
	}
	
	/**
	 * Get all the values of a parameter.
	 * 
	 * @param sParam
	 * @return values array, possibly empty but never null
	 */
	public String[] getValues(final String sParam){
		final String[] vs = this.mpRequest!=null ? this.mpRequest.getParameterValues(sParam) : this.request.getParameterValues(sParam);
		
		if (vs!=null)
			return vs;
		
		return new String[0];
	}
	
	/**
	 * Get the value of a parameter as a string.
	 * 
	 * @param sParam the parameter name
	 * @return the string value of this parameter, never null ("" is returned in the worst case)
	 */
	public String gets(final String sParam) {
		final String sValue = this.mpRequest != null ? this.mpRequest.getParameter(sParam) : this.request.getParameter(sParam);

		try {
			return sValue != null ? new String(sValue.getBytes("ISO-8859-1"), "UTF-8") : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		} catch (UnsupportedEncodingException e) {
			return ""; //$NON-NLS-1$
		}
	}
	
	/**
	 * Get the value of a parameter as a string
	 * 
	 * @param sParam parameter name 
	 * @param sDefault default value to return in case the parameter is not defined or has no value ("")
	 * @return value
	 */
	public String gets(final String sParam, final String sDefault){
		final String sValue = gets(sParam);
		
		if (sValue.length()==0)
			return sDefault;
		
		return sValue;
	}
	
	/**
	 * Get the value of a parameter as an integer value. If there is a parsing error then return the given default value. 
	 * 
	 * @param sParam the name of the parameter
	 * @param defaultVal default value to return in case of an error
	 * @return parsed value of the parameter, or the defaultValue if parameter is missing or is not an integer value representation
	 */
	public int geti(final String sParam, final int defaultVal) {
		try {
			final String s = gets(sParam);
			if (s.length() > 0) {
				return Integer.parseInt(s);
			}
			return defaultVal;
		} catch (NumberFormatException e) {
			return defaultVal;
		}
	}

	/**
	 * Get the value of a parameter as an integer value. If there is a parsing error then the value 0 will be returned. 
	 * 
	 * @param sParam name of the parameter
	 * @return parsed value of the request parameter, or 0 if there is any error in parsing, parameter not existing etc
	 */
	public int geti(final String sParam) {
		return geti(sParam, 0);
	}
	
	/**
	 * Get the value of a parameter as a long value. If there is a parsing error then return the given default value. 
	 * 
	 * @param sParam the name of the parameter
	 * @param defaultVal default value to return in case of an error
	 * @return parsed value of the parameter, or the defaultValue if parameter is missing or is not a long value representation
	 */
	public long getl(final String sParam, final long defaultVal) {
		try {
			final String s = gets(sParam);
			if (s.length() > 0) {
				return Long.parseLong(s);
			}
			return defaultVal;
		} catch (NumberFormatException e) {
			return defaultVal;
		}
	}
	
	/**
	 * Get the value of a parameter as a long value. If there is a parsing error then the value 0 will be returned. 
	 * 
	 * @param sParam name of the parameter
	 * @return parsed value of the request parameter, or 0 if there is any error in parsing, parameter not existing etc
	 */
	public long getl(final String sParam) {
		return getl(sParam, 0);
	}
	
	/**
	 * Get the value of a parameter as a float value. If there is a parsing error then return the given default value. 
	 * 
	 * @param sParam the name of the parameter
	 * @param defaultVal default value to return in case of an error
	 * @return parsed value of the parameter, or the defaultValue if parameter is missing or is not a float value representation
	 */
	public float getf(final String sParam, final float defaultVal) {
		try {
			final String s = gets(sParam);
			if (s.length() > 0) {
				return Float.parseFloat(s);
			}
			return defaultVal;
		} catch (Exception e) {
			return defaultVal;
		}
	}
	
	/**
	 * Get the value of a parameter as a float value. If there is a parsing error then the value 0 will be returned. 
	 * 
	 * @param sParam name of the parameter
	 * @return parsed value of the request parameter, or 0 if there is any error in parsing, parameter not existing etc
	 */
	public float getf(final String sParam) {
		return getf(sParam, 0f);
	}
	
	/**
	 * Get the value of a parameter as a double value. If there is a parsing error then return the given default value. 
	 * 
	 * @param sParam the name of the parameter
	 * @param defaultVal default value to return in case of an error
	 * @return parsed value of the parameter, or the defaultValue if parameter is missing or is not a double value representation
	 */
	public double getd(final String sParam, final double defaultVal) {
		try {
			final String s = gets(sParam);
			if (s.length() > 0) {
				return Double.parseDouble(s);
			}
			return defaultVal;
		} catch (NumberFormatException e) {
			return defaultVal;
		}
	}
	
	/**
	 * Get the value of a parameter as a double value. If there is a parsing error then the value 0 will be returned. 
	 * 
	 * @param sParam name of the parameter
	 * @return parsed value of the request parameter, or 0 if there is any error in parsing, parameter not existing etc
	 */
	public double getd(final String sParam) {
		return getd(sParam, 0D);
	}
	
	/**
	 * Get the value of a specific cookie
	 * 
	 * @param sName the name of the cookie
	 * @return the value of the cookie, or "" if there was an error (cookie not existing ...)
	 */
	public String getCookie(final String sName) {
		try {
			for (Cookie c: this.request.getCookies()) {
				if (c.getName().equals(sName))
					return Format.decode(c.getValue());
			}
		}
		catch (RuntimeException re){
			// ignore
		}

		return ""; //$NON-NLS-1$
	}
	
	/**
	 * Get the boolean value
	 * 
	 * @param sName parameter name
	 * @param bDefault default value
	 * @return true/false, obviously :)
	 */
	public boolean getb(final String sName, final boolean bDefault){
		final String s = gets(sName);
		
		return Utils.stringToBool(s, bDefault);
	}
	
	/**
	 * Get the SQL-safe value of this parameter
	 * 
	 * @param sName URL parameter name
	 * @return SQL-safe value of the given URL parameter
	 */
	public String esc(final String sName){
		return Format.escSQL(gets(sName));
	}
	
	/**
	 * Make this response uncacheable by the browser / proxies on the way
	 * 
	 * @param response
	 */
	public static void setNotCache(final HttpServletResponse response){
		setCacheTimeout(response, 0);
	}
	
	/**
	 * Set the caching timeout for some generated content.
	 * 
	 * @param response the response object 
	 * @param seconds expiration time, in seconds relative to "now". A strict positive value
	 *        would enable the caching while a zero or negative one would disable the caching.
	 */
	public static void setCacheTimeout(final HttpServletResponse response, final int seconds){
		if (seconds<=0){
			// disable caching
			response.setHeader("Expires", "0"); //$NON-NLS-1$ //$NON-NLS-2$
			response.setHeader("Cache-Control", "no-cache,no-store"); //$NON-NLS-1$ //$NON-NLS-2$			
		}
		else{
			// enable caching for the given time interval
			response.setHeader("Expires", getHTTPDate(new Date(System.currentTimeMillis() + 1000*seconds))); //$NON-NLS-1$
			response.setHeader("Cache-Control", "max-age="+seconds); //$NON-NLS-1$ //$NON-NLS-2$			
		}
	}
	
	/**
	 * http-style date formatter
	 */
	private static final SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US); //$NON-NLS-1$

	static{
		httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
	}
	
	/**
	 * get the http-style formatted date
	 * 
	 * @param d
	 * @return http-style formatted date
	 */
	public static synchronized String getHTTPDate(final Date d){
		return httpDateFormat.format(d);
	}
}
