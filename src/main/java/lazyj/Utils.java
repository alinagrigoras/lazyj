/**
 * Various utility functions, that are commonly used by everybody.
 */
package lazyj;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
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
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
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
@SuppressWarnings("nls")
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
		
		sLazyjConfigFolder = System.getProperty("lazyj.config.folder"); //$NON-NLS-1$
		
		if (sLazyjConfigFolder!=null){
			final File f = new File(sLazyjConfigFolder);
			
			if (f.isDirectory() && f.canRead()){
				bLazyjConfigFolderDetermined = true;
			
				return sLazyjConfigFolder;
			}
		}
	
		sLazyjConfigFolder = getConfigFolder("lazyj", new String[]{"logging.properties", "dbsessions.properties", "basepage.properties", "modules.properties"});  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		
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
		final List<String> folders = new LinkedList<>();
		
		try{
			File fDir = new File(".");  //$NON-NLS-1$
			
			if (fDir.isDirectory() && fDir.canRead()){
				final String sPath = fDir.getCanonicalPath();
				
				folders.add(sPath+File.separator+"config"+File.separator+sAppName); //$NON-NLS-1$
				folders.add(sPath+File.separator+"config"); //$NON-NLS-1$
				folders.add(sPath);
			}

			fDir = new File("..");  //$NON-NLS-1$
			
			if (fDir.isDirectory() && fDir.canRead()){
				final String sPath = fDir.getCanonicalPath();
				
				folders.add(sPath+File.separator+"config"+File.separator+sAppName); //$NON-NLS-1$
				folders.add(sPath+File.separator+"config"); //$NON-NLS-1$
				folders.add(sPath);
			}
		}
		catch (final IOException _){
			// ignore
		}
		
		folders.add(System.getProperty("user.home")+File.separator+'.'+sAppName); //$NON-NLS-1$
		folders.add("/etc/"+sAppName); //$NON-NLS-1$
				
		for (final String sPath: folders){
			File f = new File(sPath);
			
			if (!f.isDirectory() || !f.canRead())
				continue;
			
			for (final String sFile: filesToSearch){
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
		} catch (final Exception e) {
			Log.log(Log.ERROR, "lazyj.Utils", "image resize: exception decoding a compressed format from file '" + sSource + "'", e); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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

			final Component comp = new Component() {
				private static final long	serialVersionUID	= 1L;
			};

			final Image i2 = orig.getScaledInstance(destWidth, destHeight, Image.SCALE_SMOOTH);
			final MediaTracker tracker = new MediaTracker(comp);
			tracker.addImage(i2, 0);
			try {
				tracker.waitForID(0);
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}

			// Log.log("ServletExtension: i2 size : "+i2.getWidth(comp)+"x"+i2.getHeight(comp));

			dest = new BufferedImage(destWidth, destHeight, BufferedImage.TYPE_INT_RGB);
			final Graphics2D big = dest.createGraphics();
			big.drawImage(i2, 0, 0, comp);

			// Log.log("ServletExtension: dest size : "+dest.getWidth()+"x"+dest.getHeight());
		}

		try{
			final Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg"); //$NON-NLS-1$
			
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
		catch (final Throwable t){
			Log.log(Log.ERROR, "lazyj.Utils", "resize: cannot write to destination file: " + sDest, t); //$NON-NLS-1$ //$NON-NLS-2$
			return false;			
		}

		if (bDeleteOriginalFile)
			try {
				if (!(new File(sSource)).delete())
					Log.log(Log.WARNING, "lazyj.Utils", "resize: could not delete original file (" + sSource + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			} catch (final SecurityException se) {
				Log.log(Log.ERROR, "lazyj.Utils", "resize: security constraints prevents file deletion"); //$NON-NLS-1$ //$NON-NLS-2$
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
		} catch (final Throwable e) {
			Log.log(Log.WARNING, "lazyj.Utils", "compress : cannot compress '" + sSource + "' to '" + sDest + "'", e); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			return false;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (final IOException ioe) {
					// ignore
				}
			}

			if (os != null) {
				try {
					os.close();
				} catch (final IOException ioe) {
					// ignore
				}
			}
		}

		if (bDeleteSourceOnSuccess)
			try {
				if (!(new File(sSource)).delete())
					Log.log(Log.WARNING, "lazyj.Utils", "compress: could not delete original file (" + sSource + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			} catch (final SecurityException se) {
				Log.log(Log.ERROR, "lazyj.Utils", "compress: security constraints prevents file deletion"); //$NON-NLS-1$ //$NON-NLS-2$
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
			if (sFileName.indexOf('/') >= 0)
				sFileName = sFileName.substring(sFileName.lastIndexOf('/') + 1);

			os.putNextEntry(new ZipEntry(sFileName));

			while ((r = is.read(buff)) > 0)
				os.write(buff, 0, r);

			is.close();

			os.flush();
			os.closeEntry();
			os.close();
		} catch (final Throwable e) {
			Log.log(Log.WARNING, "lazyj.Utils", "compressToZip : cannot compress '" + sSource + "' to '" + sDest + "' because", e); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			return false;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (final IOException ioe) {
					// ignore
				}
			}

			if (os != null) {
				try {
					os.close();
				} catch (final IOException ioe) {
					// ignore
				}
			}
		}

		if (bDeleteSourceOnSuccess)
			try {
				if (!(new File(sSource)).delete())
					Log.log(Log.WARNING, "lazyj.Utils", "compressToZip: could not delete original file (" + sSource + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 
			} catch (final SecurityException se) {
				Log.log(Log.ERROR, "lazyj.Utils", "compressToZip: security constraints prevents file deletion"); //$NON-NLS-1$ //$NON-NLS-2$
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
		return readFile(sFileName, "UTF-8"); //$NON-NLS-1$
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
			Log.log(Log.WARNING, "lazyj.Utils", "could not read '" + sFileName + "' because : exists=" + f.exists() + ", canread=" + f.canRead() + ", isfile=" + f.isFile()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

			return null;
		}

		try (FileInputStream fis = new FileInputStream(f)){
			final long len = f.length();
			final byte b[] = new byte[(int) len];
			final int readLen = fis.read(b, 0, (int) len);

			if (readLen == 0)
				return null;

			return new String(b, 0, readLen, charSet);
		} catch (final IOException ioe) {
			Log.log(Log.WARNING, "lazyj.Utils", "exception reading from '" + sFileName + "'", ioe); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return null;
		}
	}

	/**
	 * HTML special characters to base characters mapping
	 */
	private static final HashMap<String, String> HTML_CHAR_MAP = new HashMap<>(64, 0.95f);
	
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
			
			if (c=='&' && i<iSize-1 && ((chars[i+1]>='a' && chars[i+1]<='z') || (chars[i+1]>='A' && chars[i+1]<='Z')) ){
				final int idx = sText.indexOf(';', i+1);
				
				if (idx>0 && idx-i<9){
					final String s = sText.substring(i+1, idx);
					
					final String v = HTML_CHAR_MAP.get(s);
					
					if (v!=null){
						sb.append(v);
						i+=s.length()+1;
						continue;
					}
				}
			}
			
			if (c=='&' && i<iSize-2 && chars[i+1]=='#' && (chars[i+2]>='0' && chars[i+2]<='9')){
				final int idx = sText.indexOf(';', i+1);
				
				if (idx>0 && idx-i<9){
					final String s = sText.substring(i+2, idx);
					
					try{
						final char ch = (char) Integer.parseInt(s);
						
						sb.append(ch);
					}
					catch (final NumberFormatException nfe){
						sb.append("&#").append(s).append(';');
					}
				}
			}
			
			// some weird quotes that are not properly converted by the normalizer
			switch (c){
				case 0xA9:
					sb.append("(C)");
					continue;
				case '\u2014':
					sb.append('-');
					continue;
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
				final byte[] bLetter = sLetter.getBytes("UTF-8");
				
				sb.append((char) bLetter[0]);
			} catch (final UnsupportedEncodingException e) {
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
		catch (final Throwable t){
			return false;
		}
		finally{
			if (is!=null){
				try{
					is.close();
				}
				catch (final Exception e){
					// improbable, ignore
				}
			}
			
			if (os!=null){
				try{
					os.close();
				}
				catch (final Exception e){
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
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
			final GZIPOutputStream gzipos = new GZIPOutputStream(baos);
		
			gzipos.write(buffer);
			gzipos.flush();
			gzipos.close();
		
			return baos.toByteArray();
		}
		catch (final IOException ioe){
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
			final GZIPInputStream gzipis = new GZIPInputStream(new ByteArrayInputStream(buffer));
			
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			
			final byte[] tmp = new byte[1024];
			int len;
			
			while ( (len=gzipis.read(tmp))>0 ){
				baos.write(tmp, 0, len);
			}
			
			baos.flush();
			
			return baos.toByteArray();
		}
		catch (final IOException ioe){
			return null;
		}
	}
	
	/**
	 * Host name resolving cache
	 */
	private static final ExpirationCache<String, String> ipCache = new ExpirationCache<>(1024);	
	
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
		catch (final Exception e){
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
		catch (final Throwable t) {
			// ignore
		}
		
		try{
			final String sTemp = addr.getHostName().toLowerCase(Locale.getDefault());
			
			if (sTemp!=null && !sTemp.equals(sIP)){
				ipCache.put(sIP, sTemp, 1000*60*120);
				return sTemp;
			}			
		}
		catch (final Throwable e){
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
	private static final Pattern PATTERN_HTML_SPECIAL = Pattern.compile("&[a-zA-Z]{2,6};", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	
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
		return htmlToText(sHTML, 0);
	}
	
	/**
	 * Don't collapse multiple blank characters in a single space.
	 */
	public static final int HTML_OPTION_KEEP_SPACES = 1;
	
	/**
	 * Don't collapse multiple new lines in a single new line
	 */
	public static final int HTML_OPTION_KEEP_NEWLINES = 2;
	
	/**
	 * Simple convertor from HTML to plain text. Can be used for example to automatically add a plain text email body part when
	 * all you have is the HTML input.
	 * 
	 * @param sHTML HTML input
	 * @param options a binary OR between any Utils.HTML_OPTION_* constants
	 * @return plain text version of the HTML
	 */
	public static String htmlToText(final String sHTML, final int options){
		// first, remove all the comments
		
		Matcher m = PATTERN_HTML_COMMENT.matcher(sHTML);
		String s = m.replaceAll("");
		
		m = PATTERN_HTML_SCRIPT.matcher(s);
		s = m.replaceAll("");
		
		m = PATTERN_HTML_STYLE.matcher(s);
		s = m.replaceAll("");

		m = PATTERN_HTML_HEAD.matcher(s);
		s = m.replaceAll("");

		if ( (options & HTML_OPTION_KEEP_SPACES) == 0){
			m = PATTERN_HTML_SPACES.matcher(s);
			s = m.replaceAll(" ");
		}
		
		m = PATTERN_HTML_BR.matcher(s);
		s = m.replaceAll("\n");
		
		m = PATTERN_HTML_BULL.matcher(s);
		s = m.replaceAll("- ");
		
		m = PATTERN_HTML_TAG.matcher(s);
		s = m.replaceAll("");

		if (s.indexOf("&")>=0){
			s = s.replace("&lt;", "<");
			s = s.replace("&gt;", ">");
			s = s.replace("&raquo;", ">>");
			s = s.replace("&laquo;", "<<");
			s = s.replace("&copy;", "(c)");
			s = s.replace("&amp;", "&");
			s = s.replace("&ndash;", "-");
			s = s.replace("&mdash;", "-");
			s = s.replace("&quot;", "\"");
			s = s.replace("&hellip;", "...");
			s = s.replace("&rsquo;", "'");
			s = s.replace("&lsquo;", "`");
			s = s.replace("&rdquo;", "\"");
			s = s.replace("&ldquo;", "\"");

			if (s.indexOf("&")>=0){
				for (final Map.Entry<String, String> me: HTML_CHAR_MAP.entrySet()){
					s = s.replace('&'+me.getKey()+';', me.getValue());
				}
			}
		}

		int idxStart = s.indexOf("&#");
		
		while (idxStart>0){
			final int idx = s.indexOf(';', idxStart+2);
			
			if (idx>0 && idx-idxStart<9){
				final String v = s.substring(idxStart+2, idx);
				
				try{
					final char c = (char) Integer.parseInt(v);
					
					s = Format.replace(s, "&#"+v+";", String.valueOf(c));
					
					idxStart = s.indexOf("&#");
					
					continue;
				}
				catch (final NumberFormatException nfe){
					// ignore
				}
			}

			idxStart = s.indexOf("&#", idxStart+2);
		}
		
		if ( (options & HTML_OPTION_KEEP_NEWLINES) == 0){
			m = PATTERN_HTML_LINES.matcher(s);
			s = m.replaceAll("\n");
		}

		m = PATTERN_HTML_TRIM.matcher(s);
		s = m.replaceAll("");		
		
		s = s.replace("&nbsp;", " ");

		m = PATTERN_HTML_SPECIAL.matcher(s);
		s = m.replaceAll("");

		return s;
	}
	
	/**
	 * Download a file at a remote URL to the local disk or to the memory.
	 * 
	 * @param sURL Content to download
	 * @param sFilename Local file or directory. If it's a directory then the last part of the URL (after the last "/") will be used as a file name.
	 * 					Can be <code>null</code> if you want to get back the contents directly.
	 * @return the contents of the file if the filename is <code>null</code>, or the file name if the second parameter is not null 
	 * @throws IOException in case of problems
	 * @since 1.0.5 (23.03.2008)
	 */
	public static String download(final String sURL, final String sFilename) throws IOException{
		final URL u = new URL(sURL);
		
		final URLConnection conn = u.openConnection();
				
		final InputStream is = conn.getInputStream();
		
		final byte[] buff = new byte[10240];
		int read;
		
		final OutputStream os;
		
		String actualFileName = sFilename;
		
		if (sFilename!=null){
			File f = new File(sFilename);
			
			if (f.exists() && f.isDirectory()){
				String sLastPart = sURL;
				
				int idx = sLastPart.indexOf('?'); 
				
				if (idx>=0)
					sLastPart = sLastPart.substring(0, idx);
				
				idx = sLastPart.lastIndexOf('/');
				
				if (idx>=0)
					sLastPart = sLastPart.substring(idx+1);
								
				f = new File(f, sLastPart);
				
				actualFileName = f.getCanonicalPath();
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
		
		if (os instanceof ByteArrayOutputStream){
			String encoding = conn.getContentEncoding();
			
			if (encoding==null)
				encoding = "UTF-8";
			
			final byte[] content = ((ByteArrayOutputStream) os).toByteArray();
			
			try{
				return new String(content, encoding);
			}
			catch (final UnsupportedEncodingException uee){
				return new String(content);
			}
		}
		
		return actualFileName;
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
	 * Encrypt or decrypt using Blowfish algorithm
	 * 
	 * @param input
	 * @param key
	 * @param mode
	 * @return the data
	 * @throws Exception
	 */
	private static byte[] blowfishCrypt(final byte[] input, final byte[] key, final int mode) throws Exception {
		//final Provider sunJce = new com.sun.crypto.provider.SunJCE();
		//Security.addProvider(sunJce);

		final SecretKeySpec skeySpec = new SecretKeySpec(key, "Blowfish");
       
		final Cipher cipher = Cipher.getInstance("Blowfish/ECB/PKCS5Padding");
		cipher.init(mode, skeySpec);

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		final ByteArrayInputStream bis = new ByteArrayInputStream(input);
		final CipherOutputStream cos = new CipherOutputStream(bos, cipher);

		int length = 0;
		final byte[] buffer =  new byte[1024];

		while ((length = bis.read(buffer)) != -1) {
			cos.write(buffer, 0, length);
		}

		bis.close();
		cos.close();

		return bos.toByteArray();
	}
	
	/**
	 * Encrypt some input data using Blowfish with the given key
	 * 
	 * @param input
	 * @param key
	 * @return encrypted data
	 */
	public static byte[] blowfishCrypt(final byte[] input, final byte[] key){
		try{
			return blowfishCrypt(input, key, Cipher.ENCRYPT_MODE);
		}
		catch (final Exception e){
			return null;
		}
	}
	
	/**
	 * Encrypt some input text using Blowfish with the given key
	 * 
	 * @param input
	 * @param key
	 * @return Base64-encoded encrypted text, <code>null</code> on error
	 */
	public static String blowfishCrypt(final String input, final String key){
		try{
			return base64Encode(blowfishCrypt(input.getBytes(), key.getBytes()));
		}
		catch (final Exception e){
			return null;
		}
	}
	
	/**
	 * Decrypt using Blowfish and the given key
	 * 
	 * @param input
	 * @param key
	 * @return decrypted data
	 */
	public static byte[] blowfishDecrypt(final byte[] input, final byte[] key){
		try{
			return blowfishCrypt(input, key, Cipher.DECRYPT_MODE);
		}
		catch (final Exception e){
			return null;
		}
	}
	
	/**
	 * Decrypt a Base64-encoded text using Blowfish and the given key
	 * 
	 * @param input
	 * @param key
	 * @return decoded text, or <code>null</code> on error
	 */
	public static String blowfishDecrypt(final String input, final String key){
		try{
			return new String(blowfishDecrypt(base64Decode(input), key.getBytes()));
		}
		catch (final Exception e){
			return null;
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		final String orig = "something longer to make more sense and make sure the line doesn't end at the base64 boundaries";
		
		final String key = "blabla";
		
		final byte[] input = orig.getBytes();
		
		final byte[] k = key.getBytes();
		
		final byte[] enc = blowfishCrypt(input, k);
		
		System.err.println("Original: "+input.length+", enc: "+enc.length);
		
		System.err.println(blowfishDecrypt(blowfishCrypt(orig, key), key));
		
		final String base64 = base64Encode(enc);
		System.err.println("Encoded : "+base64);
	}
	
	/**
	 * @author costing / from http://forums.sun.com/thread.jspa?threadID=477461&start=15&tstart=0
	 * @since Nov 6, 2010
	 */
	public static class Base64Coder extends AbstractPreferences {
		/**
		 * the value
		 */
		private String store;

		/**
		 * Singleton
		 */
		private static Base64Coder instance = new Base64Coder();

		/**
		 * Hide the constructor; this is a singleton.
		 */
		private Base64Coder() {
			super(null, "");
		}

		/**
		 * Given a byte array, return its Base64 representation as a String.
		 * 
		 * @param b
		 * @return encoded bytes
		 */
		public static synchronized String encode(final byte[] b) {
			final String key = String.valueOf(Thread.currentThread().getId()); 
			
			instance.putByteArray(key, b);
			final String value = instance.get(key, null);
			
			instance.remove(key);
			
			return value;
		}

		/**
		 * Given a String containing a Base64 representation, return the corresponding byte array.
		 * 
		 * @param base64String
		 * @return decoded bytes
		 */
		public static synchronized byte[] decode(final String base64String) {
			final String key = String.valueOf(Thread.currentThread().getId()); 
			
			instance.put(key, base64String);
			final byte[] value = instance.getByteArray(key, null);
			
			instance.remove(key);
			
			return value;
		}

		@Override
		public String get(final String key, final String def) {
			return this.store;
		}

		@Override
		public void put(final String key, final String value) {
			this.store = value;
		}

		// Other methods required to implement the abstract class; these methods are not used.
		@Override
		protected AbstractPreferences childSpi(final String name) {
			return null;
		}

		@Override
		protected void putSpi(final String key, final String value) {
			// ignore
		}

		@Override
		protected String getSpi(final String key) {
			return null;
		}

		@Override
		protected void removeSpi(final String key) {
			// ignore
		}

		@Override
		protected String[] keysSpi() throws BackingStoreException {
			return null;
		}

		@Override
		protected String[] childrenNamesSpi() throws BackingStoreException {
			return null;
		}

		@Override
		protected void syncSpi() throws BackingStoreException {
			// ignore
		}

		@Override
		protected void removeNodeSpi() throws BackingStoreException {
			// ignore
		}

		@Override
		protected void flushSpi() throws BackingStoreException {
			// ignore
		}
	}

	/**
	 * Encode a byte array to a single-line BASE64-encoded string.
	 * The output is ~4/3 the size of the original data.
	 * 
	 * @param b bytes to encode
	 * @return BASE64-encoding
	 */
	public static String base64Encode(final byte[] b){
		final String encoded = Base64Coder.encode(b);
		
		if (encoded.indexOf('\n')>=0 || encoded.indexOf('\r')>=0){
			final char[] chars = encoded.toCharArray();
			
			final StringBuilder sb = new StringBuilder(chars.length);
			
			for (final char c: chars){
				if (c!='\r' && c!='\n')
					sb.append(c);
			}
			
			return sb.toString();
		}
		
		return encoded;
	}

	/**
	 * Decode some Base64-encoded data
	 * 
	 * @param data
	 * @return contents, or <code>null</code> if there was a problem decoding it
	 */
	public static byte[] base64Decode(final String data){
	    return Base64Coder.decode(data);
	}
	
	/**
	 * Get an iterator over the lines in a file
	 * 
	 * @param file file to read
	 * @return the iterator
	 * @throws IOException an exception from the underlying IO
	 * @see FileLinesIterable
	 * @see FileLinesIterator
	 */
	public static Iterable<String> getFileLines(final String file) throws IOException {
		return new FileLinesIterable(new BufferedReader(new FileReader(file)));
	}
}
