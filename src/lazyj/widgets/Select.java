/**
 * 
 */
package lazyj.widgets;

import java.util.Collection;
import java.util.Set;

import lazyj.page.BasePage;
import lazyj.page.Page;

/**
 * Build the options for a <i><code>select</code></i> HTML tag.
 * 
 * @author costing
 * @param <K> key type (hidden ID field). Usually Integer or String.
 * @param <V> value type. Usually String.
 * @since Nov 26, 2007 (1.0.4)
 */
public class Select<K,V> implements Page {

	/**
	 * Options
	 */
	private final Collection<Option<K,V>> options;
	
	/**
	 * Which of the above options are selected
	 */
	private final Set<K> selected;
	
	/**
	 * Template for one option
	 */
	private final BasePage pOption;
	
	/**
	 * How to indent options on lower levels
	 */
	private final String sIndent;
	
	/**
	 * How to format an option in order to display it
	 */
	private final OptionFormatter formatter;
	
	/**
	 * Build the list of options from a collection.
	 * 
	 * @param options list of options
	 */
	public Select(final Collection<Option<K,V>> options){
		this(options, null);
	}

	/**
	 * Build the list of options from a collection, selecting by default the elements that show up in the set.
	 * 
	 * @param options list of options
	 * @param selected list of selected items. Can be <code>null</code>.
	 */
	public Select(final Collection<Option<K,V>> options, final Set<K> selected){
		this(options, selected, "&nbsp;&nbsp;");
	}
	
	/**
	 * Build the list of options from a collection, selecting by default the elements that show up in the set,
	 * indenting options that have level&gt;0 with some string.
	 * 
	 * @param options list of options
	 * @param selected list of selected items. Can be <code>null</code>.
	 * @param sIndent indentation to be appended for each level (can be <code>null</code> or empty string if you don't want indentation based on the level).
	 */
	public Select(final Collection<Option<K,V>> options, final Set<K> selected, final String sIndent){
		this(options, selected, sIndent, null);
	}

	
	/**
	 * Build the list of options from a collection, selecting by default the elements that show up in the set,
	 * indenting options that have level&gt;0 with some string. With this constructor you can also override the
	 * template used to display an option.<br>
	 * <br>
	 * If you give a custom template please take note of the following optional tags:<br><ul>
	 * <li><b>key</b> : replaced by the key of each option</li>
	 * <li><b>selected</b> : replaced by "selected" if the key is part of the given set</li>
	 * <li><b>indent</b> : replaced with the depth level times of <code>sIndent</code></li>
	 * <li><b>value</b> : replaced with the value for each option</li>
	 * </ul> 
	 * 
	 * @param options list of options
	 * @param selected list of selected items. Can be <code>null</code>.
	 * @param sIndent indentation to be appended for each level (can be <code>null</code> or empty string if you don't want indentation based on the level).
	 * @param pOption a page that has the following tags : 'key', 'selected', 'indent' and 'value'. Can be <code>null</code>, if you want to use the built-in simple template.
	 */
	public Select(final Collection<Option<K,V>> options, final Set<K> selected, final String sIndent, final BasePage pOption){
		this(options, selected, sIndent, pOption, null);
	}
	
	/**
	 * Build the list of options from a collection, selecting by default the elements that show up in the set,
	 * indenting options that have level&gt;0 with some string. With this constructor you can also override the
	 * template used to display an option.<br>
	 * <br>
	 * If you give a custom template please take note of the following optional tags:<br><ul>
	 * <li><b>key</b> : replaced by the key of each option</li>
	 * <li><b>selected</b> : replaced by "selected" if the key is part of the given set</li>
	 * <li><b>indent</b> : replaced with the depth level times of <code>sIndent</code></li>
	 * <li><b>value</b> : replaced with the value for each option</li>
	 * </ul>
	 * <br>
	 * The formatter is used to further change each option, as it is displayed.
	 * 
	 * @param options list of options
	 * @param selected list of selected items. Can be <code>null</code>.
	 * @param sIndent indentation to be appended for each level (can be <code>null</code> or empty string if you don't want indentation based on the level).
	 * @param pOption a page that has the following tags : 'key', 'selected', 'indent' and 'value'. Can be <code>null</code>, if you want to use the built-in simple template.
	 * @param formatter line extra formatter
	 */
	public Select(final Collection<Option<K,V>> options, final Set<K> selected, final String sIndent, final BasePage pOption, final OptionFormatter formatter){
		this.options = options;
		this.selected = selected;
		this.pOption = pOption!=null ? pOption : BasePage.getPage("<option value='<<:key esc:>>' <<:selected:>>><<:indent:>><<:value:>></option>\n");
		this.sIndent = sIndent;
		this.formatter = formatter;
	}
	
	/* (non-Javadoc)
	 * @see lazyj.page.Page#getContents()
	 */
	public StringBuilder getContents(){
		final StringBuilder sb = new StringBuilder();
		
		final boolean bIndent = this.sIndent!=null && this.sIndent.length()>0;
		
		for (Option<K,V> option: this.options){
			K key = option.getKey(); 
			
			this.pOption.modify("key", key);
			
			if (bIndent){
				for (int i=option.getLevel(); i>0; i--)
					this.pOption.append("indent", this.sIndent);
			}
			
			this.pOption.append("value", option.getValue());
			
			this.pOption.modify("selected", (this.selected!=null && this.selected.contains(key)) ? "selected" : "");
			
			if (this.formatter!=null)
				this.formatter.formatOption(option, this.pOption);
			
			sb.append(this.pOption.getContents());
		}
		
		return sb;
	}
}
