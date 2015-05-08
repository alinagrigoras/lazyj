/**
 * 
 */
package lazyj.page.tags;

import java.util.Locale;

import lazyj.page.StringFormat;

/**
 * <i>cutN</i> return the first <b>N</b> characters from the value. It will leave in the return text
 * the tags <i>A, B, U, I, FONT</i> but will not count their length but only the length of the text
 * that is actually displayed. 
 * 
 * @author costing
 * @since 2006-10-13
 * @see Strip
 */
public class Cut implements StringFormat {

	/**
	 * Cuts a string, leaving only the first N characters from it, jumping over tags.
	 * 
	 * @param sTag ignored
	 * @param sOption tag option, in the form "cutN", with N>0
	 * @param sValue original string
	 * @return the first N visible characters from the string
	 */
	@Override
	public String format(final String sTag, final String sOption, final String sValue) {
		try{
			return cut(sValue, Integer.parseInt(sOption.substring(3)));
		}
		catch (Exception e){
			return ""; //$NON-NLS-1$
		}
	}
		
	/**
	 * Cuts a string, leaving only the first N characters from it, jumping over tags.
	 * 
	 * @param sValue
	 * @param iCut
	 * @return the first N visible characters from the string
	 */
	@SuppressWarnings("nls")
	public static String cut(final String sValue, final int iCut){
		try {
			if (iCut<=0)
				return "";
			
			final StringBuilder sBuffer = new StringBuilder();

			int count = 0;

			int i2 = 0;
			int iOld2 = 0;

			String s = sValue.replace("<<:.*:>>", "");

			while (count<iCut && (i2 = s.indexOf("<", iOld2)) >= 0) {
				if (i2 > iOld2) {
					String sTemp = s.substring(iOld2, i2);

					if (count + sTemp.length() > iCut) {
						if (iCut - count > 0)
							sTemp = sTemp.substring(0, iCut - count);
						else
							sTemp = "";
					}

					sBuffer.append(sTemp);
					count += sTemp.length(); // only count the actual text
				}

				int j2 = s.indexOf(">", i2);

				iOld2 = i2 + 1;

				if (j2 > i2) { // some tags make it into the final text, but they don't count up to the allowed length
					final String stag = s.substring(i2, j2 + 1);
					final String sl = stag.toLowerCase(Locale.getDefault());
					if (
						sl.startsWith("</a") || 
						sl.startsWith("</b") || 
						sl.startsWith("</font") || 
						sl.startsWith("</u") || 
						sl.startsWith("</i") ||
						(count<iCut && (
							sl.startsWith("<a ") || 
							sl.startsWith("<b>") || 
							sl.startsWith("<font") || 
							sl.startsWith("<u") || 
							(sl.startsWith("<i") && !sl.startsWith("<img"))
						))
					)
						sBuffer.append(stag);

					iOld2 = j2 + 1;
				}
			}

			if (iOld2 < s.length()) {
				String sTemp = s.substring(iOld2);

				if (count + sTemp.length() > iCut) {
					if (iCut - count > 0)
						sTemp = sTemp.substring(0, iCut - count);
					else
						sTemp = "";
				}

				sBuffer.append(sTemp);
				//count += sTemp.length();
			}

			if (sBuffer.length() < s.length()-3)
				sBuffer.append("...");
			else
				return s;
			
			s = sBuffer.toString();
			
			return s;
		}
		catch (Throwable e) {
			return null;
		}
	}

	/**
	 * Debug method
	 * 
	 * @param args
	 */
	public static void main(String args[]){
		String sTest = "string to <a blabla>test</a>"; //$NON-NLS-1$
		
		System.err.println(cut(sTest, 11));
	}
}
