package lazyj.widgets;

import lazyj.page.BasePage;

/**
 * Implement this interface to be able to process yourself each Option. An implementation of this interface
 * can be passed to {@link Select#Select(java.util.Collection, java.util.Set, String, BasePage, OptionFormatter)}
 * and so the Select will call {@link #formatOption(Option, BasePage)} for each entry that is displayed, after it
 * had applies the default substitutions.
 * 
 * @author costing
 * @since Nov 26, 2007 (1.0.4)
 */
public interface OptionFormatter {

	/**
	 * Callback function that further processes each Option.
	 * 
	 * @param option Option to display
	 * @param p template that reaches this point with the default substitutions already applied.
	 */
	public void formatOption(Option<?, ?> option, BasePage p); 
	
}
