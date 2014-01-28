package lazyj.mail;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import lazyj.Format;


/**
 * Wrapper for the global mail headers that extracts typical fields into the class fields.
 * 
 * @author costing
 * @since 2006-10-06
 */
public class MailHeader extends Header implements Comparable<MailHeader> {
	/**
	 * Sender of the mail
	 */
	public String					sFrom;

	/**
	 * Just the name part of the sender
	 */
	public String					sShortFrom;

	/**
	 * Destination(s) for this mail
	 */
	public String					sTo;

	/**
	 * Carbon copies of this mail
	 */
	public String					sCC;

	/**
	 * Blind carbon copies
	 */
	public String					sBCC;

	/**
	 * Mail subject
	 */
	public String					sSubject;

	/**
	 * Content type
	 */
	public String					sContentType;

	/**
	 * Where to ask the receiver to send the replies to
	 */
	public String					sReplyTo;

	/**
	 * Where to ask the servers on the way to report errors in case that the delivery is not possible
	 */
	public String					sReturnPath;

	/**
	 * Encoding used to embed the text / attachments / ...
	 */
	public String					sEncoding;

	/**
	 * Where to send the notification that this mail was read
	 */
	public String					sNotification;

	/**
	 * Message ID, used to thread emails
	 */
	public String					sMessageID;

	/**
	 * The Date field of the header
	 */
	public MailDate					mdDate;

	/**
	 * Unique mail ID; set by higher-level code
	 */
	public int						iMailID;

	/**
	 * Mail status; set by higher-level code (0=new, 1=read)
	 */
	public int						iStatus;

	/**
	 * True if this mail was displayed before, false otherwise; set by higher-level code. 
	 */
	public boolean					bOld;

	/**
	 * Folder ID; set by higher-level code
	 */
	public int						iFolder;

	/**
	 * Total size of this mail; set by higher-level code.
	 */
	public int						iMailSize;

	/**
	 * Message priority.
	 */
	public int						iPriority;

	/**
	 * This only an assumption about the attachment status. This assumption is based on the content-type. When
	 * an email has attachments it typically has some content-types, that are not there when the mail does not
	 * contain any attachments. Since this assumption can be broken by some mail clients, use this only as an
	 * indication. It is good for example to display a special icon in folder listings, since it doesn't hurt,
	 * and it doesn't actually take time to decode the mail.
	 */
	public boolean					bEstimatedHasAttachments;

	/**
	 * If this is a read confirmation of a previously sent mail. 
	 */
	public boolean					bConfirmation;

	/**
	 * The text to send when confirming the read of an email
	 */
	public String					sConfirmation;

	/**
	 * The original message ID that is confirmed to be read
	 */
	public String					sOrigMessageID;

	/**
	 * Whether or not to request read confirmation from the recipients of this mail 
	 */
	public boolean					bRequestRcpt;

	/**
	 * Supplemental headers to be added when sending the mail
	 */
	public HashMap<String, String>	hmHeaders	= new HashMap<>();

	/**
	 * Add an extra header to be put in the mail that is sent.  
	 * 
	 * @param sKey header name
	 * @param sValue value for this key
	 */
	public void addHeader(final String sKey, final String sValue) {
		this.hmHeaders.put(sKey, sValue);
	}

	/**
	 * Empty constructor, initialize fields to default values.
	 */
	@SuppressWarnings("nls")
	public MailHeader() {
		super("");
		this.sFrom = "";
		this.sShortFrom = "";
		this.sTo = "";
		this.sCC = "";
		this.sBCC = "";
		this.sSubject = "";
		this.sContentType = "";
		this.sReplyTo = "";
		this.sEncoding = "";
		this.sNotification = "";
		this.sMessageID = "";
		this.mdDate = new MailDate(new Date());
		this.iMailID = 0;
		this.iFolder = 0;
		this.iMailSize = 0;
		this.iStatus = 0;
		this.iPriority = 3;
		this.bEstimatedHasAttachments = false;
		this.bConfirmation = false;
		this.sOrigMessageID = "";
	}

	/**
	 * Initialize the fields based on this actual header.
	 * 
	 * @param sHeader original mail header
	 */
	@SuppressWarnings("nls")
	public MailHeader(final String sHeader) {
		super(sHeader);
		this.sFrom = getValue("From");
		this.sShortFrom = Format.extractMailTitle(this.sFrom);
		this.sTo = getValue("To");
		this.sSubject = getValue("Subject");
		this.sCC = getValue("Cc");
		this.sBCC = getValue("BCC");
		this.sReplyTo = getValue("Reply-To");
		if (this.sFrom.length() <= 0)
			this.sFrom = this.sReplyTo;
		if (this.sFrom.length() <= 0)
			this.sFrom = getValue("Return-Path");

		this.sNotification = getValue("Disposition-Notification-To");
		if (this.sNotification == null || this.sNotification.length() <= 0)
			this.sNotification = getValue("Return-Receipt-To");

		this.sEncoding = getValue("Content-Transfer-Encoding");
		final String sPriority = getValue("X-Priority");
		this.iPriority = 3;
		if ((sPriority != null) && (sPriority.length() > 0))
			try {
				this.iPriority = Integer.parseInt(sPriority);
			} catch (final NumberFormatException e) {
				// keep the default value "3" if there is a problem parsing the given string
			}

		this.sContentType = getValue("Content-Type");
		if (this.sContentType.length() == 0)
			this.sContentType = "text/plain";
		final String sDate = getValue("Date");

		if (sDate.length() > 0)
			this.mdDate = new MailDate(sDate);
		else
			this.mdDate = new MailDate(new Date());

		final String sStatus = getValue("Status");

		this.iStatus = (sStatus.indexOf("R") >= 0) ? 1 : 0;
		this.bOld = sStatus.indexOf("O") >= 0;

		final String sLowerContent = this.sContentType.toLowerCase(Locale.getDefault());

		if (sLowerContent.startsWith("multipart/mixed")) {
			this.bEstimatedHasAttachments = true;
		}
		if (sLowerContent.startsWith("multipart/related")) {
			this.bEstimatedHasAttachments = true;
		}

		this.bConfirmation = false;
		if (sLowerContent.startsWith("multipart/report")) {
			final String sRepType = getValue("report-type").toLowerCase(Locale.getDefault());
			if (sRepType.startsWith("disposition-notification")) {
				this.bConfirmation = true;
			}
		}

		this.sMessageID = getValue("Message-ID");
		if (this.sMessageID.startsWith("<") && this.sMessageID.endsWith(">"))
			this.sMessageID = this.sMessageID.substring(1, this.sMessageID.length() - 1);

		this.sOrigMessageID = "";
	}

	/**
	 * A reconstruction of the headers.
	 * 
	 * @return a correct mail header, built from the internal fields
	 */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		String sResult = "";
		if (this.sFrom.length() > 0) {
			sResult += "From: " + this.sFrom + "\r\n";
			sResult += "Return-Path: " + this.sFrom + "\r\n";
		}
		if (this.sTo.length() > 0)
			sResult += "To: " + this.sTo + "\r\n";
		if (this.sCC.length() > 0)
			sResult += "Cc: " + this.sCC + "\r\n";
		if (this.sReplyTo.length() > 0)
			sResult += "Reply-to: " + this.sReplyTo + "\r\n";
		if (this.sContentType.length() > 0)
			sResult += "Content-Type: " + this.sContentType + ";\r\n";
		if (this.sContentType.toLowerCase(Locale.getDefault()).startsWith("multipart/"))
			sResult += "        boundary=" + this.sBoundary.substring(2) + "\r\n";
		sResult += "Date: " + this.mdDate.toMailString() + "\r\n";
		if (this.sSubject.length() > 0)
			sResult += "Subject: " + this.sSubject + "\r\n";
		if (this.sNotification.length() > 0)
			sResult += "Disposition-Notification-To: " + this.sNotification + "\r\n";

		return sResult;
	}

	/**
	 * Default sorting methods for the mails: by the date
	 * 
	 * @param o object to compare to
	 */
	@Override
	public int compareTo(final MailHeader o) {
		return this.mdDate.compareTo(o.mdDate);
	}

	@Override
	public boolean equals(final Object o){
		return this==o;
	}
	
	@Override
	public int hashCode(){
		assert false : "method not implemented"; //$NON-NLS-1$
	
		return 1;
	}
}
