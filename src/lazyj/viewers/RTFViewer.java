package lazyj.viewers;

/**
 * Executes "/usr/jserv/bin/rtf.sh" to convert a RTF to a plain text representation.
 * 
 * @author costing
 * @since 2006-10-15
 */
public class RTFViewer extends Viewer {

	/**
	 * The input to convert.
	 * 
	 * @param o input to convert
	 * @see Viewer#Viewer(Object)
	 */
	public RTFViewer(Object o) {
		super(o);
	}

	/**
	 * This just calls {@link Viewer#getProgramOutput(String)} for "/usr/jserv/bin/rtf.sh" that will
	 * convert the original input into a string
	 * 
	 * @return the plain text representation of the original RTF
	 */
	@Override
	public String getString() {
		return getProgramOutput("/usr/jserv/bin/rtf.sh"); //$NON-NLS-1$
	}

}