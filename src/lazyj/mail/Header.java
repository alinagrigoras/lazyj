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

		final List<String> lLines = new LinkedList<String>();

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
		} catch (IOException e) {
			// ignore
		}

		if (sbPrev != null)
			lLines.add(sbPrev.toString());

		this.mHeaders = new HashMap<String, String>();

		for (String sLine : lLines)
			processLine(sLine);

		this.sBoundary = getBoundary();
	}

	private boolean processLine(String s) {
		if (s == null || s.length() <= 0)
			return false;

		try {
			int l = s.length();
			int i = 0;
			char c = s.charAt(0);
			

			String sTag = "";
			StringBuilder sValue = new StringBuilder(s.length());

			byte cb[] = s.getBytes();

			while ((i < l) && (c = (char) cb[i]) == ' ')
				i++;

			while ((i < l)) {
				c = (char) cb[i];

				if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-')
					sTag += c;
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

				while (sRez.length() >= 2 && sRez.startsWith("\"") && sRez.endsWith("\""))
					sRez = sRez.substring(1, sRez.length() - 1);

				this.mHeaders.put(sTag.toLowerCase(Locale.getDefault()), sRez);
			} else { // ignor acest fals tag, ciudat oricum ...
				return false;
			}

			return true;
		} catch (Exception e) {
			Log.log(Log.WARNING, "lazyj.mail.Header", "processLine", e);

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

	private String getBoundary() {
		String b = getValue("boundary");

		final StringTokenizer st = new StringTokenizer(b, " \"\t\r\n");

		if (st.hasMoreTokens())
			b = st.nextToken();
		else
			b = "";

		if (b.length() > 0)
			b = "--" + b;

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

		return sRez != null ? sRez : "";
	}

	/**
	 * Testing code
	 * 
	 * @param args ignored
	 */
	public static void main(String args[]){
		Header h = new Header("Content-Type: text/plain;\n" + 
				"	charset=\"us-ascii\"\n" + 
				"Content-Transfer-Encoding: quoted-printable\n");
		
		System.out.println(h.getValue("charset"));
	}
	
}