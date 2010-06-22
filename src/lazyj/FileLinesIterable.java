package lazyj;

import java.io.BufferedReader;
import java.util.Iterator;

/**
 * Iterable object over the lines in a buffer
 * 
 * @author costing
 */
public class FileLinesIterable implements Iterable<String> {
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

	public Iterator<String> iterator() {
		return new FileLinesIterator(this.br);
	}
}
