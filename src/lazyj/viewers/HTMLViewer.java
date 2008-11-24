package lazyj.viewers;

/**
 * Executes "/usr/jserv/bin/html.sh" to convert a HTML to a plain text representation.
 * 
 * @author costing
 * @since 2006-10-15
 */
public class HTMLViewer extends Viewer {

	/**
	 * The input to convert.
	 * 
	 * @param o input to convert
	 * @see Viewer#Viewer(Object)
	 */
	public HTMLViewer(Object o) {
		super(o);
	}

	/**
	 * This just calls {@link Viewer#getProgramOutput(String)} for "/usr/jserv/bin/html.sh" that will
	 * convert the original input into a string
	 * 
	 * @return the plain text representation of the original HTML
	 */
	@Override
	public String getString() {
		return getProgramOutput("/usr/jserv/bin/html.sh");
	}

}