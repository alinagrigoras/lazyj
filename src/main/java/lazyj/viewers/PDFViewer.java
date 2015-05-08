package lazyj.viewers;

/**
 * Executes "/usr/jserv/bin/pdf.sh" to convert a PDF to a plain text representation.
 * 
 * @author root
 * @since 2006-10-15
 */
public class PDFViewer extends Viewer {

	/**
	 * The input to convert.
	 * 
	 * @param o input to convert
	 * @see Viewer#Viewer(Object)
	 */
	public PDFViewer(final Object o) {
		super(o);
	}

	/**
	 * This just calls {@link Viewer#getProgramOutput(String)} for "/usr/jserv/bin/pdf.sh" that will
	 * convert the original input into a string
	 * 
	 * @return the plain text representation of the original PDF file
	 */
	@Override
	public String getString() {
		return getProgramOutput("/usr/jserv/bin/pdf.sh"); //$NON-NLS-1$
	}

}