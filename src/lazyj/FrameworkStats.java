package lazyj;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Vector;

/**
 * Statistics for the framework.
 * 
 * @author costing
 * @since 2006-10-04
 */
public final class FrameworkStats {
	
	/**
	 * Statistics helper class, only holds the zone and the page.
	 * 
	 * @author costing
	 * @since 2006-10-16
	 */
	private static final class AccessedPage {
		/**
		 * Servlet zone
		 */
		public final String	sZone;

		/**
		 * Page (servlet) name
		 */
		public final String	sPage;

		/**
		 * The only constructor, package protected 
		 * 
		 * @param sServletZone servlet zone
		 * @param sServlet page name
		 */
		AccessedPage(final String sServletZone, final String sServlet) {
			this.sZone = sServletZone;
			this.sPage = sServlet;
		}

		/**
		 * String representation, concatenates the two fields
		 */
		@Override
		public String toString() {
			return StringFactory.get(this.sZone + '/' + this.sPage);
		}
	}
	
	/**
	 * @author costing
	 * @since Jan 17, 2009
	 */
	private static final class StringCounter implements Comparable<StringCounter> {
		/**
		 * String to count
		 */
		public final String	s;

		/**
		 * Number of appearances
		 */
		public int	i;

		/**
		 * Initialize the variables
		 * 
		 * @param sKey string
		 * @param iCount number of appearances
		 */
		public StringCounter(final String sKey, final int iCount) {
			this.s = sKey;
			this.i = iCount;
		}

		/**
		 * Implementation of the Comparable interface
		 * @param sc object to compare to 
		 * @return result of the comparition, by the number of appearances
		 */
		public int compareTo(final StringCounter sc) {
			return this.i - sc.i;
		}

		@Override
		public boolean equals(final Object o){
			if (o==null || !(o instanceof StringCounter))
				return false;
			
			final StringCounter sc = (StringCounter) o;
			
			if (this.i != sc.i)
				return false;
			
			if (this.s==null)
				return sc.s==null;
			
			return this.s.equals(sc.s);
		}

		@Override
		public int hashCode(){
			return (this.s!=null ? this.s.hashCode() : 0) * 31 + this.i;
		}
		
		/**
		 * String representation of this object.
		 *  
		 * @return the original string and the number of appearances
		 */
		@Override
		public String toString() {
			return this.s + " : " + this.i; //$NON-NLS-1$
		}
	}

	/**
	 * Accessed pages
	 */
	private static final LinkedList<AccessedPage>	lAccessedPages	= new LinkedList<AccessedPage>();
	
	/**
	 * Package protected method called from {@link ExtendedServlet}.
	 * 
	 * @param sZone servlet zone
	 * @param sPage servlet name
	 */
	static final void addPage(final String sZone, final String sPage) {
		synchronized (lAccessedPages) {
			while (lAccessedPages.size() > 50000)
				lAccessedPages.remove(0);

			lAccessedPages.add(new AccessedPage(sZone, sPage));
		}
	}
		
	/**
	 * @param count
	 * @param type
	 * @return all the pages
	 */
	private static final Vector<String> getStatistics(final int count, final int type){
		final Vector<AccessedPage> vTemp = new Vector<AccessedPage>();

		synchronized (lAccessedPages) {
			vTemp.addAll(lAccessedPages);
		}

		final Vector<String> v = new Vector<String>();

		final HashMap<String, StringCounter> ht = new HashMap<String, StringCounter>();

		for (int i = 0; i < vTemp.size(); i++) {
			final String sKey = type==0 ? vTemp.get(i).sZone : vTemp.get(i).toString(); 
			
			StringCounter sc = ht.get(sKey);

			if (sc == null) {
				sc = new StringCounter(sKey, 0);
				ht.put(sKey, sc);
			}

			sc.i++;
		}

		final Vector<StringCounter> v2 = new Vector<StringCounter>();
		v2.addAll(ht.values());

		Collections.sort(v2);
		Collections.reverse(v2);

		for (int i = 0; i < v2.size() && i < count; i++)
			v.add(v2.get(i).toString());

		return v;		
	}

	/**
	 * Get the most accessed zones
	 * 
	 * @param count maximum number of zones to return
	 * @return an array of the most accessed zones, with the number of accesses to them
	 */
	public static final Vector<String> getTopZones(final int count) {
		return getStatistics(count, 0);
	}
	

	/**
	 * Get the most accessed pages (servlets)
	 * 
	 * @param count maximum number of results to return
	 * @return an array of strings with the zone/servlet and the number of accesses
	 */
	public static final Vector<String> getTopPages(final int count) {
		return getStatistics(count, 1);
	}
	
	/**
	 * Clear the counters and the internal structures.
	 */
	public static void clear(){
		synchronized (lAccessedPages) {
			lAccessedPages.clear();
		}
	}
}
