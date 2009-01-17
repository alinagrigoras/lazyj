/**
 * Various utility functions, that are commonly used by everybody.
 */
package lazyj;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import lazyj.cache.ExpirationCache;
import lazyj.page.BasePage;

/**
 * Various utility functions, that are commonly used by everybody. Currently there are:<br>
 * <ul>
 * <li>Image-related functions : {@link #resize(String, String, int, int, float, boolean)}</li>
 * <li>File archiving functions : {@link #compress(String, String, boolean)},
 * {@link #compressToZip(String, String, boolean)}</li>
 * </ul>
 * 
 * @author costing
 * @since 2006-10-04
 */
/**
 * @author costing
 * @since Jan 17, 2009
 */
public final class Utils {

	/**
	 * Have we already tried to see where the configuration is located ?
	 */
	private static boolean bLazyjConfigFolderDetermined = false;
	
	/**
	 * Configuration folder
	 */
	private static String sLazyjConfigFolder = null;
	
	/**
	 * Try to figure out where the configuration files for LazyJ are, looking, in this order, at:<br>
	 * <ul>
	 * <li>env(lazyj.config.folder)</li>
	 * <li>./config/lazyj</li>
	 * <li>./config</li>
	 * <li>.</li>
	 * <li>../config/lazyj</li>
	 * <li>../config</li>
	 * <li>..</li>
	 * <li>$HOME/.lazyj</li>
	 * <li>/etc/lazyj</li>
	 * </ul>
	 * 
	 * If the system property <i>lazyj.config.folder</i> is defined it will quicky return it. If not, it will check each of the rest folders and return the first
	 * one that contains one of the standard <i>.properties</i> files. If no file is found in any of these folders, it will return <code>null</code>.
	 * 
	 * @return configuration folder for LazyJ, or <code>null</code> if it could not be found
	 */
	public static String getLazyjConfigFolder(){
		if (bLazyjConfigFolderDetermined)
			return sLazyjConfigFolder;
		
		sLazyjConfigFolder = System.getProperty("lazyj.config.folder");
		
		if (sLazyjConfigFolder!=null){
			final File f = new File(sLazyjConfigFolder);
			
			if (f.isDirectory() && f.canRead()){
				bLazyjConfigFolderDetermined = true;
			
				return sLazyjConfigFolder;
			}
		}
	
		sLazyjConfigFolder = getConfigFolder("lazyj", new String[]{"logging.properties", "dbsessions.properties", "basepage.properties", "modules.properties"});
		
		bLazyjConfigFolderDetermined = true;
		
		return sLazyjConfigFolder;
	}
	
	/**
	 * Find out which standard path contains one of the given files
	 * 
	 * @param sAppName
	 * @param filesToSearch
	 * @return one of the standard paths, or <code>null</code> if none matches
	 */
	public static String getConfigFolder(final String sAppName, final String[] filesToSearch){
		final List<String> folders = new LinkedList<String>();
		
		try{
			File fDir = new File("."); 
			
			if (fDir.isDirectory() && fDir.canRead()){
				String sPath = fDir.getCanonicalPath();
				
				folders.add(sPath+File.separator+"config"+File.separator+sAppName);
				folders.add(sPath+File.separator+"config");
				folders.add(sPath);
			}

			fDir = new File(".."); 
			
			if (fDir.isDirectory() && fDir.canRead()){
				String sPath = fDir.getCanonicalPath();
				
				folders.add(sPath+File.separator+"config"+File.separator+sAppName);
				folders.add(sPath+File.separator+"config");
				folders.add(sPath);
			}
		}
		catch (IOException _){
			// ignore
		}
		
		folders.add(System.getProperty("user.home")+File.separator+"."+sAppName);
		folders.add("/etc/"+sAppName);
				
		for (String sPath: folders){
			File f = new File(sPath);
			
			if (!f.isDirectory() || !f.canRead())
				continue;
			
			for (String sFile: filesToSearch){
				f = new File(sPath, sFile);
				
				if (f.isFile() && f.canRead())
					return sPath;
			}
		}
		
		return null;
	}
	
	/**
	 * Resize an original image, saving the destination in JPEG format, with a given compression
	 * quality.<br>
	 * <br>
	 * The file is only down-scaled, never up-scaled. That is, if the original size of the file is
	 * below the given width/height parameters then the content remains the same.<br>
	 * <br>
	 * This operation will keep the aspect of the original file, resizing the entire image so that
	 * both dimensions are less or equal to the given parameters.
	 * 
	 * @param sSource
	 *            source file name
	 * @param sDest
	 *            destination file name
	 * @param width
	 *            maximum width of the destination image
	 * @param height
	 *            maximum height of the destination image
	 * @param quality
	 *            jpeg compression quality. Recommended value: 0.6f
	 * @param bDeleteOriginalFile
	 *            whether or not to delete the original file, after successfuly creating the
	 *            destination
	 * @return true if everything is ok, false on any error
	 */
	public static final boolean resize(final String sSource, final String sDest, final int width, final int height, final float quality, final boolean bDeleteOriginalFile) {
		final BufferedImage orig;

		try {
			orig = javax.imageio.ImageIO.read(new FileInputStream(sSource));
		} catch (Exception e) {
			Log.log(Log.ERROR, "lazyj.Utils", "image resize: exception decoding a compressed format from file '" + sSource + "'", e);
			return false;
		}

		final int w = orig.getWidth();
		final int h = orig.getHeight();

		BufferedImage dest;

		// Log.log("ServletExtension: resize: from "+w+"x"+h+" to "+width+"x"+height);

		if (w <= width && h <= height) {
			// Log.log("ServletExtension: using original image");
			dest = orig;
		} else {
			final double ratio = (double) w / (double) h;

			int destWidth = width;
			int destHeight = height;

			if (w * height > h * width) {
				destHeight = (int) (destWidth / ratio);
			} else {
				destWidth = (int) (destHeight * ratio);
			}

			// Log.log("ServletExtension: scalling to "+destWidth+"x"+destHeight);

			Component comp = new Component() {
				private static final long	serialVersionUID	= 1L;
			};

			Image i2 = orig.getScaledInstance(destWidth, destHeight, Image.SCALE_SMOOTH);
			MediaTracker tracker = new MediaTracker(comp);
			tracker.addImage(i2, 0);
			try {
				tracker.waitForID(0);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// Log.log("ServletExtension: i2 size : "+i2.getWidth(comp)+"x"+i2.getHeight(comp));

			dest = new BufferedImage(destWidth, destHeight, BufferedImage.TYPE_INT_RGB);
			Graphics2D big = dest.createGraphics();
			big.drawImage(i2, 0, 0, comp);

			// Log.log("ServletExtension: dest size : "+dest.getWidth()+"x"+dest.getHeight());
		}

		try{
			final Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
			
			if (it.hasNext()){
				final ImageWriter writer = it.next();
				
				final ImageOutputStream ios = new FileImageOutputStream(new File(sDest));
				
				writer.setOutput(ios);
				
				final JPEGImageWriteParam iwParam = new JPEGImageWriteParam(Locale.getDefault());
				iwParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				iwParam.setCompressionQuality(quality);
				
				writer.write(null, new IIOImage(dest, null, null), iwParam);
				
				ios.flush();
				ios.close();
			}
			else{
				return false;
			}
		}
		catch (Throwable t){
			Log.log(Log.ERROR, "lazyj.Utils", "resize: cannot write to destination file: " + sDest, t);
			return false;			
		}

		if (bDeleteOriginalFile)
			try {
				if (!(new File(sSource)).delete())
					Log.log(Log.WARNING, "lazyj.Utils", "resize: could not delete original file (" + sSource + ")");
			} catch (SecurityException se) {
				Log.log(Log.ERROR, "lazyj.Utils", "resize: security constraints prevents file deletion");
			}

		return true;
	}

	/**
	 * Compresses a file to gzip format.
	 * 
	 * @param sSource
	 *            source file. Must have at least read permissions.
	 * @param sDest
	 *            destination file. Either the file doesn't exist and the user has write permissions
	 *            on target folder, or the file exists and the user must have write permission on
	 *            the file itself.
	 * @param bDeleteSourceOnSuccess
	 *            whether or not to delete the original file when the operation is successfuly
	 *            completed.
	 * @return true if everything is ok, false on error
	 */
	public static final boolean compress(final String sSource, final String sDest, final boolean bDeleteSourceOnSuccess) {
		InputStream is = null;
		OutputStream os = null;

		try {
			os = new GZIPOutputStream(new FileOutputStream(sDest));
			is = new FileInputStream(sSource);

			final byte[] buff = new byte[1024];
			int r;

			do {
				r = is.read(buff);
				if (r > 0)
					os.write(buff, 0, r);
			} while (r > 0);

			is.close();

			os.flush();
			os.close();
		} catch (Throwable e) {
			Log.log(Log.WARNING, "lazyj.Utils", "compress : cannot compress '" + sSource + "' to '" + sDest + "'", e);
			return false;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ioe) {
					// ignore
				}
			}

			if (os != null) {
				try {
					os.close();
				} catch (IOException ioe) {
					// ignore
				}
			}
		}

		if (bDeleteSourceOnSuccess)
			try {
				if (!(new File(sSource)).delete())
					Log.log(Log.WARNING, "lazyj.Utils", "compress: could not delete original file (" + sSource + ")");
			} catch (SecurityException se) {
				Log.log(Log.ERROR, "lazyj.Utils", "compress: security constraints prevents file deletion");
			}

		return true;
	}

	/**
	 * Create a zip archive containing the given source file.
	 * 
	 * @param sSource
	 *            file to be archived
	 * @param sDest
	 *            archive name
	 * @param bDeleteSourceOnSuccess
	 *            whether or not to delete the original file after the operation is successfuly
	 *            completed
	 * @return true if everything is ok, false if there was an error
	 */
	public static final boolean compressToZip(final String sSource, final String sDest, final boolean bDeleteSourceOnSuccess) {
		ZipOutputStream os = null;
		InputStream is = null;

		try {
			os = new ZipOutputStream(new FileOutputStream(sDest));
			is = new FileInputStream(sSource);

			final byte[] buff = new byte[1024];
			int r;

			String sFileName = sSource;
			if (sFileName.indexOf("/") >= 0)
				sFileName = sFileName.substring(sFileName.lastIndexOf("/") + 1);

			os.putNextEntry(new ZipEntry(sFileName));

			while ((r = is.read(buff)) > 0)
				os.write(buff, 0, r);

			is.close();

			os.flush();
			os.closeEntry();
			os.close();
		} catch (Throwable e) {
			Log.log(Log.WARNING, "lazyj.Utils", "compressToZip : cannot compress '" + sSource + "' to '" + sDest + "' because", e);
			return false;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ioe) {
					// ignore
				}
			}

			if (os != null) {
				try {
					os.close();
				} catch (IOException ioe) {
					// ignore
				}
			}
		}

		if (bDeleteSourceOnSuccess)
			try {
				if (!(new File(sSource)).delete())
					Log.log(Log.WARNING, "lazyj.Utils", "compressToZip: could not delete original file (" + sSource + ")");
			} catch (SecurityException se) {
				Log.log(Log.ERROR, "lazyj.Utils", "compressToZip: security constraints prevents file deletion");
			}

		return true;
	}

	/**
	 * Read the contents of a text file, in UTF-8
	 * 
	 * @param sFileName
	 *            file to read
	 * @return file contents if everything is ok, null if there is an error
	 */
	public static final String readFile(final String sFileName) {
		return readFile(sFileName, "UTF-8");
	}
	
	/**
	 * Read the contents of a text file, in an arbitrary character set
	 * 
	 * @param sFileName file to read
	 * @param charSet character set
	 * @return file contents if everything is ok, null if there is an error
	 */
	public static final String readFile(final String sFileName, final String charSet) {
		final File f = new File(sFileName);
		if (!f.exists() || !f.canRead() || !f.isFile()) {
			Log.log(Log.WARNING, "lazyj.Utils", "could not read '" + sFileName + "' because : exists=" + f.exists() + ", canread=" + f.canRead() + ", isfile=" + f.isFile());

			return null;
		}

		FileInputStream fis = null;

		try {
			final long len = f.length();
			final byte b[] = new byte[(int) len];
			fis = new FileInputStream(f);
			int readLen = fis.read(b);

			if (len != readLen)
				return null;

			return new String(b, charSet);
		} catch (IOException ioe) {
			Log.log(Log.WARNING, "lazyj.Utils", "exception reading from '" + sFileName + "'", ioe);
			return null;
		} finally {
			if (fis != null)
				try {
					fis.close();
				} catch (IOException e) {
					// ignore
				}
		}
	}

	/**
	 * HTML special characters to base characters mapping
	 */
	private static final HashMap<String, String> HTML_CHAR_MAP = new HashMap<String, String>(64, 0.95f);
	
	static{
		// upper case HTML special characters
		HTML_CHAR_MAP.put("Aacute", "A");
		HTML_CHAR_MAP.put("Agrave", "A");
		HTML_CHAR_MAP.put("Acirc", "A");
		HTML_CHAR_MAP.put("Atilde", "A");
		HTML_CHAR_MAP.put("Aring", "A");
		HTML_CHAR_MAP.put("Auml", "A");
		HTML_CHAR_MAP.put("AElig", "AE");
		HTML_CHAR_MAP.put("Ccedil", "C");
		HTML_CHAR_MAP.put("Eacute", "E");
		HTML_CHAR_MAP.put("Egrave", "E");
		HTML_CHAR_MAP.put("Ecirc", "E");
		HTML_CHAR_MAP.put("Euml", "E");
		HTML_CHAR_MAP.put("Iacute", "I");
		HTML_CHAR_MAP.put("Igrave", "I");
		HTML_CHAR_MAP.put("Icirc", "I");
		HTML_CHAR_MAP.put("Iuml", "I");
		HTML_CHAR_MAP.put("Ntilde", "N");
		HTML_CHAR_MAP.put("Oacute", "O");
		HTML_CHAR_MAP.put("Ograve", "O");
		HTML_CHAR_MAP.put("Ocirc", "O");
		HTML_CHAR_MAP.put("Otilde", "O");
		HTML_CHAR_MAP.put("Ouml", "O");
		HTML_CHAR_MAP.put("Oslash", "O");
		HTML_CHAR_MAP.put("Uacute", "U");
		HTML_CHAR_MAP.put("Ugrave", "U");
		HTML_CHAR_MAP.put("Ucirc", "U");
		HTML_CHAR_MAP.put("Uuml", "U");
		HTML_CHAR_MAP.put("Yacute", "Y");

		// lower case HTML special characters
		HTML_CHAR_MAP.put("aacute", "a");
		HTML_CHAR_MAP.put("agrave", "a");
		HTML_CHAR_MAP.put("acirc", "a");
		HTML_CHAR_MAP.put("atilde", "a");
		HTML_CHAR_MAP.put("auml", "a");
		HTML_CHAR_MAP.put("aelig", "ae");
		HTML_CHAR_MAP.put("ccedil", "c");
		HTML_CHAR_MAP.put("eacute", "e");
		HTML_CHAR_MAP.put("egrave", "e");
		HTML_CHAR_MAP.put("ecirc", "e");
		HTML_CHAR_MAP.put("euml", "e");
		HTML_CHAR_MAP.put("iacute", "i");
		HTML_CHAR_MAP.put("igrave", "i");
		HTML_CHAR_MAP.put("icirc", "i");
		HTML_CHAR_MAP.put("iuml", "i");
		HTML_CHAR_MAP.put("ntilde", "n");
		HTML_CHAR_MAP.put("oacute", "o");
		HTML_CHAR_MAP.put("ograve", "o");
		HTML_CHAR_MAP.put("ocirc", "o");
		HTML_CHAR_MAP.put("otilde", "o");
		HTML_CHAR_MAP.put("ouml", "o");
		HTML_CHAR_MAP.put("oslash", "o");
		HTML_CHAR_MAP.put("uacute", "u");
		HTML_CHAR_MAP.put("ugrave", "u");
		HTML_CHAR_MAP.put("ucirc", "u");
		HTML_CHAR_MAP.put("uuml", "u");
		HTML_CHAR_MAP.put("yacute", "y");
		HTML_CHAR_MAP.put("yuml", "y");
	}
	
	/**
	 * Convert a text with special characters into the same text but with the base characters instead
	 * of the special ones. It will also recognize special HTML characters (like &acirc;) in the input text and convert them.
	 * 
	 * @param sText original text
	 * @return base string
	 */
	public static final String toBaseCharacters(final String sText) {
		if (sText==null || sText.length()==0)
			return sText;
		
		final char[] chars = sText.toCharArray();
		
		final int iSize = chars.length;
		
		final StringBuilder sb = new StringBuilder(iSize);

		for (int i = 0; i < iSize; i++) {
			final char c = chars[i];
			
			if (c=='&'){
				final int idx = sText.indexOf(';', i+1);
				
				if (idx>0){
					final String s = sText.substring(i+1, idx);
					
					final String v = HTML_CHAR_MAP.get(s);
					
					if (v!=null){
						sb.append(v);
						i+=s.length()+1;
						continue;
					}
				}
			}
			
			// some weird quotes that are not properly converted by the normalizer
			switch (c){
				case '\u2019':
					sb.append('\'');
					continue;
				case '\u201C':
				case '\u201D':
					sb.append('"');
					continue;
				case '\u2026':
					sb.append("...");
					continue;
			}
			
			String sLetter = new String(new char[] { c });

			sLetter = Normalizer.normalize(sLetter, Normalizer.Form.NFD);

			try {
				byte[] bLetter = sLetter.getBytes("UTF-8");
				
				sb.append((char) bLetter[0]);
			} catch (UnsupportedEncodingException e) {
				// the encoding is surely valid
			}
		}
		
		return sb.toString();
	}
	
	/**
	 * Copy the file contents.
	 * 
	 * @param sSource
	 * @param sDest
	 * @return true if everything went ok, false if there was a problem
	 */
	public static final boolean copyFile(final String sSource, final String sDest){
		InputStream is = null;
		OutputStream os = null;
		
		try{
			is = new FileInputStream(sSource);
			os = new FileOutputStream(sDest);
			
			final byte[] buff = new byte[16*1024];
			int len;
			
			while ((len = is.read(buff)) > 0){
				os.write(buff, 0, len);
			}

			os.flush();
			os.close();
			os = null;
			
			is.close();
			is = null;
			
			return true;
		}
		catch (Throwable t){
			return false;
		}
		finally{
			if (is!=null){
				try{
					is.close();
				}
				catch (Exception e){
					// improbable, ignore
				}
			}
			
			if (os!=null){
				try{
					os.close();
				}
				catch (Exception e){
					// improbable, ignore
				}
			}
		}
	}
	
	
	/**
	 * Compress a byte array with GZip
	 * 
	 * @param buffer
	 * @return the compressed content or null if there was a problem
	 * @since 1.0.2
	 */
	public static byte[] compress(final byte[] buffer){
		try{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
			GZIPOutputStream gzipos = new GZIPOutputStream(baos);
		
			gzipos.write(buffer);
			gzipos.flush();
			gzipos.close();
		
			return baos.toByteArray();
		}
		catch (IOException ioe){
			// ignore, cannot happen
			return null;
		}
	}
	
	/**
	 * Uncompress a GZIP piece of content
	 * 
	 * @param buffer
	 * @return the uncompressed content, or null if there was an error
	 * @since 1.0.2
	 */
	public static byte[] uncompress(final byte[] buffer){
		try{
			GZIPInputStream gzipis = new GZIPInputStream(new ByteArrayInputStream(buffer));
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			
			byte[] tmp = new byte[1024];
			int len;
			
			while ( (len=gzipis.read(tmp))>0 ){
				baos.write(tmp, 0, len);
			}
			
			baos.flush();
			
			return baos.toByteArray();
		}
		catch (IOException ioe){
			return null;
		}
	}
	
	/**
	 * Host name resolving cache
	 */
	private static final ExpirationCache<String, String> ipCache = new ExpirationCache<String, String>(1024);	
	
	/**
	 * Calls the clear methods on all the cached structures (page cache, template cache, framework counters etc).
	 */
	public static void clearCaches(){
		PageCache.clear();
		BasePage.clear();
		FrameworkStats.clear();
		DBSession.clear();
	}
	
	/**
	 * Try to reverse a given IP address
	 * 
	 * @param ip IP address to reverse
	 * @return the reversed name or the original IP address if the reverse process is not possible
	 */
	public static String getHostName(final String ip){
		if (ip==null || ip.length()<=0)
			return ip;
		
		final String sIP = ip.toLowerCase(Locale.getDefault());
		
		final String sName = ipCache.get(sIP);
		
		if (sName!=null)
			return sName;
		
		final InetAddress addr;
		
		try{
			addr = InetAddress.getByName(sIP); 
		}
		catch (Exception e){
			ipCache.put(sIP, sIP, 1000*60*10);
			return sIP;
		}
		
		try {
			final String sTemp = addr.getCanonicalHostName().toLowerCase(Locale.getDefault());
			
			if (sTemp!=null && !sTemp.equals(sIP)){
				ipCache.put(sIP, sTemp, 1000*60*120);
				return sTemp;
			}
		}
		catch (Throwable t) {
			// ignore
		}
		
		try{
			final String sTemp = addr.getHostName().toLowerCase(Locale.getDefault());
			
			if (sTemp!=null && !sTemp.equals(sIP)){
				ipCache.put(sIP, sTemp, 1000*60*120);
				return sTemp;
			}			
		}
		catch (Throwable e){
			// ignore
		}

		ipCache.put(sIP, sIP, 1000*60*10);
		
		return sIP;
	}
	
	/**
	 * HTML comments
	 */
	private static final Pattern PATTERN_HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
	
	/**
	 * JavaScript tags
	 */
	private static final Pattern PATTERN_HTML_SCRIPT = Pattern.compile("<script.*?</script.*?>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	
	/**
	 * Style tags
	 */
	private static final Pattern PATTERN_HTML_STYLE = Pattern.compile("<style.*?</style.*?>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	
	/**
	 * HTML Head element
	 */
	private static final Pattern PATTERN_HTML_HEAD = Pattern.compile("<head.*?</head.*?>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	
	/**
	 * Many spaces as one
	 */
	private static final Pattern PATTERN_HTML_SPACES = Pattern.compile("\\s+", Pattern.DOTALL);
	
	/**
	 * What counts as new line
	 */
	private static final Pattern PATTERN_HTML_BR = Pattern.compile("<(ul|br|/li|/option|/div|table|/tr).*?>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	
	/**
	 * Any HTML tag
	 */
	private static final Pattern PATTERN_HTML_TAG = Pattern.compile("<.*?>", Pattern.DOTALL);
	
	/**
	 * What to put as bullets
	 */
	private static final Pattern PATTERN_HTML_BULL = Pattern.compile("<(li|option).*?>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	
	/**
	 * Special HTML characters 
	 */
	private static final Pattern PATTERN_HTML_SPECIAL = Pattern.compile("&.*?;", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	
	/**
	 * New lines, possibly surrounded by spaces
	 */
	private static final Pattern PATTERN_HTML_LINES = Pattern.compile("\\s*\n\\s*"); 
	
	/**
	 * Left and right trim
	 */
	private static final Pattern PATTERN_HTML_TRIM = Pattern.compile("(^\\s+)|(\\s+$)");
	
	/**
	 * Simple convertor from HTML to plain text. Can be used for example to automatically add a plain text email body part when
	 * all you have is the HTML input.
	 * 
	 * @param sHTML HTML input
	 * @return plain text version of the HTML
	 * @since 1.0.5
	 */
	public static String htmlToText(final String sHTML){
		// first, remove all the comments
		
		Matcher m = PATTERN_HTML_COMMENT.matcher(sHTML);
		String s = m.replaceAll("");
		
		m = PATTERN_HTML_SCRIPT.matcher(s);
		s = m.replaceAll("");
		
		m = PATTERN_HTML_STYLE.matcher(s);
		s = m.replaceAll("");

		m = PATTERN_HTML_HEAD.matcher(s);
		s = m.replaceAll("");

		m = PATTERN_HTML_SPACES.matcher(s);
		s = m.replaceAll(" ");
		
		m = PATTERN_HTML_BR.matcher(s);
		s = m.replaceAll("\n");
		
		m = PATTERN_HTML_BULL.matcher(s);
		s = m.replaceAll("- ");
		
		m = PATTERN_HTML_TAG.matcher(s);
		s = m.replaceAll("");

		s = s.replace("&nbsp;", " ");
		s = s.replace("&lt;", "<");
		s = s.replace("&gt;", ">");
		s = s.replace("&raquo;", ">>");
		s = s.replace("&laquo;", "<<");
		s = s.replace("&copy;", "(c)");
		s = s.replace("&amp;", "&");
		s = s.replace("&ndash;", "-");
		s = s.replace("&mdash;", "-");
		s = s.replace("&quot;", "\"");
		
		m = PATTERN_HTML_SPECIAL.matcher(s);
		s = m.replaceAll("");
		
		m = PATTERN_HTML_LINES.matcher(s);
		s = m.replaceAll("\n");		

		m = PATTERN_HTML_TRIM.matcher(s);
		s = m.replaceAll("");		

		return s;
	}
	
	/**
	 * Download a file at a remote URL to the local disk or to the memory.
	 * 
	 * @param sURL Content to download
	 * @param sFilename Local file or directory. If it's a directory then the last part of the URL (after the last "/") will be used as a file name.
	 * 					Can be <code>null</code> if you want to get back the contents directly.
	 * @return the contents of the file if the filename is <code>null</code>, or <code>null</code> if a filename was specified.
	 * @throws IOException in case of problems
	 * @since 1.0.5 (23.03.2008)
	 */
	public static String download(final String sURL, final String sFilename) throws IOException{
		final URL u = new URL(sURL);
		
		final URLConnection conn = u.openConnection();
				
		final InputStream is = conn.getInputStream();
		
		final byte[] buff = new byte[1024];
		int read;
		
		final OutputStream os;
		
		if (sFilename!=null){
			File f = new File(sFilename);
			
			if (f.exists() && f.isDirectory()){
				f = new File(f, sURL.substring(sURL.lastIndexOf("/")+1));
			}
			
			os = new FileOutputStream(f);
		}
		else{
			os = new ByteArrayOutputStream();
		}
		
		try{
			while ((read=is.read(buff))>0){
				os.write(buff, 0, read);
			}
		}
		finally{
			os.close();
		}
		
		is.close();
		
		if (os instanceof ByteArrayOutputStream)
			return new String(((ByteArrayOutputStream) os).toByteArray(), conn.getContentEncoding());
		
		return null;
	}
	
	/**
	 * Convert a String to a boolean. It will take any hint at the beginning of the string
	 * (t, T, y, Y, 1 / f, F, n, N, 0) to return <code>true</code> / <code>false</code>.  
	 * 
	 * @param s string to convert
	 * @param bDefault default value to return in case the given string doesn't show up any of the predefined signs
	 * @return the boolean value
	 */
	public static boolean stringToBool(final String s, final boolean bDefault){
		if (s!=null && s.length()>0){
			final char c = s.charAt(0);
			
			if (c=='t' || c=='T' || c=='y' || c=='Y' || c=='1') return true;
			if (c=='f' || c=='F' || c=='n' || c=='N' || c=='0') return false;
		}
		
		return bDefault;
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		getLazyjConfigFolder();
	}
}
