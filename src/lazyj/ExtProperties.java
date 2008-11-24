/**
 * Utility class to process enhanced .properties file.
 */
package lazyj;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Utility class to process enhanced .properties file.<br>
 * <br>
 * Such a file can have a special "include" key, which is a list of comma-separated file names 
 * (without the .properties extension) relative to the same base folder.<br>
 * <br>
 * Each value can contain constructions like:
 * <ul>
 * 	<li> <code>${other_key}</code> , to include the value of another key in that place </li>
 * </ul>
 * This object can monitor an underlying file for modifications and automatically reload the contents 
 * of the file when it detects changes.
 *  
 * @author costing
 * @since 2006-09-29
 */
public final class ExtProperties extends Observable implements Observer {
	/** The original contents */
	private Properties prop = null;
	
	/** Cached key lookups */
	private HashMap<String, String> hmCached = null;
	
	/** Remember the file name */
	private String sConfigFile = null;
		
	/** Constructor values, for reloading */
	private String sConfDir, sFileName;
	
	private ExtProperties pSuper;
	
	private HashMap<String, String> hmExtraSet = null;
	
	private boolean bReadOnly = false;
	
	/**
	 * Default constructor, creates a configuration dictionary that has no file backing.
	 */
	public ExtProperties(){
		this.prop = new Properties();
	}
	
	/**
	 * Load the configuration from an InputStream (such as the one returned by {@link ClassLoader#getResourceAsStream(String)}
	 * 
	 * @param is input stream to read from
	 * @throws IOException in case of problems while reading from the stream
	 */
	public ExtProperties(final InputStream is) throws IOException{
		this.prop = new Properties();
		this.prop.load(is);
	}
	
	/**
	 * Load the contents of a .properties file from the sConfDir path.
	 * 
	 * @param sBaseConfigDir base folder for the configuration files 
	 * @param sConfigFilename file to load, without the ".properties" extension, that will be automatically added
	 */ 	
	public ExtProperties(final String sBaseConfigDir, final String sConfigFilename){
		this(sBaseConfigDir, sConfigFilename, null);
	}
	
	/**
	 * Load the contents of a .properties file from the sConfDir path.
	 * If the pSuper parameter is not null then the values from the given ExtProperties are inherited.
	 * Any key defined in this class will override any inherited key.
	 * 
	 * @param sBaseConfigDir base folder for all the configuration files 
	 * @param sConfigFilename file to load (without the ".properties" extension)
	 * @param superProperties other configuration to use as default (the keys from the current file will override the ones given as defaults)
	 */ 
	public ExtProperties(final String sBaseConfigDir, final String sConfigFilename, final ExtProperties superProperties){
		this.sConfDir = sBaseConfigDir;
		this.sFileName = sConfigFilename;
		this.pSuper = superProperties;
		
		reload();
	}
	
	private DateFileWatchdog dfw = null;
	
	/**
	 * Make this object check for changes in the base file and reload the contents when it changes.
	 * 
	 * @param lReload
	 */
	public synchronized void setAutoReload(final long lReload){
		if (lReload>0 && this.dfw==null){
			try{
				this.dfw = new DateFileWatchdog(this.sConfigFile, lReload);
				this.dfw.addObserver(this);
			}
			catch (Throwable t){
				this.dfw = null;
			}
		}
		
		if (lReload<=0 && this.dfw!=null){
			this.dfw.stopIt();
		}
	}
	
	/**
	 * Get the name of the file that was loaded. It will return the full path to the file name, eg. the
	 * base folder + file name + ".properties".
	 * 
	 * @return the file name that was loaded 
	 */
	public String getConfigFileName(){
		return this.sConfigFile;
	}
	
	/**
	 * Re-read the same configuration file. Also clears the cache of parsed keys.
	 */
	public void reload(){
		this.prop = load(this.sConfDir, this.sFileName, this.pSuper, new HashSet<String>());
		
		this.hmCached = null;
		
		if (this.hmExtraSet!=null)
			this.prop.putAll(this.hmExtraSet);
	}
	
	/**
	 * Make this dictionary read-only, to prevent writes from other pieces of code that see this object.
	 */
	public void makeReadOnly(){
		this.bReadOnly = true;
	}
	
	/**
	 * Recursive function to actually load the contents of the given .properties file.
	 * Iterates through all the "include" keys and recursively loads them.
	 * 
	 * @param sBaseConfDir   the base folder for all .properties file
	 * @param sConfigFilename  file name without the .properties extension, can contain folder names
	 * @param superProperties     default values
	 * @param hsIncluded previously included files, to not include them again
	 * @return           the final Properties
	 */
	private Properties load(final String sBaseConfDir, final String sConfigFilename, final ExtProperties superProperties, final HashSet<String> hsIncluded){
		Properties propLoader = superProperties!=null ? new Properties(superProperties.prop) : new Properties();
		
		FileInputStream fis = null;
		
		try{
			this.sConfigFile = sBaseConfDir+(sBaseConfDir.endsWith(File.separator) ? "" : File.separator) + sConfigFilename+".properties";
			
			fis = new FileInputStream(this.sConfigFile);
			
			propLoader.load(fis);
		}
		catch (IOException ioe){
			Log.log(Log.WARNING, "lazyj.ExtProperties", "cannot load '"+this.sConfigFile+"'", ioe);
			
			this.sConfigFile = null;
			
			return propLoader;
		}
		finally{
			if (fis!=null){
				try{
					fis.close();
				}
				catch (IOException ioe2){
					// ignore
				}
			}
		}
		
		final String sInclude = propLoader.getProperty("include");
		
		if (sInclude!=null && sInclude.length()>0){
			final StringTokenizer st = new StringTokenizer(sInclude, ";, \t");
			
			while (st.hasMoreTokens()){
				final String sIncludeFile = st.nextToken();
				
				if (!hsIncluded.contains(sIncludeFile)){
					hsIncluded.add(sIncludeFile);
					
					final Properties pTemp = load(sBaseConfDir, sIncludeFile, null, hsIncluded);
					
					pTemp.putAll(propLoader);
					
					propLoader = pTemp;
				}
			}
		}
		
		return propLoader;
	}
	
	/**
	 * Parse a value to include other keys
	 * 
	 * @param sKey the key that is parsed
	 * @param sValue original value
	 * @param sDefault default value, in case of an error in parsing
	 * @param bProcessQueries whether or not to execute database queries / cache lookups
	 * @return the processed value
	 */
	public String parseOption(final String sKey, final String sValue, final String sDefault, final boolean bProcessQueries) {
		int i = 0;

		StringBuffer sbVal = new StringBuffer();
		
		String sVal = sValue;
		
		// see if there are any other keys' values to include
		while ((i = sVal.indexOf("${")) >= 0) {
			final int i2 = sVal.indexOf("}", i);

			if (i2 > 0) {
				final String s = sVal.substring(i + 2, i2);

				if (s.equals(sKey))
					return sDefault;
				
				sbVal.append(sVal.substring(0, i));
				sbVal.append(gets(s, "", bProcessQueries));

				sVal = sVal.substring(i2 + 1);
			} else
				break;
		}
		
		if (sbVal.length()>0){
			// some processing happened here
			if (sVal.length()>0)
				sbVal.append(sVal);
		
			sVal = sbVal.toString();
		}

		return sVal;
	}

	/**
	 * Get the String value for a given key. If the key is not defined then the empty string is returned.
	 * Queries will be processed. 
	 * 
	 * @param sKey the key to get the value for
	 * @return the value
	 * 
	 * @see #gets(String, String, boolean)
	 */
	public String gets(final String sKey){
		return gets(sKey, "");
	}
	
	/**
	 * Get the String value for a given key, returning the given default value if the key is not defined.
	 * Queries will be processed.
	 * 
	 * @param sKey the key to get the value for
	 * @param sDefault default value to return in case the key is not defined
	 * @return the value
	 * 
	 * @see #gets(String, String, boolean)
	 */
	public String gets(final String sKey, final String sDefault){
		return gets(sKey, sDefault, true);
	}
	
	/**
	 * Get the String value for a given key, returning the given default value if the key is not defined.
	 * The value that is returned is also stored in a cache so that future requests to the same key
	 * will return the value from the cache. This also means that if the key is not defined then the
	 * given default value will be cached and returned the next time this function is called. <code>null</code>
	 * values are not cached.
	 * 
	 * @param sKey the key to get the value for
	 * @param sDefault default value to return in case the key is not defined. 
	 * @param bProcessQueries flag to process or not process the database/memory cache queries
	 * @return value for this key
	 */
	public String gets(final String sKey, final String sDefault, final boolean bProcessQueries){
		if (sKey==null)
			return sDefault;
		
		if (this.hmCached!=null){
			final String sValue = this.hmCached.get(sKey);
			
			if (sValue!=null)
				return sValue;
		}
		
		String sReturn = sDefault;
		
		if (this.prop.getProperty(sKey) != null) {
			final String sVal = this.prop.getProperty(sKey).trim();

			sReturn = parseOption(sKey, sVal, sDefault, bProcessQueries);
		}

		if (sReturn!=null){
			if (this.hmCached==null)
				this.hmCached = new HashMap<String, String>();
		
			this.hmCached.put(sKey, sReturn);
		}
				
		return sReturn;
	}
	
	/**
	 * Parse an option to return the boolean value.
	 * It returns true if the value starts with t,T,y,Y or 1 and false if the value starts with f,F,n,N or 0.
	 * In any other case it returns the given default value. 
	 * 
	 * @param sKey the key to get the value for
	 * @param bDefault default value
	 * @return a boolean
	 */
	public boolean getb(final String sKey, final boolean bDefault) {
		final String s = gets(sKey, ""+bDefault);
		
		if (s.length()>0){
			final char c = s.charAt(0);
			
			if (c=='t' || c=='T' || c=='y' || c=='Y' || c=='1')
				return true;
			
			if (c=='f' || c=='F' || c=='n' || c=='N' || c=='0')
				return false;
		}
		
		return bDefault;
	}
	
	/**
	 * Get the integer value for a key. Returns the given default value if the key is not defined or the value
	 * is not an integer reprezentation.
	 * 
	 * @param sKey the key to get the value for
	 * @param iDefault default value
	 * @return an integer
	 */
	public int geti(final String sKey, final int iDefault) {
		try {
			return Integer.parseInt(gets(sKey, ""+iDefault));
		} catch (Exception e) {
			return iDefault;
		}
	}

	/**
	 * Get the long value for a key. Returns the given default value if the key is not defined or the value
	 * is not a long reprezentation.
	 * 
	 * @param sKey the key to get the value for
	 * @param lDefault default value
	 * @return a long
	 */
	public long getl(final String sKey, final long lDefault) {
		try {
			return Long.parseLong(gets(sKey, ""+lDefault));
		} catch (Exception e) {
			return lDefault;
		}
	}

	/**
	 * Get the double value for a key. Returns the given default value if the key is not defined or the value
	 * is not a double reprezentation.
	 * 
	 * @param sKey the key to get the value for
	 * @param dDefault default value
	 * @return a double
	 */
	public double getd(final String sKey, final double dDefault) {
		try {
			return Double.parseDouble(gets(sKey, ""+dDefault));
		} catch (Exception e) {
			return dDefault;
		}
	}
	
	/**
	 * Split a value by "," and return a Vector of String parts.
	 * 
	 * @param sKey the key to get the values for
	 * @return a Vector of String parts
	 */
	public Vector<String> toVector(final String sKey){
		final String sVal = gets(sKey);
		
		final StringTokenizer st = new StringTokenizer(sVal, ",");
		
		final Vector<String> vReturn = new Vector<String>(st.countTokens());
		
		while (st.hasMoreTokens()){
			vReturn.add(st.nextToken());
		}
		
		return vReturn;
	}
	
	/**
	 * Method to clear the cache in order to force the evaluation of the keys once again.
	 */
	public void clearCache(){
		this.hmCached.clear();
	}
	
	/**
	 * Get some debugging info for this object
	 * 
	 * @return the original keys dump + cached values
	 */
	@Override
	public String toString(){
		return "  Original properties:\n    "+(this.prop!=null ? this.prop.toString(): "null")+"\n  Cached values:\n    "+(this.hmCached!=null ? this.hmCached.toString() : "null");
	}
		
	/**
	 * Modify an entry of the dictionary.
	 * 
	 * @param sKey key to change
	 * @param sValue new value
	 */
	public void set(final String sKey, final String sValue){
		if (this.bReadOnly)
			throw new IllegalArgumentException("This object is read-only, you are not allowed to modify its contents");
		
		final String sOld = (String) this.prop.put(sKey, sValue);
		
		// save the set value in a map, to override future changes in the underlying file
		if (this.sConfigFile!=null){
			if (this.hmExtraSet==null)
				this.hmExtraSet = new HashMap<String, String>();
		
			this.hmExtraSet.put(sKey, sValue);
		}
		
		// if the value hasn't in fact changed, don't go further
		if (sValue.equals(sOld))
			return;
		
		if (this.hmCached!=null)
			this.hmCached.remove(sKey);
		
		setChanged();
		notifyObservers();
	}

	/**
	 * Get a Properties view of this object. This will force a parsing of all the defined keys.
	 * 
	 * @return a Properties view of this object
	 */
	public Properties getProperties(){
		final Properties pRet = new Properties();
		
		for (Object o: this.prop.keySet()){
			if (o instanceof String){
				final String s = (String) o;
				
				pRet.put(s, gets(s));
			}
		}
		
		return pRet;
	}

	/**
	 * Implementation of the {@link Observer} interface. Since this object can monitor the original file
	 * for changes, it will be notified by a call to this method when this happens. 
	 * @param o ignored
	 * @param arg ignored
	 */
	public void update(final Observable o, final Object arg) {
		reload();
		
		setChanged();
		notifyObservers();
	}
	
}
