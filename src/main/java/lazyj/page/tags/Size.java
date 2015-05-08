/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>size</i> takes the integer value and returns the size (in bytes) indicated by the value.
 * 
 * @author costing
 * @since 2006-10-13
 * @see DDot
 * @see Dot
 */
public final class Size implements StringFormat {

	/**
	 * Use this to display file sizes. You should provide the file size in bytes as the value to this tag.
	 * 
	 * @param sTag tag name, ignored
	 * @param sOption "size" (bytes) or "size[UNIT]" where UNIT can be one of B,K,M,G,T,P,X
	 * @param s string to format, the size in the unit above 
	 * @return human-friendly size
	 * @see Format#size(long)
	 */
	@Override
	public String format(final String sTag, final String sOption, final String s) {
		try {
			if (sOption!=null && sOption.length()>4)
				return Format.size(Double.parseDouble(s), sOption.substring(4));
			
			return Format.size(Double.parseDouble(s));
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
