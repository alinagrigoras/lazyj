/**
 * 
 */
package lazyj.widgets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lazyj.DBFunctions;

/**
 * Generic tree object. It will take the entries through a {@link TreeProvider} object and will keep the
 * children in the same order they are returned by the provider. The only restriction is to have one of the 
 * roots as first entry. Any orphan node will be put on the same level with the first entry (root).
 * 
 * @author costing
 * @param <K> key type (usually Integer).
 * @param <V> value type (usually String).
 * @since Nov 26, 2007 (1.0.4)
 */
public class Tree<K,V> implements OptionProvider<K,V>{
	
	/**
	 * Node's key (internal ID)
	 */
	final K key;
	
	/**
	 * Node's value (what is displayed)
	 */
	final V value;
	
	/**
	 * Children
	 */
	private List<Tree<K,V>> children = null;
	
	/**
	 * Entries on the same level
	 */
	private List<Tree<K,V>> brothers = null;
	
	/**
	 * Build a tree based on the information returned by the provider.
	 * 
	 * @param provider
	 */
	public Tree(final TreeProvider<K, V> provider){
		this(provider.getKey(), provider.getValue());
	
		final HashMap<K, Tree<K,V>> parents = new HashMap<K, Tree<K, V>>();
		
		final HashMap<K, List<Tree<K,V>>> flatSpace = new HashMap<K, List<Tree<K, V>>>(); 
		
		parents.put(this.key, this);
		
		while (provider.moveNext()){
			final K parent = provider.getParent();
			final K nodeKey = provider.getKey();
			final Tree<K, V> node = new Tree<K, V>(nodeKey, provider.getValue());
			
			parents.put(nodeKey, node);
			
			List<Tree<K, V>> childrenOf = flatSpace.get(parent);
			
			if (childrenOf==null){
				childrenOf = new ArrayList<Tree<K,V>>();
				
				flatSpace.put(parent, childrenOf);
			}
			
			childrenOf.add(node);
		}
		
		for (Map.Entry<K, List<Tree<K,V>>> entry: flatSpace.entrySet()){
			final Tree<K,V> parent = parents.get(entry.getKey());
			
			if (parent!=null){
				parent.children = entry.getValue();
			}
			else{
				if (this.brothers==null)
					this.brothers = new ArrayList<Tree<K,V>>();
				
				this.brothers.addAll(entry.getValue());
			}
		}
	}

	/**
	 * @param key
	 * @param value
	 */
	private Tree(final K key, final V value){
		this.key = key;
		this.value = value;
	}
	
	/**
	 * Key of this node.
	 * 
	 * @return key
	 */
	public K getKey(){
		return this.key;
	}
	
	/**
	 * Value for this node.
	 * 
	 * @return value
	 */
	public V getValue(){
		return this.value;
	}
	
	/**
	 * List of children. They retain the order from the query result.
	 * 
	 * @return children
	 */
	public List<Tree<K, V>> getChildren(){
		return new ArrayList<Tree<K,V>>(this.children);
	}
	
	/**
	 * Convert the tree to a collection of {@link Option} objects that are good to be used in a {@link Select}.
	 * 
	 * @return tree as options
	 */
	@Override
	public Collection<Option<K,V>> getOptions(){
		final Collection<Option<K, V>> ret = getOptions(0);
		
		if (this.brothers!=null){
			for (Tree<K, V> brother: this.brothers){
				ret.addAll(brother.getOptions(0));
			}
		}
		
		return ret;
	}
	
	/**
	 * Get the options having as value the concatenated values of the parents and their own, separated by
	 * the specified separator.
	 * 
	 * @param sSeparator some separator, for example " &amp;raquo; "
	 * @return tree as options with full path to them
	 */
	public Collection<Option<K, String>> getOptionsFullPath(final String sSeparator){
		final Collection<Option<K, String>> ret = getOptionsFullPath(0, "", sSeparator==null ? "" : sSeparator);  //$NON-NLS-1$//$NON-NLS-2$
		
		if (this.brothers!=null){
			for (Tree<K, V> brother: this.brothers){
				ret.addAll(brother.getOptionsFullPath(0, "", sSeparator==null ? "" : sSeparator)); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		
		return ret;		
	}
	
	/**
	 * @author costing
	 * @since Jan 17, 2009
	 */
	private class TreeOption implements Option<K,V>{
		/**
		 * level
		 */
		private final int iLevel;
		
		/**
		 * @param iLevel
		 */
		public TreeOption(final int iLevel){
			this.iLevel = iLevel;
		}
		
		@Override
		public K getKey() {
			return Tree.this.key;
		}

		@Override
		public int getLevel() {
			return this.iLevel;
		}

		@Override
		public V getValue() {
			return Tree.this.value;
		}
		
	}
	
	/**
	 * @param iLevel
	 * @return options
	 */
	private Collection<Option<K,V>> getOptions(final int iLevel){
		final Collection<Option<K, V>> ret = new ArrayList<Option<K,V>>();
		
		ret.add(new TreeOption(iLevel));
		
		if (this.children!=null){
			for (Tree<K, V> child: this.children){
				ret.addAll(child.getOptions(iLevel+1));
			}
		}
		
		return ret;
	}
	
	/**
	 * @author costing
	 * @since Jan 17, 2009
	 */
	private class FullPathOption implements Option<K,String>{
		
		/**
		 * level
		 */
		private final int iLevel;
		
		/**
		 * key
		 */
		private final K keyInternal;
		
		/**
		 * value
		 */
		private final String valueInternal;
		
		/**
		 * @param key
		 * @param value
		 * @param iLevel
		 */
		public FullPathOption(final K key, final String value, final int iLevel){
			this.keyInternal = key;
			this.valueInternal = value;
			this.iLevel = iLevel;
		}
		
		@Override
		public int getLevel(){
			return this.iLevel;
		}
		
		@Override
		public K getKey(){
			return this.keyInternal;
		}
		
		@Override
		public String getValue(){
			return this.valueInternal;
		}
		
	}
	
	/**
	 * @param iLevel
	 * @param sPrefix
	 * @param sSeparator
	 * @return options with full path
	 */
	private Collection<Option<K,String>> getOptionsFullPath(final int iLevel, final String sPrefix, final String sSeparator){
		final Collection<Option<K, String>> ret = new ArrayList<Option<K, String>>();
		
		String sNewPrefix = sPrefix+getValue().toString();
		
		ret.add(new FullPathOption(getKey(), sNewPrefix, iLevel));
		
		if (this.children!=null){
			sNewPrefix += sSeparator;
			
			for (Tree<K, V> child: this.children){
				ret.addAll(child.getOptionsFullPath(iLevel+1, sNewPrefix, sSeparator));
			}
		}
		
		return ret;		
	}
	
	/**
	 * Build a tree upon a simple query that has ID on the first column, parent ID on the second, and value
	 * to be displayed on the third. It's just shortcut that relies on {@link BasicDBTreeProvider} to do
	 * the actual work.
	 * 
	 * @param db
	 * @return the tree
	 * @see BasicDBTreeProvider
	 */
	public static Tree<Integer, String> getDefaultTree(final DBFunctions db){
		return new Tree<Integer, String>(new BasicDBTreeProvider(db));
	}
}
