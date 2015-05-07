package lazyj;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

/**
 * Iterable object over the lines in a buffer
 * 
 * @author costing
 */
public class FileLinesIterable implements Iterable<String>, Closeable {
	/**
	 * The buffer
	 */
	private final BufferedReader br;

	/**
	 * Build the iterable over a buffered reader
	 * 
	 * @param br the buffer
	 */
	public FileLinesIterable(final BufferedReader br) {
		this.br = br;
	}

	@Override
	public Iterator<String> iterator() {
		return new FileLinesIterator(this.br);
	}
	
	@Override
	protected void finalize() throws Throwable {
		close();
	}

	@Override
	public void close() throws IOException {
		try{
			this.br.close();
		}
		catch (final IOException ioe){
			// ignore
		}
	}
}
