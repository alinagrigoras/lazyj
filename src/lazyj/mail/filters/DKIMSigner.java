/**
 * 
 */
package lazyj.mail.filters;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import sun.misc.BASE64Encoder;

import lazyj.mail.Mail;
import lazyj.mail.MailFilter;
import lazyj.mail.Sendmail;

/**
 * @author costing
 * @since Sep 15, 2009
 */
public class DKIMSigner implements MailFilter {

	/**
	 * Default mail headers to look for
	 */
	private static final List<String> DEFAULT_HEADERS = new ArrayList<String>(29);
	
	static {
		DEFAULT_HEADERS.add("Content-Description");
		DEFAULT_HEADERS.add("Content-ID");
		DEFAULT_HEADERS.add("Content-Type");
		DEFAULT_HEADERS.add("Content-Transfer-Encoding");
		DEFAULT_HEADERS.add("CC");
		
		DEFAULT_HEADERS.add("Date");
		DEFAULT_HEADERS.add("From");
		DEFAULT_HEADERS.add("In-Reply-To");
		DEFAULT_HEADERS.add("List-Subscribe");
		DEFAULT_HEADERS.add("List-Post");
		
		DEFAULT_HEADERS.add("List-Owner");
		DEFAULT_HEADERS.add("List-Id");
		DEFAULT_HEADERS.add("List-Archive");
		DEFAULT_HEADERS.add("List-Help");
		DEFAULT_HEADERS.add("List-Unsubscribe");
		
		DEFAULT_HEADERS.add("MIME-Version");
		DEFAULT_HEADERS.add("Message-ID");
		DEFAULT_HEADERS.add("Resent-Sender");
		DEFAULT_HEADERS.add("Resent-Cc");
		DEFAULT_HEADERS.add("Resent-Date");
		
		DEFAULT_HEADERS.add("Resent-To");
		DEFAULT_HEADERS.add("Reply-To");
		DEFAULT_HEADERS.add("References");
		DEFAULT_HEADERS.add("Resent-Message-ID");
		DEFAULT_HEADERS.add("Resent-From");
		
		DEFAULT_HEADERS.add("Sender");
		DEFAULT_HEADERS.add("Subject");
		DEFAULT_HEADERS.add("To");
		DEFAULT_HEADERS.add("X-Mailer");
	}
	
	/**
	 * What is the set of mail headers to look for
	 */
	private LinkedHashSet<String> headers = new LinkedHashSet<String>(DEFAULT_HEADERS);
	
	/**
	 * Message digester
	 */
	private MessageDigest digester;
	
	/**
	 * Message signer
	 */
	private Signature signer;
	
	/**
	 * Domain name
	 */
	private String domain;
	
	/**
	 * DNS selector (prefix)
	 */
	private String dnsSelector;
	
	/**
	 * DKIM signer
	 * 
	 * @param sDomain
	 * @param sDNSSelector
	 * @param sKeyPath path to the file containing the private RSA key 
	 * @throws IOException when the key file cannot be read
	 * @throws NoSuchAlgorithmException if the algorithm is unknown
	 * @throws InvalidKeySpecException if the contents of the private key file is not ok
	 * @throws InvalidKeyException when the signature cannot make use of the given private key 
	 */
	public DKIMSigner(final String sDomain, final String sDNSSelector, final String sKeyPath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
		this.domain = sDomain;
		
		this.dnsSelector = sDNSSelector;
		
		final File privKeyFile = new File(sKeyPath);
		
		final DataInputStream dis = new DataInputStream(new FileInputStream(privKeyFile));
		
		final byte[] privKeyBytes; 
		
		try{
			privKeyBytes = new byte[(int) privKeyFile.length()];
			dis.read(privKeyBytes);
			dis.close();
		}
		finally{
			dis.close();
		}
		
		final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		
		final PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privKeyBytes);
		
		final PrivateKey privateKey = keyFactory.generatePrivate(privSpec);
		
		this.digester = MessageDigest.getInstance("sha-256");
		
		this.signer = Signature.getInstance("SHA256withRSA");
		this.signer.initSign(privateKey);
	}
	
	/**
	 * Add another header to the signature
	 * 
	 * @param sHeader
	 * @return true if it was really added, false if it was already in the list
	 */
	public boolean addHeader(final String sHeader){
		return this.headers.add(sHeader);
	}
	
	/**
	 * Remove a header from the signature
	 * 
	 * @param sHeader
	 * @return true if it was really removed, false if it was not defined
	 */
	public boolean removeHeader(final String sHeader){
		return this.headers.remove(sHeader);
	}
	
	/* (non-Javadoc)
	 * @see lazyj.mail.MailFilter#filter(java.util.Map, java.lang.String, java.lang.String, lazyj.mail.Mail)
	 */
	@Override
	public void filter(final Map<String, String> mailHeaders, final String sBody, final Mail mail) {
		final Map<String, String> fields = new LinkedHashMap<String, String>();
		
		fields.put("v", "1");
		fields.put("a", "rsa-sha256");
		fields.put("q", "dns/txt");
		fields.put("t", String.valueOf(System.currentTimeMillis()/1000));
		fields.put("s", this.dnsSelector);
		fields.put("d", this.domain);
		fields.put("l", String.valueOf(sBody.length()));
		fields.put("c", "relaxed/simple");
		
		final List<String> foundHeaders = new LinkedList<String>();
		
		final StringBuilder sbHeader = new StringBuilder(1024);
		
		for (String header: this.headers){
			if (mailHeaders.containsKey(header)){
				foundHeaders.add(header);
				
				sbHeader.append(relaxedHeader(header, mailHeaders.get(header))).append(Sendmail.CRLF);
			}
		}
		
		String hField = "";
		
		for (String header: foundHeaders){
			if (hField.length()>0)
				hField+=": ";
			
			hField += header.toLowerCase();
		}
		
		fields.put("h", hField);
		
		fields.put("bh", base64Encode(this.digester.digest(simpleBody(sBody).getBytes())));
		
		fields.put("b", "");
		
		final String DKIM = "DKIM-Signature";
		
		final StringBuilder sbValue= new StringBuilder(256);
		
		for (Map.Entry<String, String> me: fields.entrySet()){
			if (sbValue.length()>0)
				sbValue.append("; ");
			
			sbValue.append(me.getKey()).append('=').append(me.getValue());
		}
		
		final String sKeyValue = sbValue.toString();
		
		sbHeader.append(relaxedHeader(DKIM, sKeyValue)).append(Sendmail.CRLF);
		
		try{
			this.signer.update(sKeyValue.getBytes());
			byte[] signedSignature = this.signer.sign();
			
			mailHeaders.put(DKIM, sKeyValue+base64Encode(signedSignature));
		}
		catch (SignatureException se){
			// ignore
		}
	}

	/**
	 * Implement the "relaxed" method for headers
	 * 
	 * @param key header key
	 * @param value value
	 * @return canonical value
	 */
	public static String relaxedHeader(final String key, final String value){
		return key.toLowerCase()+":"+value.replaceAll("\\s+", " ").trim();
	}
	
	/**
	 * Implement the "simple" method for body
	 * 
	 * @param body
	 * @return canonical value
	 */
	public static String simpleBody(final String body){
		if (body == null || "".equals(body) ) {
			return Sendmail.CRLF;
		}
		
		if (!Sendmail.CRLF.equals(body.substring(body.length()-2, body.length()))) {
			return body+Sendmail.CRLF;
		}
		
		String ret = body;
		
		while ("\r\n\r\n".equals(ret.substring(ret.length()-4, ret.length()))) {
			ret = ret.substring(0, ret.length()-2);
		}
		
		return ret;
	}
	
	/**
	 * Encode a byte array
	 * 
	 * @param b bytes to encode
	 * @return BASE64-encoding
	 */
	public static String base64Encode(final byte[] b){
		final BASE64Encoder base64Enc = new BASE64Encoder();
		
		String encoded = base64Enc.encode(b);
		encoded = encoded.replace("\n", "");
		return encoded.replace("\r", "");
	}
}
