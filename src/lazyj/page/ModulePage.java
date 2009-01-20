/**
 * 
 */
package lazyj.page;

import lazyj.ExtProperties;
import lazyj.Utils;

/**
 * Wrapper class for HTML templates loaded from modules. This is optional of course. But if you want
 * to use a system-wide location for these pieces of HTML you can use this class and:<br>
 * <ul>
 * <li>define system property <code>lazyj.config.folder</code> to point to a readable folder on disk (you should
 * anyway have this defined</li>
 * <li>create a file there named <code>modules.properties</code></li>
 * <li>put in it:<br><code><b>module.res.dir=<i>/path/to/templates</i></b></code></li>
 * </ul>
 * 
 * @author costing
 * @since 2006-10-15
 */
public class ModulePage extends BasePage {

	/**
	 * Configuration options for the modules
	 */
	static final ExtProperties	moduleProp;

	static {
		String s = Utils.getLazyjConfigFolder();

		ExtProperties pTemp;
		
		if (s==null){
			System.err.println("lazyj.page.ModulePage : System property 'lazyj.config.folder' is not defined"); //$NON-NLS-1$
			pTemp = new ExtProperties();
		}
		else{
			pTemp = new ExtProperties(s, "modules"); //$NON-NLS-1$
		}

		moduleProp = pTemp;
	}

	/**
	 * Read the indicated html template from the disk, relative to the folder specified by {@link #getResDir()}
	 * 
	 * @param sPage page to read from the disk
	 */
	public ModulePage(String sPage) {
		super(sPage);
	}

	/**
	 * Return the default path for the module html templates, as the value for the "module.res.dir" key from the "modules.properties" file
	 * that is found in the folder indicated by the "lazyj.config.folder" system property.
	 */
	@Override
	protected String getResDir() {
		return moduleProp.gets("module.res.dir"); //$NON-NLS-1$
	}

}
