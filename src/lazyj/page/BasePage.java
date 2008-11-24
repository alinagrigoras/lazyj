package lazyj.page;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import lazyj.DBFunctions;
import lazyj.ExtProperties;
import lazyj.ExtendedServlet;
import lazyj.Log;
import lazyj.StringFactory;
import lazyj.Utils;
import lazyj.cache.GenericLastValuesCache;
import lazyj.page.tags.Checked;
import lazyj.page.tags.Cut;
import lazyj.page.tags.DDot;
import lazyj.page.tags.Dash;
import lazyj.page.tags.Date;
import lazyj.page.tags.Dot;
import lazyj.page.tags.Enc;
import lazyj.page.tags.Esc;
import lazyj.page.tags.JS;
import lazyj.page.tags.NiceDate;
import lazyj.page.tags.Size;
import lazyj.page.tags.Strip;
import lazyj.page.tags.Time;
import lazyj.page.tags.UEnc;
import lazyj.page.tags.Under;

/**
 * Base class for the HTML templates.
 * 
 * <br>
 * <br>
 * Basically this class parses a given template, looks for tags like:<br>
 * <code>&lt;&lt;:<i>tag</i> option1 option2 ...:&gt;&gt;</code><br>
 * and replaces <code><i>tag</i></code> with a supplied value, applying all 
 * formatting options that follow the tag name.<br>
 * <br>
 * Formatting options are implementations of the {@link lazyj.page.StringFormat}
 * class that were previously registered into this class via {@link #registerExactTag(String, StringFormat)} or
 * {@link #registerRegexpTag(String, StringFormat)}.<br>
 * <br>
 * Current tags and their corresponding implementing classes:<br>
 * <B>Exact tags</B> (in the order of their most-probable use):<br>
 * <ul>
 * <li>esc : {@link Esc} : HTML-escaped version of the string</li>
 * <li>enc : {@link Enc} : URL-encoded version of the string</li>
 * <li>js : {@link JS} : JS-escaped version of the string</li>
 * <li>dash : {@link Dash} : the same string with any sequence of non-alphanumeric characters replaced by a single dash</li>
 * <li>under : {@link Under} : the same string with any sequence of non-alphanumeric characters replaced by a single underscore</li>
 * <li>checked : {@link Checked} : "checked" if the value is >=1</li>
 * <li>nicedate : {@link NiceDate} : nice representation of the date only</li>
 * <li>date : {@link Date} : nicely formatted date</li>
 * <li>time : {@link Time} : show only the time from the given date</li>
 * <li>dot : {@link Dot} : display a long value as groups of 3 digits separated by dot</li>
 * <li>size : {@link Size} : consider that the value is a file size, in bytes, display it in a human readable format</li>
 * <li>uenc : {@link UEnc} : URL-decoded version of the string</li>
 * <li>res : (internal class) : the HTML template (.res file) indicated by the tag is loaded</li>
 * </ul>
 * <br>
 * <B>Regexp tags</B>:<br>
 * <ul>
 * <li> date.+ : {@link Date} : the leftover is the {@link SimpleDateFormat} formating string, with spaces replaced by underscores</li>
 * <li> cut[0-9]+ : {@link Cut} : take only the first N characters from the string, ignoring HTML tags and keeping only A,B,FONT,U and I from them</li>
 * <li> ddot[0-9]+ : {@link DDot} : display a double number with the given number of digits after the decimal point</li>
 * <li>strip([,a-z]*) : {@link Strip} : strip HTML tags from the text, optionally leaving some of them behind. See the class description for details</li>
 * </ul>
 * <br>
 * <B>Special tag</B>:<br>
 * <ul>
 * <li>db : this field will be loaded from the database on a {@link #fillFromDB(DBFunctions)} call</li>
 * </ul>
 * <br>
 * You are strongly encouraged to extend this base class and override {@link #getResDir()}, to avoid 
 * hard coding template paths everywhere in your application.<br>
 * <br>
 * Please be aware that templates are by default cached. While the code automatically detects changed files
 * on disk and reloads them, it might take a few seconds until the changes are detected. So if you are in the 
 * development phase you can use the {@link #BasePage(OutputStream, String, boolean)} with the last argument
 * to <code>false</code> to prevent the framework from caching the contents. <b>For base performance switch 
 * back to <code>true</code> as soon as you finished changing the templates.</b><br>
 * <br>
 * <b>Sample code</b> (build a small table filled with DB contents):<br>
 * <code>
 * BasePage p = new BasePage(osOut, "/path/to/general/template.res", true);<br>
 * BasePage pLine = new BasePage(null, "/path/to/a/single/line/template.res", false);<br>
 * <br>
 * DB db = new DB("SELECT id,username,lastlogin FROM users;");<br>
 * <br>
 * while (db.moveNext()){<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;pLine.fillFromDB(db);<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;p.append(pLine);<br>
 * }<br>
 * <br>
 * p.write();<br>
 * </code>
 * <br>
 * <code>pLine</code> could point to a file containing something like:<br>
 * <code>
 * &lt;tr&gt;<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;td&gt;&lt;&lt;:id db:&gt;&gt;&lt;/td&gt;<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;td&gt;&lt;&lt;:username db esc:&gt;&gt;&lt;/td&gt;<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;td&gt;&lt;&lt;:lastlogin db nicedate:&gt;&gt; &lt;&lt;:lastlogin db time:&gt;&gt;&lt;/td&gt;<br>
 * &lt;/tr&gt;<br>
 * </code>
 * 
 * @author costing
 * @since 2006-10-06
 */
public class BasePage implements TemplatePage {
	/**
	 * Output stream, to write the generated HTML to.
	 */
	protected OutputStream	os			= null;

	/**
	 * For writing to a Writer instead of a OutputStream (from a JSP page for example).
	 */
	protected Writer writer = null;
	
	/**
	 * Original file that was used to read the contents from.
	 */
	public String			sFile		= null;

	/**
	 * Cache for the precompiled templates parser instances.
	 */
	private static final GenericLastValuesCache<String, TemplateParser> cache = new GenericLastValuesCache<String, TemplateParser>(){
		private static final long	serialVersionUID	= 1L;

		@Override
		protected int getMaximumSize(){
			// no limit to the number of templates that are kept in the cache
			return -1;
		}
		
		@Override
		protected TemplateParser resolve(final String sFile) {
			if (sFile.length()==0){
				return new TemplateParser("<<:content:>>");
			}
			
			return loadParser(sFile, true);
		}
		
	};
	
	/**
	 * Create a template object based on a template file, with the option of caching the generated entry.
	 * 
	 * @param sFile
	 * @param bCache
	 * @return the parser object
	 */
	static TemplateParser loadParser(final String sFile, final boolean bCache){
		try{
			final TemplateParser tp = new TemplateParser(sFile, bCache);
			
			if (tp.isOk()){
				return tp;
			}
			
			Log.log(Log.WARNING, "lazyj.page.BasePage", "could not correctly parse '"+sFile+"'");
		}
		catch (Exception e){
			Log.log(Log.WARNING, "lazyj.page.BasePage", "could not load '"+sFile+"' because ", e);
		}
		
		return null;
	}
	
	/**
	 * This method should be overriden by an object internal to each zone, that knows where
	 * all the HTML templates for that zone reside. The base implementation returns the empty
	 * string, making it usable if you want to give the full path to a template file.
	 * 
	 * @return the full path to the base folder where the templates can be found
	 */
	protected String getResDir(){
		return "";
	}

	/**
	 * Testing code
	 * 
	 * @param args ignored
	 */
	public static void main(final String args[]){
		System.out.println(getFormattingClass("stripBR,P").getClass().getCanonicalName());
		
		final TemplateParser tp = new TemplateParser("some <<:res.res res:>> text with <<:tag1 esc:>> and <<:tag2 enc:>> <<:strip stripBR:>> tags <<:com_start:>> this <<:tag1:>> section should not <<:tag2:>> be visible <<:com_end:>>. <<:number size:>> asdds");
		
		final HashMap<String, StringBuilder> m = new HashMap<String, StringBuilder>();
		m.put("tag1", new StringBuilder("html escape : <>&"));
		m.put("tag2", new StringBuilder("url encode: <>&"));
		m.put("number", new StringBuilder("123456"));
		m.put("strip", new StringBuilder("aaa<BR>bbb<A>cccc"));
		
		final Set<String> s = new HashSet<String>();
		s.add("com");
		
		System.out.println(tp.process(m, s, null));
	}
	
	/**
	 * Values for the tags
	 */
	public final Map<String, StringBuilder>	mValues				= new HashMap<String, StringBuilder>();
	
	/**
	 * What are the sections that are commented out?
	 * @see #comment(String, boolean)
	 */
	public final Set<String> sComments 							= new HashSet<String>(8);

	private TemplateParser tp;
	
	/**
	 * Create an empty holder to whatever. It is equivalent to a file that only has:<br>
	 * &lt;&lt;:content:&gt;&gt;
	 */
	public BasePage() {
		this.tp = cache.get("");

	}

	private BasePage(final TemplateParser parser){
		this.tp = parser;
	}
	
	/**
	 * Create a page by reading the contents of the given file.
	 * 
	 * @param sTemplateFile file to read.
	 */
	public BasePage(final String sTemplateFile) {
		this(null, sTemplateFile);
	}
	
	/**
	 * Create a page by reading the contents of the given file. When calling {@link #write()} later on this 
	 * object, the generated contents will be written to the given OutputStream, if this is not null.
	 * 
	 * @param osOut stream to write the generated HTML to at the end 
	 * @param sTemplateFile file name to read the template from
	 */
	public BasePage(final OutputStream osOut, final String sTemplateFile) {
		this(osOut, sTemplateFile, true);
	}

	/**
	 * Create a page by reading the contents of the given file. When calling {@link #write()} later on this 
	 * object, the generated contents will be written to the given OutputStream, if this is not null. With
	 * this constructor you can control whether or not the contents of this file is kept precompiled in memory.
	 * The default behavior of the simpler constructors is to cache everything. This is generally a good idea,
	 * since the original files are anyway monitored for changes and the contents is automatically reloaded if
	 * the underlying file is changed (1 minute between checks).
	 * 
	 * @param osOut stream to write the generated HTML to at the end 
	 * @param sTemplateFile file name to read the template from
	 * @param bCached controls the caching of this file
	 */
	public BasePage(final OutputStream osOut, final String sTemplateFile, final boolean bCached) {
		this.sFile = getResDir();
		
		if (!this.sFile.endsWith(File.separator) && !sTemplateFile.startsWith(File.separator))
			this.sFile += File.separator;
		
		this.sFile += sTemplateFile;

		if (bCached){
			this.tp = cache.get(this.sFile);
		}
		else{
			this.tp = loadParser(this.sFile, false);
		}

		if (Log.isLoggable(Log.FINER, "lazyj.page.BasePage"))
			Log.log(Log.FINER, "lazyj.page.BasePage", "parser is : ", this.tp);
		
		setOutputStream(osOut);
	}

	/**
	 * If at a later time the user (programmer) decides that this object is to write itself into an output stream it can call this method.
	 * This is normally done via one of the constructors that have the OutputStream parameter.
	 * 
	 * @param osOut output stream to write the contents to
	 */
	public void setOutputStream(final OutputStream osOut) {
		this.os = osOut;
	}

	/**
	 * If you want to write directly to a writer (for example from a JSP).
	 * 
	 * @param writer
	 */
	public void setWriter(final Writer writer){
		this.writer = writer;
	}
	
	/**
	 * Assign a value to a tag, only if this tag wasn't assigned at a previous time. In other words only the first assignment to a tag is considered.
	 * 
	 * @param sTag tag to assign a value to
	 * @param oValue the value to assign. If <code>null</code> then the empty string will be put in the map.
	 * @see #append(String, Object, boolean)
	 */
	public void modify(final String sTag, final Object oValue) {
		if (this.mValues.get(sTag) == null) {
			append(sTag, oValue!=null ? oValue : "", false);
		}
	}

	/**
	 * Set a bulk of tags in one move. 
	 * 
	 * @param m the &lt;tag, value&gt; mapping
	 */
	public void modify(final Map<?,?> m) {
		if (m == null || m.size()==0)
			return;
		
		for (Map.Entry<?,?> me: m.entrySet()){
			modify(me.getKey().toString(), me.getValue());
		}
	}
	
	/**
	 * Append a value to the default tag, "content"
	 * 
	 * @param oValue value to be appended to the default tag.
	 */
	public void append(final Object oValue) {
		append("content", oValue, false);
	}

	/**
	 * Append a value to a tag.
	 * 
	 * @param sTag tag name
	 * @param oValue value to append
	 */
	public void append(final String sTag, final Object oValue) {
		append(sTag, oValue, false);
	}

	/**
	 * Append a value to a tag, at the start or at the end.
	 * 
	 * @param sTagName tag name
	 * @param oValue value to append
	 * @param bBeginning true to add at the start, false to add at the end
	 */
	public void append(final String sTagName, final Object oValue, final boolean bBeginning) {
		if (sTagName==null)
			throw new InvalidParameterException("Tag cannot be null");
		
		if (oValue==null)
			return;

		final String sTag = StringFactory.get(sTagName);
		
		StringBuilder sb = this.mValues.get(sTag);
		
		final CharSequence value;
		
		if (oValue instanceof String){
			value = (String) oValue;
		}
		else
		if (oValue instanceof Page){
			final Page p = (Page) oValue;
				
			if (this.callingServlet!=null && (p instanceof BasePage))
				((BasePage)p).setCallingServlet(this.callingServlet);
				
			value = p.getContents();
		}
		else
		if (oValue instanceof StringBuilder){
			value = (StringBuilder) oValue;
		}
		else{
			value = oValue.toString();
		}
		
		if (sb==null){
			sb = new StringBuilder(value);
			this.mValues.put(sTag, sb);
		}
		else{
			if (bBeginning)
				sb.insert(0, value);
			else
				sb.append(value);
		}
	}

	/**
	 * Apply the dynamic values to the initial template to obtain a StringBuilder representation of the final data.
	 * 
	 * @return a StringBuilder with the final contents
	 */
	public StringBuilder getContents(){
		if (this.tp==null)
			return new StringBuilder();
		
		final StringBuilder sb = this.tp.process(this.mValues, this.sComments, this.callingServlet);
		
		reset();
		
		return sb;
	}
	
	/**
	 * Override the default toString() to obtain the dynamic data applied over the original template.
	 * @return the final form of the template + dynamic data
	 */
	@Override
	public String toString() {
		return getContents().toString();
	}

	/**
	 * Try to send the generated content to the given output stream, assuming UTF-8 charset.
	 * If both the output stream and the writer are set, send the result to both of them.
	 * 
	 * @return true if everything was OK, false on any error or if no output medium was defined.
	 */
	public boolean write() {
		if (this.os==null && this.writer==null)
			return false;
		
		final String sOutput = toString();
		
		if (this.os!=null){
			try {
				final PrintWriter pwOut = new PrintWriter(new OutputStreamWriter(this.os, "UTF-8"));
				pwOut.print(sOutput);
				pwOut.flush();
				return true;
			} catch (IOException e) {
				return false;
			}
		}
		
		if (this.writer!=null){
			try{
				this.writer.write(sOutput);
				this.writer.flush();
			}
			catch (IOException e){
				return false;
			}
		}

		return true;
	}
	
	/**
	 * Cancel any changes done to the template. You should use this when, in the code, you realize that
	 * the previous calls to {@link #modify(String, Object)}, {@link #append(String, Object)} etc are not
	 * going to be used and you have to prepare the object for the next iteration.
	 * 
	 * @since 1.0.5
	 */
	public void reset(){
		this.mValues.clear();
		this.sComments.clear();		
	}

	/**
	 * Shortcut for easy hiding of pieces of html. It will act on TAGNAME_start and TAGNAME_end tags in the template.
	 * If the flag is true then the contents will be displayed. If not everything between *_start and *_end will be cut.
	 * 
	 * @param sTagName base name of the two tags
	 * @param bShow true if you want to display the contents, false if you want to hide it
	 */
	public void comment(final String sTagName, final boolean bShow) {
		final String sTag = StringFactory.get(sTagName);
		
		if (bShow) {
			this.sComments.remove(sTag);
		}
		else {
			this.sComments.add(sTag);
		}
	}

	/**
	 * For all the tags that have the "db" option attached to them try to get the columns with the same name from the database row.
	 * 
	 * @param db a database object that holds the result of a select query, having the same column names as the tags you want to fill
	 */
	public void fillFromDB(final DBFunctions db) {
		if (this.tp==null)
			return;
		
		for (String sTag : this.tp.getDBTags()){
			modify(sTag, db.gets(sTag));
		}
	}

	/**
	 * Base path for the system-wide html templates that are made available to be included in any app.
	 */
	static final String BASE_PAGE_DIR;

	static {
		final String s = Utils.getLazyjConfigFolder();

		String sDir = "/";
		
		try {
			final ExtProperties pTemp = new ExtProperties(s, "basepage");
			sDir = pTemp.gets("includes.default.dir");
		} catch (Throwable e) {
			Log.log(Log.WARNING, "lazyj.page.BasePage", "could not read properties file", e);
		}
		
		if (!sDir.endsWith("/"))
			sDir+="/";
		
		BASE_PAGE_DIR = sDir;
	}
	
	private static class InternalPage extends BasePage {
		/**
		 * Implement abstract method to return a fixed folder from which you would include templates by default
		 * @return the value of the "includes.default.dir" property value in the "basepage.properties" file that
		 * 			exists in the folder indicated by the "lazyj.config.folder" system property.
		 */
		@Override
		protected String getResDir() {
			return "";
		}

		/**
		 * Open the given html template, with caching.
		 * 
		 * @param s file name. If it doesn't start with "/" then it's assumed to be relative to the default path.
		 * @see #getResDir()
		 */
		public InternalPage(String s) {
			super(null, s.startsWith("/") ? s : BASE_PAGE_DIR+s, true);
		}
	}

	/**
	 * This class implements .res includes
	 * 
	 * @author costing
	 * @since 15.12.2006
	 */
	static final class Res implements StringFormat{

		/**
		 * Implementation of the "res" tag
		 * @param sTag tag, that is actually the file name to load
		 * @param sOption always "res'
		 * @param s value for the tag, ignored
		 * @return the contents of the file specified by the tag name
		 */
		public String format(final String sTag, final String sOption, final String s) {
			return new InternalPage(sTag).toString();
		}
		
	}
	
	/**
	 * A mapping of exact formatting tags to the corresponding formatting class.
	 */
	static final ConcurrentHashMap<String, StringFormat> exactTags = new ConcurrentHashMap<String, StringFormat>(32);
	
	/**
	 * This allows the programmer to implement new formatting options and to dynamically make them visible by registering them here.
	 * The option needs to exactly match the name given here, in lowecase.
	 * 
	 * @param sOption option for which to add formatting name 
	 * @param sf an instance of the formatting class for this option
	 */
	public static final void registerExactTag(final String sOption, final StringFormat sf){
		exactTags.put(sOption.trim().toLowerCase(Locale.getDefault()), sf);
	}

	/**
	 * A mapping of regular expressions to formatting classes.
	 */
	static final ConcurrentHashMap<Pattern, StringFormat> regexpTags = new ConcurrentHashMap<Pattern, StringFormat>(8);
	
	/**
	 * This allows the programmer to implement new formatting options and to dynamically make them visible by registering them here.
	 * The option can be in this case a dynamic value that matches some regexp pattern. For example "cutN", "ddotX" are 
	 * 
	 * @param sPattern pattern that the option matches
	 * @param sf an instance of the formatting class for this option
	 * @return true if the pattern could be correctly compiled, false if the pattern is not ok
	 */
	public static final boolean registerRegexpTag(final String sPattern, final StringFormat sf){
		try{
			final Pattern p = Pattern.compile("^"+sPattern+"$");
		
			regexpTags.put(p, sf);
			
			return true;
		}
		catch (PatternSyntaxException pse){
			return false;
		}
	}
	
	static {
		// date can have two forms, default and with a user-specified format
		final Date date = new Date();
		final Checked check = new Checked();
		final DDot ddot = new DDot();
		
		registerExactTag("check", check);
		registerExactTag("checked", check);
		registerExactTag("dash", new Dash());
		registerExactTag("date", date);
		registerExactTag("dot", new Dot());
		registerExactTag("ddot", ddot);
		registerExactTag("enc", new Enc());
		registerExactTag("esc", new Esc());
		registerExactTag("js", new JS());
		registerExactTag("nicedate", new NiceDate());
		registerExactTag("size", new Size());
		registerExactTag("time", new Time());
		registerExactTag("uenc", new UEnc());
		registerExactTag("under", new Under());
		registerExactTag("res", new Res());
		
		registerRegexpTag("date.+", date);
		registerRegexpTag("cut[0-9]+", new Cut());
		registerRegexpTag("ddot[0-9]+", new DDot());
		registerRegexpTag("strip([,a-z]*)", new Strip());
	}
	
	private static final GenericLastValuesCache<String, StringFormat> formattingClass = new GenericLastValuesCache<String, StringFormat>(){
		private static final long	serialVersionUID	= 1L;

		@Override
		protected int getMaximumSize(){
			return 200;
		}

		@Override
		protected StringFormat resolve(final String sKey) {
			for (Map.Entry<Pattern, StringFormat> me: regexpTags.entrySet()){
				final Matcher m = me.getKey().matcher(sKey);
				
				if (m.matches())
					return me.getValue();
			}
			
			return null;
		}
		
	};
	
	/**
	 * Find out what is the <u>regex</u> formatting class for a given tag option
	 * 
	 * @param sTag option name
	 * @return a StringFormat instance, or <code>null</code> if there is nothing appropriate
	 */
	static final StringFormat getFormattingClass(final String sTag){
		return formattingClass.get(StringFactory.get(sTag.toLowerCase(Locale.getDefault())));
	}
	
	/**
	 * Get the exact formatting class for this tag, if it exists
	 * 
	 * @param sTag option name
	 * @return exact tag, or <code>null</code> if there is no class associated to this
	 */
	static StringFormat getExactClass(final String sTag){
		return exactTags.get(StringFactory.get(sTag.toLowerCase(Locale.getDefault())));
	}
	
	private ExtendedServlet callingServlet = null;
	
	/**
	 * If a page contains modules that need access to the request / response this is the way to make it work.
	 * 
	 * @param servlet
	 */
	public void setCallingServlet(final ExtendedServlet servlet){
		this.callingServlet = servlet;
	}
	
	/**
	 * Clear the page templates cache
	 * @since 1.0.2
	 */
	public static void clear(){
		cache.refresh();
	}
	
	/**
	 * Create a page based on a predefined template. Use this constructor when the contents is not on disk,
	 * but be aware that in this case the template is <B>not cached</B>!
	 * 
	 * @param sTemplate
	 * @return the compiled template/page
	 */
	public static BasePage getPage(final String sTemplate){
		return new BasePage(new TemplateParser(sTemplate));
	}
}