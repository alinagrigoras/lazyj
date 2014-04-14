/**
 * 
 */
package lazyj.page.tags;

import lazyj.Format;
import lazyj.page.StringFormat;

/**
 * <i>ntobr</i> tag replaces all \n occurences with &lt;BR&gt;
 * 
 * @author costing
 * @since 2010-02-28
 */
public final class NtoBR implements StringFormat {

	/**
	 * Replace all \n with &lt;BR&gt;
	 * 
	 * @param sTag ignored
	 * @param sOption always "ntobr"
	 * @param s value to encode
	 * @return html lines
	 */
	@Override
	public String format(final String sTag, final String sOption,final String s) {
		if (s==null || s.length()==0)
			return s;
		
		return Format.replace(Format.replace(s, "\n", "<BR>"), "\r", "");  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

}
