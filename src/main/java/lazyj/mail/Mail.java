package lazyj.mail;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import lazyj.Format;
import lazyj.Log;


/**
 * Wrapper class for a mail. See the public fields for details.<br>
 * Normal use is something like:<br>
 * <pre> Mail m = new Mail();
 *  m.sFrom = "john@lazyj.ro";
 *  m.sTo = "itzic@lazyj.ro;strul@lazyj.ro";
 *  m.sCC = "hans@lazyj.ro";
 *  m.sBCC = "boss@lazyj.ro";
 *  m.sSubject = "test message";
 *  m.sBody = "some text here";
 *  m.sHTMLBody = "the same text as html";
 *  m.sAttachedFiles = "/some/file;/another/file";
 *  
 *  Sendmail s = new Sendmail(m.sFrom);
 *  s.send(m);
 *  </pre>
 *  
 * @author costing
 * @since 2006-10-06
 * @see lazyj.mail.Sendmail
 * @see lazyj.Utils#htmlToText(String)
 */
public class Mail extends MailHeader {
	/**
	 * List of attachments that were decoded when the email was constructed
	 */
	public List<Attachment>	lAttachments	= null;

	/**
	 * Plain text body
	 */
	public String				sBody;

	/**
	 * HTML-formatted body
	 */
	public String				sHTMLBody;

	/**
	 * WML-formatted body
	 */
	public String				sWMLBody;

	/**
	 * List of files to be attached to this mail when it will be sent
	 */
	public String				sAttachedFiles;

	/**
	 * If this is an HTML-only email
	 */
	public boolean			bOnlyHTML;

	/**
	 * If this is an plain-text-only email
	 */
	public boolean			bOnlyPlain;

	/**
	 * Mail encapsulation (attachment) level
	 */
	private final int				iLevel;
	
	/**
	 * Character set used to send the body, HTML body and everything else
	 */
	public String			charSet = "UTF-8"; //$NON-NLS-1$

	/**
	 * Simple constructor, initializing the internal fields to nothing at all (as expected :) )
	 */
	@SuppressWarnings("nls")
	public Mail() {
		super();
		this.sBody = "";
		this.sHTMLBody = "";
		this.sWMLBody = "";
		this.sAttachedFiles = "";
		this.bOnlyHTML = true;
		this.bOnlyPlain = true;
		this.iLevel = 0;
	}

	/**
	 * Internal constructor, used when the body contains other embedded mails.
	 * 
	 * @param sHeader
	 * @param level
	 */
	@SuppressWarnings("nls")
	private Mail(final String sHeader, final int level) {
		super(sHeader);
		this.sBody = "";
		this.sHTMLBody = "";
		this.sWMLBody = "";
		this.sAttachedFiles = "";
		this.bOnlyHTML = true;
		this.bOnlyPlain = true;
		this.iLevel = level;
	}

	/**
	 * This constructor is used by the mail section. When decoding an email the header is first extracted then
	 * the actual body.
	 * 
	 * @param sHeader header part of the mail
	 * @param sBodyText
	 */
	public Mail(final String sHeader, final String sBodyText) {
		this(sHeader, sBodyText, 0);
	}

	/**
	 * Internal constructor, used when the body contains other embedded mails.
	 * 
	 * @param sHeader
	 * @param sBodyText 
	 * @param level
	 */
	private Mail(final String sHeader, final String sBodyText, final int level) {
		super(sHeader);
		this.sAttachedFiles = ""; //$NON-NLS-1$
		this.bOnlyHTML = true;
		this.bOnlyPlain = true;
		this.iLevel = level;
		processBody(sBodyText);
	}

	/**
	 * Find out if the mail has some attachments. It will return true if either one of these is true:<br><ul>
	 * <li>An received email had some attached files. These can be then accessed via {@link #lAttachments}.</li>
	 * <li>The mail you intend to send wants some files to be attached to it : {@link #sAttachedFiles}</li>
	 * <ul>
	 * 
	 * @return true if the mail has attachments, false if not.
	 */
	public boolean hasAttachments() {
		return (((this.lAttachments != null) && (!this.lAttachments.isEmpty())) || (this.sAttachedFiles.length() > 0));
	}

	/**
	 * Remove mail encoding tags
	 * 
	 * @param sOrig
	 * @return cleaned-up text
	 */
	@SuppressWarnings("nls")
	private static String stripCodes(final String sOrig) {
		if ((sOrig == null) || (sOrig.length() == 0))
			return "";
		
		final boolean bEndsWithEqual = sOrig.endsWith("=");
		final StringBuilder sbResult = new StringBuilder(sOrig.length());

		int pos = 0;
		
		String sLine = sOrig;
		
		while ((pos = sLine.indexOf("=")) >= 0) {
			if (pos > sLine.length() - 3)
				break;
			char c1 = sLine.charAt(pos + 1);
			char c2 = sLine.charAt(pos + 2);
			if (((c1 >= '0') && (c1 <= '9')) || ((c1 >= 'A') && (c1 <= 'F'))) {
				if (c1 <= '9')
					c1 -= '0';
				else
					c1 -= 'A' - 10;
			} else
				break; // ceva e bushit!
			if (((c2 >= '0') && (c2 <= '9')) || ((c2 >= 'A') && (c2 <= 'F'))) {
				if (c2 <= '9')
					c2 -= '0';
				else
					c2 -= 'A' - 10;
			} else
				break; // ceva e bushit!
			final char cod = (char) (c1 * 16 + c2);
			sbResult.append(sLine.substring(0, pos)).append(cod);
			sLine = sLine.substring(pos + 3);
		}
		
		sbResult.append(sLine);

		String sResult = sbResult.toString();

		if (bEndsWithEqual) {
			final int last = sResult.lastIndexOf("=");
			if (last < 0)
				Log.log(Log.FINER, "lazyj.mail.Mail", "stripCodes : sResult ends with '=' : "+sResult);
			else {
				sResult = sResult.substring(0, last);
			}
		} else
			sResult += "\n";

		return sResult;
	}

	/**
	 * Formatting function used when you want to display an email. It will concatenate lines and
	 * process special characters used to encode the text when it is put into an email.
	 * 
	 * @param sOrig text part of the mail as it is in the email
	 * @return cleaned, beautified, text, ready to be served to the user.
	 */
	@SuppressWarnings("nls")
	public static String stripBodyCodes(final String sOrig) {
		if ((sOrig == null) || (sOrig.length() == 0))
			return "";
		
		// concatenarea cu liniile urmatoare
		String sBody = Format.replace(sOrig, "=\n", "");
		sBody = Format.replace(sBody, "=\r\n", "");
		sBody = Format.replace(sBody, "=\r", "");
		sBody = Format.replace(sBody, "\n\n", "\n \n");
		sBody = Format.replace(sBody, "\r\n\r\n", "\r\n \r\n");

		final StringTokenizer st = new StringTokenizer(sBody, "\r\n", false);

		final StringBuilder sbResult = new StringBuilder(sBody.length());

		while (st.hasMoreTokens()) {
			sbResult.append(stripCodes(st.nextToken()));
		}

		return sbResult.toString();
	}

	/**
	 * Get the text contents of this email
	 * 
	 * @param sHead
	 * @param sBody
	 * @return email text
	 */
	private static String getText(final String sHead, final String sBody) {
		return new Attachment(sHead, sBody).getText();
	}

	/**
	 * parse mail body
	 * 
	 * @param sOrig
	 * @return true if everything went ok
	 */
	@SuppressWarnings("nls")
	private boolean processBody(final String sOrig) {
		String sMessageBody = sOrig;
		
		final String sCTLower = this.sContentType.toLowerCase(Locale.getDefault());
		
		if (!sCTLower.startsWith("multipart/")) {
			if (this.sEncoding.equalsIgnoreCase("base64")) { // in caz ca sunt bannerele text bagate
															// aiurea
				if (sMessageBody.indexOf("\n\n") >= 1) {
					sMessageBody = sMessageBody.substring(0, sMessageBody.indexOf("\n\n"));
				}

				if (sMessageBody.indexOf("\r\n\r\n") >= 1) {
					sMessageBody = sMessageBody.substring(0, sMessageBody.indexOf("\r\n\r\n"));
				}
			}

			if (sCTLower.startsWith("text/plain") || sCTLower.startsWith("text/enriched") || sCTLower.startsWith("message/disposition-notification")) {
				this.sBody = getText(this.sCompleteHeader, sMessageBody);
				this.bOnlyHTML = false;
				return true;
			}
			if (sCTLower.startsWith("text/html")) {
				this.sHTMLBody = getText(this.sCompleteHeader, sMessageBody);
				this.sBody = getText(this.sCompleteHeader, sMessageBody);
				this.bOnlyPlain = false;
				return true;
			}
			if (sCTLower.startsWith("text/vnd.wap.wml")) {
				this.sWMLBody = getText(this.sCompleteHeader, sMessageBody);
				return true;
			}
			if (sCTLower.startsWith("message/rfc822") && this.iLevel < 10) { // iLevel
																							// previne
																							// buclele
																							// inutile
				final Mail mail = new Mail(this.sCompleteHeader, sMessageBody, this.iLevel + 1);
				this.sBody = this.sBody + mail.sBody;
				this.sHTMLBody = this.sHTMLBody + mail.sHTMLBody;
				this.sWMLBody = this.sWMLBody + mail.sWMLBody;
				this.bOnlyHTML = this.bOnlyHTML || mail.bOnlyHTML;
				this.bOnlyPlain = this.bOnlyPlain || mail.bOnlyPlain;
				return true;
			}

			// a mai ramas numa atasament chior

			this.lAttachments = new LinkedList<>();
			final Attachment a = new Attachment(this.sCompleteHeader, sMessageBody);
			a.iMailID = this.iMailID;
			a.iAttachID = 0;
			a.iFileSize = (sMessageBody.length() * 3) / 4;
			this.lAttachments.add(a);
			return true;
		}

		final BufferedReader brBody = new BufferedReader(new StringReader(this.sCompleteHeader + "\n\n" + sMessageBody));
		String sLine = "", sFoundBoundary = "";
		boolean bInHeader = true;

		StringBuilder sb = new StringBuilder(20000);
		// sb.setLength(0);

		Attachment aCurrentAttach = null;

		final List<String> lBounds = new LinkedList<>();
		this.lAttachments = new LinkedList<>();

		int iAttachID = 0;
		boolean bFirstTextPart = true;
		boolean bFirstHTMLPart = true;
		boolean bFirstWMLPart = true;

		try {
			sLine = brBody.readLine();
		} catch (final Exception e) {
			return false;
		}

		while (sLine != null) {
			if (bInHeader) {
				sb.append(sLine).append('\n');

				if (sLine.length() == 0) {
					bInHeader = false;
					aCurrentAttach = new Attachment(sb.toString());
					if (aCurrentAttach.sBoundary.length() > 0) {
						lBounds.add(sFoundBoundary);
						sFoundBoundary = aCurrentAttach.sBoundary;
					}

					sb = new StringBuilder(30000);

					if (sCTLower.equals("multipart/digest")) {
						if ((aCurrentAttach.sContentType == null) || (aCurrentAttach.sContentType.length() <= 0)) {
							System.out.println("multipart/digest inner mail");
							aCurrentAttach.sContentType = "message/rfc822";
						}
					}

					if (aCurrentAttach.sContentType.toLowerCase(Locale.getDefault()).startsWith("message/rfc822") && this.iLevel < 10) {
						try {
							sLine = brBody.readLine();
						} catch (final Exception e) {
							return false;
						}

						do {
							sb.append(sLine).append('\n');
							if (sLine != null)
								aCurrentAttach.iFileSize += sLine.length();
							try {
								sLine = brBody.readLine();
							} catch (final Exception e) {
								return false;
							}
						} while ((sLine != null) && (!sLine.startsWith(sFoundBoundary)) && (!sLine.equals(".")));
						if (sLine != null && sLine.startsWith(sFoundBoundary + "--")) { 
							if (lBounds.size() > 0) {
								sFoundBoundary = lBounds.get(lBounds.size() - 1);
								lBounds.remove(lBounds.size() - 1);
							} else {
								Log.log(Log.ERROR, "lazyj.mail.Mail", "processMailBody : No boundary to extract. Bad mail !!! (old boundary=" + sFoundBoundary + ")");
							}
						}

						String sNewBody = sb.toString();

						final String sNewHeader = sNewBody.substring(0, sNewBody.indexOf("\n\n") + 1);
						sNewBody = sNewBody.substring(sNewBody.indexOf("\n\n") + 2);

						final Mail mAtasat = new Mail(sNewHeader, this.iLevel + 1);
						mAtasat.iMailID = this.iMailID;
						mAtasat.processBody(sNewBody);

						this.sBody += mAtasat.sBody;
						this.sHTMLBody += mAtasat.sHTMLBody;
						this.sWMLBody += mAtasat.sWMLBody;
						this.bOnlyHTML = mAtasat.bOnlyHTML || this.bOnlyHTML;
						this.bOnlyPlain = mAtasat.bOnlyPlain || this.bOnlyPlain;
						for (final Attachment at : mAtasat.lAttachments) {
							at.iAttachID = iAttachID++;
							this.lAttachments.add(at);
						}

						bInHeader = true;
						sb = new StringBuilder(50000);
					}
				}

			} else {
				if (aCurrentAttach != null) {
					if (sLine.startsWith(sFoundBoundary)) {
						bInHeader = true;

						if (aCurrentAttach.sContentType.toLowerCase(Locale.getDefault()).startsWith("message/rfc822")) {
							aCurrentAttach.sFileName = "email" + iAttachID + ".eml";
						}

						if (aCurrentAttach.sContentType.length() <= 0)
							aCurrentAttach.sContentType = "text/plain";

						final String sAttachCTLower = aCurrentAttach.sContentType.toLowerCase(Locale.getDefault()); 
						
						if (aCurrentAttach.sFileName.length() > 0 || 
								aCurrentAttach.sName.length() > 0 || 
								aCurrentAttach.sContentID.length() != 0 || 
								(sAttachCTLower.startsWith("text/plain") && !bFirstTextPart) || 
								(sAttachCTLower.startsWith("message/disposition-notification") && !bFirstTextPart) || 
								(sAttachCTLower.startsWith("text/enriched") && !bFirstTextPart) || 
								(sAttachCTLower.startsWith("text/html") && !bFirstHTMLPart) || 
								(sAttachCTLower.startsWith("text/vnd.wap.wml") && !bFirstWMLPart)) {
							aCurrentAttach.sAttachmentBody = sb.toString();
							aCurrentAttach.iMailID = this.iMailID;
							aCurrentAttach.iAttachID = iAttachID++;

							if ((aCurrentAttach.sFileName.length() == 0) && (aCurrentAttach.sName.length() == 0)) {
								aCurrentAttach.sName = "some_text_part";
								if (sAttachCTLower.indexOf("plain") >= 0)
									aCurrentAttach.sName += ".txt";
								else
								if (sAttachCTLower.indexOf("enriched") >= 0)
									aCurrentAttach.sName += ".txt";
								else
								if (sAttachCTLower.indexOf("html") >= 0)
									aCurrentAttach.sName += ".html";
								else
								if (sAttachCTLower.indexOf("wml") >= 0)
									aCurrentAttach.sName += ".wml";
							}

							if (aCurrentAttach.sFileName.length() == 0)
								aCurrentAttach.sFileName = aCurrentAttach.sName;

							if (aCurrentAttach.iFileSize > 0) { // do not list the attachments with
								// size=0 ...
								aCurrentAttach.iFileSize *= 3;
								aCurrentAttach.iFileSize /= 4;
								this.lAttachments.add(aCurrentAttach);
							}
						} else {
							aCurrentAttach.sAttachmentBody = sb.toString();
							final String sNewBody = aCurrentAttach.getText();

							if ((sAttachCTLower.startsWith("text/plain") || sAttachCTLower.startsWith("text/enriched") || sAttachCTLower.startsWith("message/disposition-notification")) && bFirstTextPart) {
								bFirstTextPart = false;
								this.bOnlyHTML = false;
								this.sBody = sNewBody;
							}
							if (sAttachCTLower.startsWith("text/html") && bFirstHTMLPart) {
								bFirstHTMLPart = false;
								this.sHTMLBody = sNewBody;
								this.bOnlyPlain = false;
							}
							if (sAttachCTLower.startsWith("text/vnd.wap.wml") && bFirstWMLPart) {
								bFirstWMLPart = false;
								this.sWMLBody = sNewBody;
							}
						}
						sb = new StringBuilder(20000);
					} else {
						sb.append(sLine).append('\n');
						aCurrentAttach.iFileSize += sLine.length();
					}
				}
				
				if (sLine.startsWith(sFoundBoundary + "--")) {
					if (lBounds.size() > 0) {
						sFoundBoundary = lBounds.get(lBounds.size() - 1);
						lBounds.remove(lBounds.size() - 1);
					} else {
						Log.log(Log.ERROR, "lazyj.mail.Mail", "processMailBody : No boundary to extract. Bad mail !!! (old boundary=" + sFoundBoundary + ")");
					}
				}
			}

			try {
				sLine = brBody.readLine();
			} catch (final Exception e) {
				return false;
			}
		}

		return true;
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
