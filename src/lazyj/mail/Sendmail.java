package lazyj.mail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import lazyj.Format;
import lazyj.Log;


import static lazyj.Format.hexChar;

import com.oreilly.servlet.Base64Encoder;

/**
 * Class for sending mails.
 * 
 * @author costing
 * @since 2006-10-06
 * @see Mail
 */
public class Sendmail {

	/**
	 * What is the current limit for a line. If the line is longer it will be split
	 * with '=' at the end and will be continued on the next line. 
	 */
	public static final int LINE_MAX_LENGTH = 75;
	
	/**
	 * Boundary to use when separating body parts. Can be anything else you like ...
	 */
	private static final String	sBoundaryInit		= "----=_NextPart_000_0010_";

	/**
	 * Everything was ok when sending.
	 */
	public final static int		SENT_OK				= 0;

	/**
	 * There are some warnings, but the mail was sent.
	 */
	public final static int		SENT_WARNING		= 1;

	/**
	 * The mail could not be sent.
	 */
	public final static int		SENT_ERROR			= 2;

	/**
	 * Complete email address of the sender
	 */
	private String					sFullUserEmail;

	/**
	 * Boundary to use
	 */
	private String					sBoundary;

	/**
	 * Socket on which we are talking to the SMTP server
	 */
	private Socket					sock				= null;

	/**
	 * Write to SMTP server
	 */
	private PrintWriter			sock_out			= null;

	/**
	 * Read from SMTP server
	 */
	private BufferedReader			sock_in				= null;

	/**
	 * Result of the sending operation.
	 */
	public int					iSentOk				= 0;

	/**
	 * Reason of the failure
	 */
	public String					sError				= "";

	/**
	 * Recipient addresses that were rejected by the mail server
	 */
	public List<String>			lInvalidAddresses	= new LinkedList<String>();

	/**
	 * Server that is used to deliver the mails through
	 */
	private String					sServer;

	/**
	 * Server port
	 */
	private int					iPort;

	/**
	 * What is an CRLF ?
	 */
	private static final String CRLF = "\r\n";
	
	/**
	 * The simplest constructor. It only need the email address of the sender.
	 * The default server that will be used is 127.0.0.1:25.
	 * 
	 * @param sFrom sender email address
	 */
	public Sendmail(final String sFrom) {
		this(sFrom, "127.0.0.1");
	}

	/**
	 * Constructor used to specify also the server to send this mail through.
	 * 
	 * @param sFrom sender mail address
	 * @param sServerAddress server to send the mails through
	 */
	public Sendmail(final String sFrom, final String sServerAddress) {
		this(sFrom, sServerAddress, 25);
	}

	/**
	 * Full-options constructor.
	 * 
	 * @param sFrom sender mail address
	 * @param sServerAddress server to send the mails through
	 * @param iServerPort server port
	 */
	public Sendmail(final String sFrom, final String sServerAddress, final int iServerPort) {
		this.sServer = sServerAddress;
		this.iPort = iServerPort;

		this.sFullUserEmail = sFrom;
	}

	/**
	 * Split a recipient field and extract a list of addresses from it.
	 * 
	 * @param adr a list of addresses, separated by ',' or ';'
	 * @return a list of addresses
	 */
	private List<String> adrFromString(final String adr) {
		if ((adr == null) || (adr.length() <= 0))
			return null;
		
		final StringTokenizer st = new StringTokenizer(adr, ",;");
		final List<String> l = new LinkedList<String>();
		
		while (st.hasMoreTokens()) {
			final String sAdresa = Format.extractAddress(st.nextToken().trim());

			if (sAdresa!=null && sAdresa.indexOf("@") >= 1)
				l.add(sAdresa);
		}
		return l;
	}

	/**
	 * Extract all the destination email addresses by parsing the To, CC and BCC fields.
	 * 
	 * @param mail a mail from which to extract all destination mails
	 * @return an Iterator over the adresses
	 */
	private Iterator<String> addresses(final Mail mail) {
		List<String> adr = new LinkedList<String>();
		List<String> lTemp = adrFromString(mail.sTo);
		if ((lTemp != null) && (!lTemp.isEmpty()))
			adr.addAll(adrFromString(mail.sTo));

		lTemp = adrFromString(mail.sCC);
		if ((lTemp != null) && (!lTemp.isEmpty()))
			adr.addAll(adrFromString(mail.sCC));

		lTemp = adrFromString(mail.sBCC);
		if ((lTemp != null) && (!lTemp.isEmpty()))
			adr.addAll(adrFromString(mail.sBCC));

		return adr.iterator();
	}
	
	/**
	 * Override for HELO
	 */
	private String sHELOOverride = null;
	
	/**
	 * Set an override to HELO SMTP command. By default the string that is sent 
	 * is extracted from the "From" header, taking only the string after "@".
	 * A <code>null</code> value will return to the default behavior.
	 * 
	 * @param s string to send to the SMTP server
	 * @return old value of the override
	 */
	public String setHELO(final String s){
		final String sOld = this.sHELOOverride;
		
		if (s==null || s.trim().length()==0)
			this.sHELOOverride = null;
		else
			this.sHELOOverride = s;
		
		return sOld;
	}

	/**
	 * Override for Mail From
	 */
	private String sMAILFROMOverride = null;
	
	/**
	 * Set an override to the MAIL FROM SMTP command. By default the string that is sent
	 * is either the "Return-Path" ({@link MailHeader#sReturnPath}, if this is not specified, the "From"
	 * ({@link MailHeader#sFrom}) field.
	 * 
	 * A value of <code>null</code> will return the code to the default behavior.
	 * 
	 * @param s email address to give to MAIL FROM
	 * @return old value of the override
	 */
	public String setMAILFROM(final String s){
		final String sOld = this.sMAILFROMOverride;
		
		if (s==null || s.trim().length()==0)
			this.sMAILFROMOverride = null;
		else
			this.sMAILFROMOverride = s;
		
		return sOld;
	}
	
	/**
	 * Is debugging enabled ?
	 */
	private boolean bDebug = false;
	
	/**
	 * Enable debugging (printing all lines of the email to the standard error).
	 * 
	 * @param bDebug debug flag
	 */
	public void setDebug(final boolean bDebug){
		this.bDebug = bDebug;
	}
	
	/**
	 * Send something to the server
	 * 
	 * @param sText
	 */
	private void print(final String sText){
		if (this.bDebug)
			System.err.println("Sendmail: text > "+sText);
		
		this.sock_out.print(sText);
		this.sock_out.flush();
	}
	
	/**
	 * Send text + new line
	 * 
	 * @param sLine
	 */
	private void println(final String sLine){
		print(sLine+CRLF);
	}
	
	/**
	 * Read SMTP server response
	 * 
	 * @return server response
	 * @throws IOException
	 */
	private String readLine() throws IOException {
		final String sLine = this.sock_in.readLine();
		
		if (this.bDebug)
			System.err.println("Sendmail: read < "+sLine);
		
		return sLine;
	}
	
	/**
	 * Initial communication with the server. Sends all the command until the "data" section.
	 * 
	 * @param mail the mail that is sent
	 * @return true if everything is ok, false if there was an error
	 */
	private boolean init(final Mail mail) {
		this.sBoundary = sBoundaryInit + System.currentTimeMillis() + "." + r.nextLong();

		try {
			try {
                this.sock = new Socket();
                this.sock.connect(new InetSocketAddress(this.sServer, this.iPort), 10000);
                this.sock.setSoTimeout(20000);
				this.sock_out = new PrintWriter(new OutputStreamWriter(this.sock.getOutputStream(), mail.charSet), true);
				this.sock_in = new BufferedReader(new InputStreamReader(this.sock.getInputStream()));
			} catch (UnknownHostException e) {
				Log.log(Log.ERROR, "lazyj.mail.Sendmail", "init : unknown host " + this.sServer);
				this.iSentOk = SENT_ERROR;
				this.sError = "could not connect to the mail server!";
				return false;
			} catch (IOException e) {
				Log.log(Log.ERROR, "lazyj.mail.Sendmail", "init : IOException (unable to establish datalink, check your server)");
				this.iSentOk = SENT_ERROR;
				this.sError = "could not connect to the mail server!";
				return false;
			}
			String line1 = readLine();
			if (line1 == null || !line1.startsWith("220")) {
				Log.log(Log.ERROR, "lazyj.mail.Sendmail", "init : unexpected response from server (didn't respond with 220...)");
				this.iSentOk = SENT_ERROR;
				this.sError = "unexpected mail server response: " + line1;
				println("QUIT");
				return false;
			}
			
			final String sFrom = Format.extractAddress(this.sFullUserEmail);
			
			if (sFrom==null){
				this.iSentOk = SENT_ERROR;
				this.sError = "incorrect FROM field";
				println("QUIT");
				return false;
			}
			
			String sServerName = sFrom.substring(sFrom.indexOf("@")+1);
			
			if (this.sHELOOverride!=null)
				sServerName = this.sHELOOverride;
			
			println("HELO "+sServerName);
			line1 = readLine();
			if (line1 == null || !line1.startsWith("250")) {
				Log.log(Log.ERROR, "lazyj.mail.Sendmail", "init : error after HELO");
				this.iSentOk = SENT_ERROR;
				this.sError = "error after telling server HELO "+sServerName+": " + line1;
				println("QUIT");
				return false;
			}
			
			String sBounce = sFrom;
			
			if (mail.sReturnPath!=null && mail.sReturnPath.length()>0){
				sBounce = Format.extractAddress(mail.sReturnPath);
				
				if (sBounce==null)
					sBounce = sFrom;
			}
			
			if (this.sMAILFROMOverride!=null)
				sBounce = this.sMAILFROMOverride;
			
			println("MAIL FROM: " + sBounce);
			line1 = readLine();
			if (line1 == null || !line1.startsWith("250")) {
				Log.log(Log.ERROR, "lazyj.mail.Sendmail", "init : error after telling server MAIL FROM: " + sBounce, line1);
				this.iSentOk = SENT_ERROR;
				this.sError = "error after telling server `MAIL FROM: " + sBounce + "` : " + line1;
				println("QUIT");
				return false;
			}

			Iterator<String> itAdrese = addresses(mail);
			int iCount = 0;
			while (itAdrese.hasNext()) {
				String sCurrentAddr = itAdrese.next();
				println("RCPT TO: " + sCurrentAddr);
				line1 = readLine();

				if (line1 == null || !line1.startsWith("250")) {
					Log.log(Log.ERROR, "lazyj.mail.Sendmail", "init : error telling RCPT TO '" + sCurrentAddr + "' : " + line1);

					println("QUIT");
					this.iSentOk = SENT_WARNING;
					this.lInvalidAddresses.add(sCurrentAddr);
				} else {
					iCount++;
				}
			}
			
			if (iCount==0)
				return false;
			
		} catch (IOException e) {
			this.iSentOk = SENT_ERROR;
			this.sError = "IOException : " + e.getMessage();
			return false;
		}
		return true;
	}

	/**
	 * A random number generator
	 */
	private static final Random	r = new Random(System.currentTimeMillis());

	/**
	 * Send mail's headers
	 * 
	 * @param mail mail to be sent
	 * @return true if everything is ok, false if there was an error
	 */
	private boolean headers(final Mail mail) {
		try {
			println("DATA");
			String line1 = readLine();
			if (line1 == null || !line1.startsWith("354")) {
				Log.log(Log.ERROR, "lazyj.mail.Sendmail", "headers : error telling server DATA: " + line1);
				this.iSentOk = SENT_ERROR;
				this.sError = "error telling server DATA: " + line1;
				println("QUIT");
				return false;
			}

			final StringBuilder sbHeaders = new StringBuilder();
			
			//"Return-Path: " + (mail.sReturnPath != null ? mail.sReturnPath : this.sFullUserEmail) + CRLF;
			
			sbHeaders.append("Date: ").append(new MailDate(new Date()).toMailString()).append(CRLF);
			
			sbHeaders.append("From: ").append(this.sFullUserEmail).append(CRLF);
			
			sbHeaders.append("To: ").append(mail.sTo).append(CRLF);

			if (mail.sCC != null && mail.sCC.length() > 0)
				sbHeaders.append("CC: ").append(mail.sCC).append(CRLF);

			if (mail.sReplyTo != null && mail.sReplyTo.length() > 0)
				sbHeaders.append("Reply-To: ").append(mail.sReplyTo).append(CRLF);

			sbHeaders.append("Message-ID: <").append(System.currentTimeMillis()).append("-").append(r.nextLong()).append("@lazyj>").append(CRLF).
						append("Subject: ").append(mail.sSubject).append(CRLF).
						append("X-Priority: ").append(mail.iPriority).append(CRLF).
						append("MIME-Version: 1.0").append(CRLF).
						append("X-Mailer: LazyJ.sf.net").append(CRLF);

			final Iterator<Map.Entry<String, String>> it = mail.hmHeaders.entrySet().iterator();

			while (it.hasNext()) {
				final Map.Entry<String, String> me = it.next();

				sbHeaders.append(me.getKey()).append(": ").append(me.getValue()).append(CRLF);
			}

			if (mail.bRequestRcpt)
				sbHeaders.append("Disposition-Notification-To: ").append(this.sFullUserEmail).append(CRLF);

			if (!mail.bConfirmation) {
				if (mail.hasAttachments())
					sbHeaders.append("Content-Type: multipart/mixed;").append(CRLF);
				else
					sbHeaders.append("Content-Type: multipart/alternative;").append(CRLF);
			} else {
				sbHeaders.append("References: <").append(mail.sOrigMessageID).append(">").append(CRLF).
						append("Content-Type: multipart/report;").append(CRLF).append("	report-type=disposition-notification;").append(CRLF);
			}

			sbHeaders.append("        boundary=\"").append(this.sBoundary).append("\"").append(CRLF).append(CRLF).append("This message is in MIME format.").append(CRLF);

			sbHeaders.append(CRLF);
			
			print(sbHeaders.toString());
		} catch (Throwable e) {
			this.iSentOk = SENT_ERROR;
			this.sError = "Exception : " + e.getMessage();
			return false;
		}
		return true;
	}

	/**
	 * Write one of the mail text parts.
	 * 
	 * @param mail mail to be sent
	 * @param bHtmlPart true to write the HTML part, false to write the plain text part
	 * @return true if everything is ok, false if there was an error
	 */
	private boolean writeBody(final Mail mail, final boolean bHtmlPart) {
		String line1;
		
		line1 = "--" + this.sBoundary + CRLF;

		if (bHtmlPart) {
			if ((mail.sHTMLBody == null) || (mail.sHTMLBody.length() <= 0))
				return true;

			line1 += "Content-Type: text/html; charset=" + (mail.sContentType != null && mail.sContentType.length() > 0 ? mail.sContentType : "iso-8859-1");
		} else {
			if ((mail.sBody == null) || (mail.sBody.length() <= 0))
				return true;

			line1 += "Content-Type: text/plain; charset=" + (mail.sContentType != null && mail.sContentType.length() > 0 ? mail.sContentType : "iso-8859-1");
		}

		line1 += CRLF + "Content-Transfer-Encoding: quoted-printable"+CRLF;
		println(line1);
		mail.sEncoding = "quoted-printable";

		try {
			line1 = bodyProcess(bHtmlPart ? mail.sHTMLBody : mail.sBody, (mail.sEncoding.length() > 0));
		} catch (Exception e) {
			Log.log(Log.ERROR, "lazyj.mail.Sendmail", "writeBody : bodyProcess error : sBody ("+(bHtmlPart ? "html" : "text")+" : '"+ mail.sBody+"'", e);
			line1 = mail.sBody;
		}

		StringTokenizer st = new StringTokenizer(line1, "\n", true);

		int count = 1;

		while (st.hasMoreTokens()) {
			String l = st.nextToken();

			if (l.equals("\n")) {
				count++;
				if (count % 2 == 0)
					continue;
			} else
				count = 1;

			if (l.indexOf("\r\n") >= 0)
				l = l.substring(0, l.indexOf("\r\n"));
			if (l.indexOf("\n") >= 0)
				l = l.substring(0, l.indexOf("\n"));

			if (l.equals("."))
				println("..");
			else
				println(l);
		}

		return true;
	}

	/**
	 * Finish sending the mail.
	 * 
	 * @param mail mail to be sent
	 * @return true if everything was ok, false on any error
	 */
	private boolean writeEndOfMail(final Mail mail) {
		try {
			println("--" + this.sBoundary + "--");

			println(".");

			String line1 = readLine();
			if (line1 == null || !line1.startsWith("250")) {
				Log.log(Log.ERROR, "lazyj.mail.Sendmail", "writeEndOfMail : error sending the mail : " + line1);
				println("QUIT");
				this.sError = line1;
				return false;
			}

			println("QUIT");

			this.sock_in.close();
			this.sock_out.close();
		} catch (IOException e) {
			Log.log(Log.FATAL, "lazyj.mail.Sendmail", "writeEndOfMail" + e);
			this.iSentOk = SENT_ERROR;
			this.sError = "IOException : " + e.getMessage();
			return false;
		} catch (Exception e) {
			Log.log(Log.FATAL, "lazyj.mail.Sendmail", "writeEndOfMail" + e);
			this.iSentOk = SENT_ERROR;
			this.sError = "Exception : " + e.getMessage();
			return false;
		}

		return true;
	}

	/**
	 * Attach a file to this mail
	 * 
	 * @param sFileName a file that is to be attached to this mail 
	 * @return true if everything is ok, false on any error
	 */
	private boolean writeFileAttachment(final String sFileName) {
		String sRealFile = sFileName;

		final File f = new File(sRealFile);
		if (!f.exists() || !f.isFile() || !f.canRead()) {
			Log.log(Log.WARNING, "lazyj.mail.Sendmail", "writeFileAttachment : can't read from : " + sRealFile);
			this.iSentOk = SENT_ERROR;
			this.sError = "cannot attach : " + sFileName;
			return false;
		}

		BufferedInputStream in = null;
		try {
			in = new BufferedInputStream(new FileInputStream(sRealFile));
		} catch (IOException e) {
			Log.log(Log.ERROR, "lazyj.mail.Sendmail", "writeFileAttachment" + e);
			this.iSentOk = SENT_ERROR;
			this.sError = "exception while attaching `" + sFileName + "` : " + e.getMessage();
			return false;
		}

		final boolean b = writeAttachment(in, sFileName);

		try {
			in.close();
		} catch (IOException e) {
			// ignore close exception
		}

		return b;
	}

	/**
	 * Actually encode the attachment.
	 * 
	 * @param in stream with the file contents
	 * @param sFileName file name to be put in the attachment's headers
	 * @return true if everything is ok, false on any error
	 */
	private boolean writeAttachment(final InputStream in, final String sFileName) {

		String sStrippedFileName = sFileName;

		final StringTokenizer st = new StringTokenizer(sFileName, "/\\:");

		while (st.hasMoreTokens())
			sStrippedFileName = st.nextToken();

		String sAttachHeader = "--" + this.sBoundary + CRLF + "Content-Type: " + FileTypes.getMIMETypeOf(sFileName) + ";" + CRLF + "        name=\"" + sStrippedFileName + "\"" + CRLF + "Content-Transfer-Encoding: base64" + CRLF +
		// "Content-ID: <MyMail.JavaServlets.1.1."+(new Random()).nextInt(31)+">\r\n"+
		"Content-Disposition: attachment;"+ CRLF + "        filename=\"" + sStrippedFileName + "\""+CRLF;

		println(sAttachHeader);

		Base64Encoder encoder = null;

		try {
			encoder = new Base64Encoder(new BufferedOutputStream(this.sock.getOutputStream()));

			byte[] buf = new byte[4 * 1024]; // 4K buffer
			int bytesRead;

			while ((bytesRead = in.read(buf)) != -1) {
				encoder.write(buf, 0, bytesRead);
			}
			
			encoder.flush();
		} catch (Exception e) {
			Log.log(Log.FATAL, "lazyj.mail.Sendmail", "writeAttachment" + e);
			this.iSentOk = SENT_ERROR;
			this.sError = "exception while writing an attachment : " + e.getMessage();
			return false;
		}
		finally{
			if (in != null)
				try{
					in.close();
				}
				catch (IOException e){
					// ignore
				}
			
			if (encoder != null){
				try{
					encoder.close();
				}
				catch (IOException e){
					// ignore
				}
			}
		}

		println("");
		return true;
	}

	/**
	 * Iterate through all the files that should be attached to this mail and process them.
	 * 
	 * @param mail mail to be sent
	 * @return true if everything is ok, false on any error
	 */
	private boolean processAttachments(final Mail mail) {
		final StringTokenizer st = new StringTokenizer(mail.sAttachedFiles, ";");
		while (st.hasMoreTokens()) {
			if (!writeFileAttachment(st.nextToken()))
				return false;
		}

		if ((mail.lAttachments != null) && (!mail.lAttachments.isEmpty())) {
			final Iterator<Attachment> itAt = mail.lAttachments.iterator();
			while (itAt.hasNext()) {
				final Attachment at = itAt.next();
				if (at.sFileName.length() > 0)
					writeAttachment(at.getDecodedInputStream(), at.sFileName);
			}
		}

		return true;
	}

	/**
	 * If this is a confirmation to a previously received mail, this method writes the actual response.
	 * 
	 * @param mail mail to be sent
	 */
	private void writeConfirmation(final Mail mail) {	
		println("--" + this.sBoundary + CRLF + "Content-Type: message/disposition-notification"+CRLF+"Content-Transfer-Encoding: 7bit"+CRLF+"Content-Disposition: inline"+CRLF+CRLF);
		println(mail.sConfirmation);
	}

	/**
	 * Send the given mail. This method will return true if everything is ok, false on any error.
	 * You can later check {@link #sError} to see what went wrong.
	 * 
	 * @param mail mail to be sent
	 * @return true if everything is ok, false on any error.
	 */
	public boolean send(final Mail mail) {
		if (!(init(mail) && headers(mail) && writeBody(mail, false) && writeBody(mail, true))) {
			return false;
		}

		if (mail.hasAttachments()) {
			if (!processAttachments(mail)) {
				return false;
			}
		}

		if (mail.bConfirmation) {
			writeConfirmation(mail);
		}

		if (!writeEndOfMail(mail)) {
			return false;
		}

		return true;
	}

	/**
	 * Encode a text part to put it into the final mail
	 * 
	 * @param sOrig body part to add
	 * @param bStripCodes whether or not to encode some special characters
	 * @return transformed String for this text part, ready to be put as it is into the mail
	 */
	private static final String bodyProcess(final String sOrig, final boolean bStripCodes) {		
		String BD = Format.replace(sOrig, "\r\n", "\n");
		BD = Format.replace(BD, "\n\n", "\n \n");

		StringBuilder sbBody = new StringBuilder(BD.length() + 2000);

		if (bStripCodes) {
			int i, len = BD.length();
			for (i = 0; i < len; i++) {
				char c = BD.charAt(i);
				if (c > 127 || c == '=') {
					byte[] vb = Character.valueOf(c).toString().getBytes();
					
					for (byte b : vb)
						sbBody.append('=').append(hexChar((b >>> 4) & 0x0F)).append(hexChar(b & 0x0F));
				}
				else
					sbBody.append(c);
			}
			BD = sbBody.toString();

			while ((i = BD.indexOf(" \n")) >= 0) {
				BD = BD.substring(0, i) + "=20\n" + BD.substring(i + 2);
			}
		}

		sbBody = new StringBuilder(BD.length() + 500);

		StringTokenizer st1 = new StringTokenizer(BD, "\n");

		StringBuilder sbResultPartial;
		String sTemp1;

		while (st1.hasMoreTokens()) {
			sTemp1 = st1.nextToken();

			sbResultPartial = new StringBuilder(sTemp1.length() + 20);

			while ((sTemp1.length() > 0) && (sTemp1.charAt(sTemp1.length() - 1) == ' '))
				sTemp1 = sTemp1.substring(0, sTemp1.length() - 1);

			if ((sTemp1.length() == 0) || ((sTemp1.length() == 1) && (sTemp1.charAt(0) == 13)))
				sTemp1 = "";

			StringTokenizer st2 = new StringTokenizer(sTemp1, " ;,!", true);
			int size = 0;
			while (st2.hasMoreTokens()) {
				String sTemp2 = st2.nextToken();
				if (size + sTemp2.length() < LINE_MAX_LENGTH) {
					sbResultPartial.append(sTemp2);
					size += sTemp2.length();
				} else {
					if (sbResultPartial.length() > 0) {
						if (bStripCodes)
							sbResultPartial.append("=\n");
						else
							sbResultPartial.append("\n");
					}

					while (sTemp2.length() > LINE_MAX_LENGTH) {
						String s = sTemp2.substring(0, LINE_MAX_LENGTH);
						
						if (s.lastIndexOf("=") > LINE_MAX_LENGTH - 4)
							s = s.substring(0, s.lastIndexOf("="));

						sbResultPartial.append(s);
						if (bStripCodes)
							sbResultPartial.append("=\n");
						else
							sbResultPartial.append("\n");
						
						sTemp2 = sTemp2.substring(s.length());
					}

					sbResultPartial.append(sTemp2);

					size = sTemp2.length();
				}
			}

			// liniile care incep cu "."

			String sResultPartial = sbResultPartial.toString();

			if (sResultPartial.startsWith("."))
				sResultPartial = "." + sResultPartial;

			int pDot = 0;
			while ((pDot = sResultPartial.indexOf("\n.", pDot)) >= 0) {
				pDot = sResultPartial.indexOf(".", pDot);
				sResultPartial = sResultPartial.substring(0, pDot) + "." + sResultPartial.substring(pDot);
				pDot++;
			}

			sbBody.append(sResultPartial).append("\n");
		}

		return sbBody.toString();
	}
	
	/**
	 * Convenience mail for quick sending of emails. It will take the "From" field from the given {@link Mail} object.
	 * 
	 * @param m the mail to send. Remember to fill at least {@link MailHeader#sFrom}, {@link MailHeader#sTo} and some {@link Mail#sBody}.
	 * @return true if the sending was ok, false otherwise
	 */
	public static boolean quickSend(final Mail m){
		return (new Sendmail(m.sFrom)).send(m);
	}

}
