package lazyj.viewers;

/**
 * Executes "/usr/jserv/bin/txt.sh" to convert a text file to a plain text representation.
 * Stupid, since it doesn't need any smart conversion, but just for the consistance of the API.
 * 
 * @author costing
 * @since 2006-10-15
 */
public class TXTViewer extends Viewer {
	
	/**
	 * The input to convert.
	 * 
	 * @param o input to convert
	 * @see Viewer#Viewer(Object)
	 */
	public TXTViewer(Object o) {
		super(o);
	}

	/**
	 * This just calls {@link Viewer#getProgramOutput(String)} for "/usr/jserv/bin/txt.sh" that will
	 * convert the original input into a string
	 * 
	 * @return the contents of the file
	 */
	@Override
	public String getString() {
		return getProgramOutput("/usr/jserv/bin/txt.sh"); //$NON-NLS-1$
	}

}