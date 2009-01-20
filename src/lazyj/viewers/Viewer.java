package lazyj.viewers;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Viewers are used to transform some input files into String representations. Usually they extract the contents from these files using 
 * appropriate tools (eg. for HTML something like "links" or "lynx" and so on)
 * 
 * @author root
 * @since 2006-10-15
 */
public abstract class Viewer {

	/**
	 * The InputStream to read the contents of the file from.
	 */
	protected InputStream	isSource	= null;

	/**
	 * Constructor to be called from the extensions of this class, it looks at the input object and builds the actual input stream from it.
	 * If it is a String then it will build a FileInputStream based on the file indicated by this parameter. If it is an InputStream than it 
	 * will just use it. Otherwise the <code>isSource</code> will remain null.
	 * 
	 * @param o input method
	 */
	protected Viewer(final Object o) {
		if (o == null)
			return;

		if (o instanceof String) {
			// System.err.println("String");
			try {
				this.isSource = new FileInputStream((String) o);
			} catch (Exception e) {
				System.err.println("Exception opening file " + (String) o + " : " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		if (o instanceof InputStream) {
			// System.err.println("InputStream");
			this.isSource = (InputStream) o;
		}
	}

	/**
	 * This is to be implemented by every viewer.
	 * 
	 * @return the contents of the original file
	 */
	public abstract String getString();

	/**
	 * Executes the given program and passes the contents of the InputStream to the programs's standard in and returns the text that the 
	 * program produces at its standard output.
	 * 
	 * @param sProgram program to call
	 * @return the output of the program
	 */
	protected final String getProgramOutput(final String sProgram) {
		if (this.isSource == null)
			return null;

		BufferedOutputStream child_out = null;
		
		try {
			Runtime rt = Runtime.getRuntime();

			// System.err.println("Program = " + sProgram);

			String comanda[] = new String[1];
			comanda[0] = sProgram;

			Process child = null;
			try {
				child = rt.exec(comanda);
				// Thread.sleep(1);
			} catch (IOException e) {
				System.err.println("IOException " + e + " (" + e.getMessage() + ")");  //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$
				return null;
			}

			child_out = new BufferedOutputStream(child.getOutputStream());
			int iVal = -1;

			byte buff[] = new byte[10240];

			while ((iVal = this.isSource.read(buff)) >= 0) {
				try {
					child_out.write(buff, 0, iVal);
				} catch (Exception e) {
					System.err.println("Child write exception " + e.getMessage()); //$NON-NLS-1$
					return null;
				}
			}
			child_out.flush();
			child_out.close();
			child_out = null;

			InputStream isCh = child.getInputStream();

			StringBuilder sb = new StringBuilder(2000);
			char cbuff[] = new char[10240];
			int iCount = 0;

			do {

				try {
					iCount = isCh.read(buff);
					if (iCount > 0) {
						for (int i = 0; i < iCount; i++)
							cbuff[i] = (char) buff[i];
						sb.append(cbuff, 0, iCount);
					}
				} catch (Exception e) {
					System.err.println("Exception ?!? " + e.getMessage()); //$NON-NLS-1$
					break;
				}

			} while (iCount > 0);

			try {
				child.waitFor();
			} catch (InterruptedException e) {
				System.err.println("Interrupted! " + e.getMessage()); //$NON-NLS-1$
				return null;
			}

			int ev = child.exitValue();

			if (ev != 0) {
				System.err.println("Exit value = " + ev); //$NON-NLS-1$
			}

			// Log.log("Program output string", sb.toString());

			return sb.toString();
		} catch (Throwable e) {			
			System.err.println("Unexpected exception " + e.getMessage()); //$NON-NLS-1$
			return null;
		}
		finally{
			try{
				if (child_out!=null)
					child_out.close();
			}
			catch (IOException ioe){
				// ignore
			}
		}

	}

}