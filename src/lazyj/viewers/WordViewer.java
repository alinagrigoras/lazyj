package lazyj.viewers;

/**
 * Executes "/usr/jserv/bin/word.sh" to convert a Word document to a plain text representation.
 * 
 * @author costing
 * @since 2006-10-15
 */
public class WordViewer extends Viewer {

	/**
	 * The input to convert.
	 * 
	 * @param o input to convert
	 * @see Viewer#Viewer(Object)
	 */
	public WordViewer(Object o) {
		super(o);
	}

	/**
	 * This just calls {@link Viewer#getProgramOutput(String)} for "/usr/jserv/bin/word.sh" that will
	 * convert the original input into a string
	 * 
	 * @return the plain text representation of the original Word document
	 */
	@Override
	public String getString() {
		return getProgramOutput("/usr/jserv/bin/word.sh"); //$NON-NLS-1$
	}

}