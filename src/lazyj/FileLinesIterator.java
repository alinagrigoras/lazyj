package lazyj;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;


/**
 * Iterator over the lines in a buffer
 * 
 * @author costing
 */
public class FileLinesIterator implements Iterator<String>{
	/**
	 * Buffer to iterate over
	 */
	private final BufferedReader br;
	
	/**
	 * Next line in the buffer
	 */
	private String sNextLine;
	
	/**
	 * Iterate over the lines in this bufferedReader 
	 * 
	 * @param br
	 */
	public FileLinesIterator(final BufferedReader br){
		this.br = br;
	
		readNextLine();
	}
	
	/**
	 * Read next line from the buffer
	 */
	private void readNextLine(){
		try{
			this.sNextLine = this.br.readLine();
		}
		catch (final IOException ioe){
			this.sNextLine = null;
		}
	}

	@Override
	public boolean hasNext() {
		return this.sNextLine!=null;
	}

	@Override
	public String next() {
		final String sRet = this.sNextLine;
		
		readNextLine();
		
		return sRet;
	}

	@Override
	public void remove() {
		// not implemented
	}
}
