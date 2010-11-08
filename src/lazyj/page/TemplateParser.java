/**
 * 
 */
package lazyj.page;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Delayed;

import lazyj.DBFunctions;
import lazyj.DateFileWatchdog;
import lazyj.ExtendedServlet;
import lazyj.Log;
import lazyj.StringFactory;
import lazyj.Utils;

/**
 * This class loads a template from a file and parses it.
 * 
 * @author costing
 * @since May 23, 2007
 */
final class TemplateParser implements Observer {
	
	/**
	 * Page elements, either strings or tags
	 */
	private List<Object> llElements = null;
	
	/**
	 * Keep an eye on the underlying template file
	 */
	private DateFileWatchdog dfw;
	
	/**
	 * File name on disk
	 */
	private final String sFileName;
	
	/**
	 * How big was the previous output, to generate a large enough object from the start
	 */
	private volatile int iPrevSize;
	
	/**
	 * Database tags
	 */
	private Set<String> hsDBTags = null;
	
	/**
	 * The rest of the tags, the ones that don't have "db" flag
	 */
	private Set<String> hsNonDBTags = null;

	/**
	 * All tags
	 */
	private Set<String> hsAllTags = null;
	
	/**
	 * Is everything ok?
	 */
	private boolean bOk;
	
	/**
	 * This constructor take a file name, reads the contents from it and parses the tags into the internal structures.
	 * 
	 * @param sTemplateFileName complete file name to read the HTML template from
	 * @param bCache whether or not to cache the contents. Caching also means that the file is scheduled to be re-read from disk when it changes
	 *        (checks every one minute)
	 */
	TemplateParser(final String sTemplateFileName, final boolean bCache){
		this.sFileName = sTemplateFileName;
		
		final String sText = Utils.readFile(sTemplateFileName);
		
		if (sText==null)
			return;
		
		try{
			this.bOk = parse(sText);
		}
		catch (Throwable t){
			Log.log(Log.ERROR, "lazyj.page.TemplateParser", "Cannot parse contents because", t); //$NON-NLS-1$ //$NON-NLS-2$
			this.bOk = false;
			return;
		}
		
		if (Log.isLoggable(Log.FINEST, "lazyj.page.TemplateParser")) //$NON-NLS-1$
			Log.log(Log.FINEST, "lazyj.page.TemplateParser", "Parse result : "+this.bOk, sText); //$NON-NLS-1$ //$NON-NLS-2$
		
		if (this.bOk && bCache){
			try{
				this.dfw = new DateFileWatchdog(sTemplateFileName, 1*60*1000);
				this.dfw.addObserver(this);
			}
			catch (Exception e){
				this.dfw = null;
			}
		}
		else{
			this.dfw = null;
		}
	}
	
	/**
	 * This is a simpler constructor, that takes the given template and parses it, with no file operations.
	 * 
	 * @param sTemplate html template to parse
	 */
	TemplateParser(final String sTemplate){
		try{
			this.bOk = parse(sTemplate);
		}
		catch (Throwable t){
			Log.log(Log.ERROR, "lazyj.page.TemplateParser", "Cannot parse contents because", t); //$NON-NLS-1$ //$NON-NLS-2$
			this.bOk = false;
		}
		
		this.sFileName = null;
	}
	
	/**
	 * @return true if everything went ok with the parsing
	 */
	boolean isOk(){
		return this.bOk;
	}
	
	/**
	 * Parse this text
	 * 
	 * @param sText
	 * @return true if parsing was ok
	 */
	private boolean parse(final String sText){
		if (sText == null)
			return false;
		
		final int defaultDBTags = this.hsDBTags!=null ? this.hsDBTags.size()+1 : 8;
		final int defaultNonDBTags = this.hsNonDBTags!=null ? this.hsNonDBTags.size()+1 : 8;
		
		final LinkedList<Object> llParseElements = new LinkedList<Object>();
		final HashSet<String> hsParseDBTags = new HashSet<String>(defaultDBTags);
		final HashSet<String> hsParseNonDBTags = new HashSet<String>(defaultNonDBTags);
		
		int i = 0;
		int iOld = 0;
		int j = 0;

		this.iPrevSize = sText.length();
		
		String sTag, sComplete;

		while ((i = sText.indexOf("<<:", iOld)) >= 0) { //$NON-NLS-1$
			if (i > iOld)
				llParseElements.add(sText.substring(iOld, i));

			j = sText.indexOf(":>>", i); //$NON-NLS-1$

			final LinkedList<Object> llTag = new LinkedList<Object>();
			
			if (j > i) {
				sTag = sText.substring(i + 3, j);

				sComplete = sTag;

				final StringTokenizer st = new StringTokenizer(sTag, " "); //$NON-NLS-1$

				sTag = st.nextToken();
				
				// hack for migration of old html templates
				if (sTag.equals("continut")) //$NON-NLS-1$
					sTag = "content"; //$NON-NLS-1$
				
				sTag = StringFactory.get(sTag);
				
				llTag.add(sTag);

				if (sTag.indexOf('.') >= 1 && sTag.indexOf('/') < 0 && !sTag.endsWith(".res")) { //$NON-NLS-1$
					final int iSpaceIdx = sComplete.indexOf(' '); 
					
					if (iSpaceIdx >= 0)
						sComplete = sComplete.substring(iSpaceIdx + 1).trim();
					else
						sComplete = ""; //$NON-NLS-1$

					llTag.add(sComplete);					
				}
				
				boolean bIsDBTag = false;

				while (st.hasMoreTokens()){
					final String sOpt = StringFactory.get(st.nextToken().trim());
					
					if (sOpt.length()>0){
						if (sOpt.equals("db")){ //$NON-NLS-1$
							bIsDBTag = true;
						}
						else{
							final StringFormat sf = BasePage.getExactClass(sOpt);
							
							llTag.add(sf==null ? sOpt : sf);
						}
					}
				}
				
				if (bIsDBTag)
					hsParseDBTags.add(sTag);
				else
					hsParseNonDBTags.add(sTag);

				llParseElements.add(llTag);
				
				i = j + 3; // sarim peste tagul acesta
			}
			else { // nu se inchide ultimul tag, punem tot ce a mai ramas
				llParseElements.add(sText.substring(i));
				i = sText.length();
				iOld = i;
				break;
			}

			iOld = i;
		}

		if (iOld == 0)
			llParseElements.add(sText);
		else if (iOld < sText.length())
			llParseElements.add(sText.substring(iOld));
		
		final HashSet<String> hsParseAllTags = new HashSet<String>(hsParseNonDBTags);
		hsParseAllTags.addAll(hsParseDBTags);
		
		// first make all collections read-only
		final List<Object> llElementsTemp = Collections.unmodifiableList(llParseElements);
		final Set<String> hsDBTagsTemp = Collections.unmodifiableSet(hsParseDBTags);
		final Set<String> hsNonDBTagsTemp = Collections.unmodifiableSet(hsParseNonDBTags);
		final Set<String> hsAllTagsTemp = Collections.unmodifiableSet(hsParseAllTags); 
		
		// and then set the object fields to the read-only instances
		this.llElements = llElementsTemp;
		this.hsDBTags = hsDBTagsTemp; 
		this.hsNonDBTags = hsNonDBTagsTemp;
		this.hsAllTags = hsAllTagsTemp;
		
		return true;
	}
	
	/**
	 * Implementation of {@link Delayed}. This is called by the {@link DateFileWatchdog} when the file that holds this template has changed.
	 * The contents of the file is read and parsed again.
	 * 
	 * @param o ignored
	 * @param arg ignored
	 */
	public void update(final Observable o, final Object arg) {
		if (!parse(Utils.readFile(this.sFileName)) && this.dfw!=null){
			this.dfw.stopIt();
			this.dfw = null;
		}
	} 
	
	/**
	 * Get the list of parsed tags.
	 */
	@Override
	public String toString(){
		return this.llElements!=null ? this.llElements.toString() : "null"; //$NON-NLS-1$
	}
	
	/**
	 * Get the set of tag names that have the "db" option attached to them. This is to be used at {@link BasePage#fillFromDB(DBFunctions)}.
	 * 
	 * @return the set of tag names with the "db" option
	 */
	Set<String> getDBTags(){
		return this.hsDBTags;
	}
	
	/**
	 * Get the set of tag names that don't have "db" option attached to them.
	 * 
	 * @return set of non-"db" tags
	 */
	Set<String> getNonDBTags(){
		return this.hsNonDBTags;
	}
	
	/**
	 * Get all the distinct tags present in this template
	 * 
	 * @return set of tags in this template
	 */
	Set<String> getTagsSet(){
		return this.hsAllTags;
	}
	
	/**
	 * Apply the set of dynamic values to the HTML template. 
	 * 
	 * @param mValues user specified <tag name, value> pairs
	 * @param sComments commented out sections
	 * @param callingServlet servlet that created the page, probably null unless {@link BasePage#setCallingServlet(ExtendedServlet)} is called
	 * @return the string to be passed to the client
	 */
	@SuppressWarnings({ "unchecked", "null" })
	StringBuilder process(final Map<String, StringBuilder> mValues, final Set<String> sComments, final ExtendedServlet callingServlet){
		final StringBuilder sb = new StringBuilder(this.iPrevSize);
		
		if (this.llElements==null || this.llElements.size()==0)
			return sb;
		
		final Iterator<Object> itElements = this.llElements.iterator();
		
		final boolean bComments = sComments!=null && sComments.size()>0;
		
		while (itElements.hasNext()){
			Object o = itElements.next();
			
			if (o instanceof String)
				sb.append((String) o);
			else
			if (o instanceof StringBuilder)
				sb.append((StringBuilder) o);
			else
			if (o instanceof List){
				final Iterator<String> itTag = ((List<String>) o).iterator();
				
				final String sTag = itTag.next();
				
				if (bComments && sTag.endsWith("_start") && sComments.contains(sTag.substring(0, sTag.lastIndexOf('_')))){ //$NON-NLS-1$
					// skip over a commented out section
					final String sSearch = sTag.substring(0, sTag.lastIndexOf('_'))+"_end"; //$NON-NLS-1$
					
					while (itElements.hasNext()){
						o = itElements.next();
						
						if (o instanceof List && ((List<?>)o).get(0).equals(sSearch)){
							break;
						}
					}
					
					continue;
				}
				
				final boolean bRes = sTag.endsWith(".res");  //$NON-NLS-1$
				
				// if the tag looks like a module, the second element in the list is the complete parameter string
				// so if we consider that it's not a module then we have to skip that parameter and evaluate the rest
				boolean bSkipSecond = false;
				
				if (sTag.indexOf('.') >= 1 && sTag.indexOf('/') < 0 && !bRes) {					
					if (mValues.containsKey(sTag)){
						bSkipSecond = true;
					}
					else{
						Module cp = null;
						Throwable ex = null;
						
						try{
							cp = (Module) Class.forName(sTag).newInstance();
						}
						catch (Throwable t){
							ex = t;
						}
						
						if (cp==null && callingServlet!=null){
							final ClassLoader loader = callingServlet.getClass().getClassLoader();
							
							try{
								cp = (Module) Class.forName(sTag, true, loader).newInstance();
							}
							catch (Throwable t){
								ex = t;
							}
						}
						
						if (cp!=null)
							sb.append(cp.getContent(itTag.next()).toString());
						else
						if (ex!=null)
							Log.log(Log.ERROR, "lazyj.page.TemplateParser", "cannot instantiate module '"+sTag+"' from page='"+this.sFileName+"'", ex); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						
						continue;
					}
				}

				final StringBuilder sbValue = bRes ? new StringBuilder() : mValues.get(sTag);
				
				if (sbValue==null)
					continue;
				
				String sValue = sbValue.toString();
				
				while (itTag.hasNext() && sValue!=null){
					final Object oNext = itTag.next();
					
					if (bSkipSecond){
						// ignore the full list of parameters
						bSkipSecond = false;
						continue;
					}
					
					if (oNext instanceof StringFormat){
						sValue = ((StringFormat) oNext).format(sTag, null, sValue);
						continue;
					}
					
					final String sFormat = (String) oNext;
					
					final StringFormat sf = BasePage.getFormattingClass(sFormat);
					
					if (sf!=null)
						sValue = sf.format(sTag, sFormat, sValue);
					else
						Log.log(Log.WARNING, "lazyj.page.TemplateParser", "Unknown format option : '"+sFormat+"' (tag='"+sTag+"', page='"+this.sFileName+"')");  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				}
				
				if (sValue!=null)
					sb.append(sValue);
			}
		}
		
		// limit the amount of memory we would allocate next time
		this.iPrevSize = Math.min(sb.length(), 100000);
		
		return sb;
	}
	
}