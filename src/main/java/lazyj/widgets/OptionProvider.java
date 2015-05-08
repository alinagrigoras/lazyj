/**
 * 
 */
package lazyj.widgets;

import java.util.Collection;

/**
 * Simple interface implemented by all the classes that can provide a list of options.
 * 
 * @author costing
 * @param <K> key type (usually Integer or String)
 * @param <V> value type (usually String)
 * @since Nov 26, 2007 (1.0.4)
 */
public interface OptionProvider<K,V> {

	/**
	 * Get the collection of options.
	 * 
	 * @return the options
	 */
	public Collection<Option<K,V>> getOptions();
	
}
