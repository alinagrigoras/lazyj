package lazyj.mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import lazyj.Log;


/**
 * Decodes mail headers and provides methods to easily extract them.
 * 
 * @author costing
 * @since 2006-10-06
 */
public class Header {

	/**
	 * Original string that was used to generate this object
	 */
	protected String				sCompleteHeader;

	/**
	 * If this is a mail header then this field in the boundary between the body parts 
	 */
	public String					sBoundary;

	/**
	 * Map of key-value pairs of the header fields.
	 */
	protected Map<String, String>	mHeaders;

	/**
	 * Constructor based on the extracted header of a mail, attachment or something else similar.
	 * 
	 * @param sHeader original header
	 */
	public Header(final String sHeader) {
		this.sCompleteHeader = sHeader.replace('\t', ' ');

		final List<String> lLines = new LinkedList<>();

		final BufferedReader br = new BufferedReader(new StringReader(this.sCompleteHeader));

		String s;
		StringBuilder sbPrev = null;

		try {
			while ( (s = br.readLine()) != null) {				
				if (s.length()==0)
					break;
					
				if (s.charAt(0) == ' ') {
					if (sbPrev != null)
						sbPrev.append(s);
					else
						sbPrev = new StringBuilder(s);
				} else {
					if (sbPrev != null)
						lLines.add(sbPrev.toString());
					
					sbPrev = new StringBuilder(s);
				}
			}
		} catch (final IOException e) {
			// ignore
		}

		if (sbPrev != null)
			lLines.add(sbPrev.toString());

		this.mHeaders = new HashMap<>();

		for (final String sLine : lLines)
			processLine(sLine);

		this.sBoundary = getBoundary();
	}

	/**
	 * Process one line of text
	 * 
	 * @param s
	 * @return <code>true</code> if ok
	 */
	private boolean processLine(final String s) {
		if (s == null || s.length() <= 0)
			return false;

		try {
			final int l = s.length();
			int i = 0;
			char c = s.charAt(0);
			

			final StringBuilder sTag = new StringBuilder();
			final StringBuilder sValue = new StringBuilder(s.length());

			final byte cb[] = s.getBytes();

			while ((i < l) && (c = (char) cb[i]) == ' ')
				i++;

			while ((i < l)) {
				c = (char) cb[i];

				if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-')
					sTag.append(c);
				else
					break;

				i++;
			}

			while ((i < l) && (c = (char) cb[i]) == ' ')
				i++;

			if (sTag.length() <= 0)
				return false;

			if (c == ':' || c == '=') { // e ok
				i++;
				while ((i < l) && (c = (char) cb[i]) == ' ')
					i++;

				boolean b = false;
				boolean e = false;

				while (i < l) {
					c = (char) cb[i];
					i++;

					if (!b && c == ';') {
						break;
					}

					if (!b && c == '"') {
						b = true;
						e = false;
					} else if (b && !e && c == '"') {
						b = false;
					} else if (b && c == '\\') {
						e = true;
					} else if (b && e) {
						e = false;
					}

					sValue.append(c);
				}

				if (i < l) {
					if (!processLine(s.substring(i)))
						sValue.append(';').append(s.substring(i));
				}

				String sRez = sValue.toString();

				while (sRez.length() >= 2 && sRez.startsWith("\"") && sRez.endsWith("\"")) //$NON-NLS-1$ //$NON-NLS-2$
					sRez = sRez.substring(1, sRez.length() - 1);

				this.mHeaders.put(sTag.toString().toLowerCase(Locale.getDefault()), sRez);
			} else { // ignor acest fals tag, ciudat oricum ...
				return false;
			}

			return true;
		} catch (final Exception e) {
			Log.log(Log.WARNING, "lazyj.mail.Header", "processLine", e);  //$NON-NLS-1$//$NON-NLS-2$

			return false;
		}
	}

	/**
	 * Get the string that was used to build this object.
	 * 
	 * @return original header
	 */
	public String getOriginalHeader() {
		return this.sCompleteHeader;
	}

	/**
	 * Get the current section boundary
	 * 
	 * @return boundary
	 */
	private String getBoundary() {
		String b = getValue("boundary"); //$NON-NLS-1$

		final StringTokenizer st = new StringTokenizer(b, " \"\t\r\n"); //$NON-NLS-1$

		if (st.hasMoreTokens())
			b = st.nextToken();
		else
			b = ""; //$NON-NLS-1$

		if (b.length() > 0)
			b = "--" + b; //$NON-NLS-1$

		return b;
	}

	/**
	 * Get the value for a given header key.
	 * 
	 * @param sKey header key
	 * @return the value for this key, if it exists, or "" if the given key doesn't exist in the original header.
	 */
	public String getValue(final String sKey) {
		final String sRez = this.mHeaders.get(sKey.trim().toLowerCase(Locale.getDefault()));

		return sRez != null ? sRez : ""; //$NON-NLS-1$
	}

	/**
	 * Testing code
	 * 
	 * @param args ignored
	 */
	public static void main(final String args[]){
		final Header h = new Header("Content-Type: text/plain;\n" +  //$NON-NLS-1$
				"	charset=\"us-ascii\"\n" +  //$NON-NLS-1$
				"Content-Transfer-Encoding: quoted-printable\n"); //$NON-NLS-1$
		
		System.out.println(h.getValue("charset")); //$NON-NLS-1$
	}
	
}