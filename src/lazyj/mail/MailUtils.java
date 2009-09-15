/**
 * 
 */
package lazyj.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

/**
 * Mail-related helper functions
 * 
 * @author costing
 * @since Sep 15, 2009
 */
public class MailUtils {

	/**
	 * MX response involves an order
	 * 
	 * @author costing
	 * @since Sep 15, 2009
	 */
	public static class MXRecord implements Comparable<MXRecord>{
		
		/**
		 * Server priority
		 */
		private int prio;
		
		/**
		 * Server address
		 */
		private String server;
		
		/**
		 * An attribute returned by the query
		 * 
		 * @param queryResponse
		 */
		MXRecord(final String queryResponse){
			final StringTokenizer st = new StringTokenizer(queryResponse);
			
			this.prio = Integer.parseInt(st.nextToken());
			this.server = st.nextToken();
			
			if (this.server.endsWith("."))
				this.server = this.server.substring(0, this.server.length()-1);
		}
		
		public int compareTo(final MXRecord other){
			if (this.prio!=other.prio)
				return this.prio-other.prio;
			
			return this.server.compareTo(other.server);
		}
		
		@Override
		public boolean equals(final Object o){
			if (o==null || !(o instanceof MXRecord))
				return false;
			
			return compareTo((MXRecord) o)==0;
		}
		
		@Override
		public int hashCode(){
			return this.server.hashCode()*31 + this.prio;
		}
		
		@Override
		public String toString() {
			return this.server;
		}
		
		/**
		 * Get the MX priority
		 * 
		 * @return priority
		 */
		public int getPriority(){
			return this.prio;
		}
		
		/**
		 * Get server name
		 * 
		 * @return server name
		 */
		public String getServer(){
			return this.server;
		}
	}
	
	/**
	 * Get the mail servers for the given domain name
	 * 
	 * @param domain
	 * @return list of mail servers, ordered by the DNS priority
	 */
	public static List<MXRecord> getMXServers(final String domain){
		 final Hashtable<String, String> env = new Hashtable<String, String>();
		 env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
		 
		 try{
			 final DirContext ictx = new InitialDirContext( env );
			 final Attributes attrs = ictx.getAttributes( domain, new String[] { "MX" });
		 
			 final Attribute attr = attrs.get( "MX" );
		 
			 if( attr == null || attr.size() == 0){
				 return null;
			 }
		 
			 final List<MXRecord> ret = new ArrayList<MXRecord>(attr.size());
			 
			 for (int i=0; i<attr.size(); i++){
				 try{
					 final MXRecord record = new MXRecord(attr.get(i).toString());
				 
					 ret.add(record);
				 }
				 catch (Throwable t){
					 // ignore
				 }
			 }
			 
			 Collections.sort(ret);
			 
			 return ret;
		 }
		 catch (Throwable e){
			// nothing 
		 }
		 
		 return null;
	}
}
