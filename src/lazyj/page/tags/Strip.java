/**
 * 
 */
package lazyj.page.tags;

import java.util.Locale;
import java.util.StringTokenizer;

import lazyj.page.StringFormat;

/**
 * <i>strip</i> or <i>stripTAG,LIST</i> cut the HTML tags from the text, optionally leaving some
 * of them behind.
 * 
 * @author costing
 * @since 2006-10-13
 * @see Cut
 */
public final class Strip implements StringFormat {

	/**
	 * Implement the "strip" tag. The text in HTML format will be converted to a plain text representation.<br>
	 * This tag can have options, separated by comma:<br>
	 * <ul>
	 *   <li><b>BR</b> : leave BR tags in the output text</li>
	 *   <li><b>P</b> : leave P tags in the output text</li>
	 *   <li><b>A</b> : leave A tags in the output text</li>
	 *   <li><b>IMG</b> : leave IMG tags in the output text</li>
	 *   <li><b>FONT</b> : leave FONT tags in the output text</li>
	 *   <li><b>DIV</b> : leave FONT tags in the output text</li>
	 *   <li><b>SPAN</b> : leave FONT tags in the output text</li>
	 * </ul>
	 * 
	 * @param s string to format
	 * @param sTag tag, ignored
	 * @param sOption either "date" or "dateOPT,OPT2,..."
	 * @return the formatted string
	 */
	public String format(final String sTag, final String sOption, final String s) {
		boolean bBR = false;
		boolean bP  = false;
		boolean bA  = false;
		boolean bIMG = false;
		boolean bFONT = false;
		boolean bDIV = false;
		boolean bSPAN = false;
		boolean bB = false;
		boolean bI = false;
		boolean bU = false;

		boolean bAnyFlag = false;
		
		if (sOption!=null && sOption.length()>5){
			final StringTokenizer st = new StringTokenizer(sOption.substring(5).toLowerCase(Locale.getDefault()), ",");
			
			while (st.hasMoreTokens()){
				final String sToken = st.nextToken();
				
				if (sToken.equals("br")) {bBR = true; bAnyFlag = true;}
				else if (sToken.equals("p")) {bP = true; bAnyFlag = true;}
				else if (sToken.equals("a")) {bA = true; bAnyFlag = true;}
				else if (sToken.equals("img")) {bIMG = true; bAnyFlag = true;}
				else if (sToken.equals("font")) {bFONT = true; bAnyFlag = true;}
				else if (sToken.equals("div")) {bDIV = true; bAnyFlag = true;}
				else if (sToken.equals("span")) {bSPAN = true; bAnyFlag = true;}
				else if (sToken.equals("b")) {bB = true; bAnyFlag = true;}
				else if (sToken.equals("i")) {bI = true; bAnyFlag = true;}
				else if (sToken.equals("u")) {bI = true; bAnyFlag = true;}
			}
		}
		
		final StringBuilder sb = new StringBuilder(s.length());

		int iOld = 0;
		int idx;
		
		boolean bDel = false;
		
		while ( (idx=s.indexOf('<', iOld)) >= 0){
			if (idx>iOld){
				if (bDel && sb.length()>0)
					sb.append(' ');
				
				sb.append(s.substring(iOld, idx));
			}
			
			int iEnd = s.indexOf('>', idx);
			
			if (iEnd>0){
				bDel = true;
				
				if (bAnyFlag){
					final String sHTMLTag = s.substring(idx+1, iEnd);
					final String sHTMLTagLower = sHTMLTag.toLowerCase(Locale.getDefault());
					
					if (
							(bBR && (sHTMLTagLower.startsWith("br") || sHTMLTagLower.startsWith("/br"))) ||
							(bP && (sHTMLTagLower.startsWith("p") || sHTMLTagLower.startsWith("/p"))) ||
							(bA && (sHTMLTagLower.startsWith("a") || sHTMLTagLower.startsWith("/a"))) ||
							(bIMG && (sHTMLTagLower.startsWith("img") || sHTMLTagLower.startsWith("/img"))) ||
							(bFONT && (sHTMLTagLower.startsWith("font") || sHTMLTagLower.startsWith("/font"))) ||
							(bDIV && (sHTMLTagLower.startsWith("div") || sHTMLTagLower.startsWith("/div"))) ||
							(bSPAN && (sHTMLTagLower.startsWith("span") || sHTMLTagLower.startsWith("/span"))) ||
							(bB && (sHTMLTagLower.equals("b") || sHTMLTagLower.equals("/b"))) ||
							(bI && (sHTMLTagLower.equals("i") || sHTMLTagLower.equals("/i"))) ||
							(bU && (sHTMLTagLower.equals("u") || sHTMLTagLower.equals("/u")))
						)
					{
						sb.append('<').append(sHTMLTag).append('>');
						bDel = false;
					}
				}
			}
			else{
				// last tag doesn't close, ignore
				iOld = s.length();
				break;
			}
			
			iOld = iEnd+1;
		}
		
		if (bDel && sb.length()>0)
			sb.append(' ');
		
		sb.append(s.substring(iOld));
		
		return sb.toString();
	}

	/**
	 * Debug method
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Strip strip = new Strip();
		
		System.out.println(strip.format(null, "stripP", "asdf<a href=x bubu</a><a></a>gigi<br blabla"));
	}
	
}
