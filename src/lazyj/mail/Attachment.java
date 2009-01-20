package lazyj.mail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import lazyj.Log;


/**
 * Wrapper class for an attachment. Supplemental to the {@link AttachHeader} fields it adds methods to 
 * work with the attachment itself: {@link #getInputStream()}, {@link #getText()} and {@link #getText(String)}. 
 * 
 * @author costing
 * @since 2006-10-06
 */
public class Attachment extends AttachHeader {
	/**
	 * Package protected field. This is set by the decoder code from {@link lazyj.mail.Mail}.
	 */
	String	sAttachmentBody;

	/**
	 * Constructor that initializes only the header fields.
	 * 
	 * @param sHeader attachment header
	 */
	public Attachment(final String sHeader) {
		super(sHeader);
	}

	/**
	 * Constructor that initializes both the headers and the body.
	 * 
	 * @param sAttachmentHeader attachment header
	 * @param sBody attachment raw body, as it is seen in the email
	 */
	public Attachment(final String sAttachmentHeader, final String sBody) {
		super(sAttachmentHeader);
		this.sAttachmentBody = sBody;
	}

	/**
	 * Get the raw input stream, as seen when looking at the mail source 
	 * 
	 * @return raw data input stream
	 */
	public InputStream getInputStream() {
		return new ByteArrayInputStream((getOriginalHeader() + '\n' + this.sAttachmentBody).getBytes());
	}

	/**
	 * Get the decoded input stream. This is the actual content that the sender has attached to the mail.
	 * 
	 * @return decoded data input stream
	 */
	public InputStream getDecodedInputStream() {
		InputStream isResult = null;

		try {
			javax.mail.internet.MimeBodyPart mbp = new javax.mail.internet.MimeBodyPart(getInputStream());
			isResult = (InputStream) mbp.getContent();
		} catch (Exception e) {
			Log.log(Log.FATAL, "lazyj.mail.Attachment", "Attachment: getDecodedInputStream exception : "+ e); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return isResult;
	}
	
	/**
	 * Convert the attachment to a String, using the encoding specified in the attachment header, if any,
	 * otherwise use the default "us-ascii" encoding.
	 * 
	 * @return the text contained in this attachment, using the supplied charset
	 */
	public String getText(){
		final String sEncoding = getValue("charset"); //$NON-NLS-1$
			
		return getText(sEncoding.length()>0 ? sEncoding : "us-ascii"); //$NON-NLS-1$
	}
	
	/**
	 * Convert the attachment to a String, using the supplied encoding.
	 * 
	 * @param sEncoding the encoding to be used
	 * @return the text contained in this attachment, in the supplied encoding
	 */
	public String getText(final String sEncoding) {
		try {
			final InputStream body = getDecodedInputStream();

			final ByteArrayOutputStream baos = new ByteArrayOutputStream(this.sAttachmentBody.length());
			
			final byte bbuff[] = new byte[10240];
			
			int i;

			while ((i = body.read(bbuff)) != -1) {
				baos.write(bbuff, 0, i);				
			}

			return new String(baos.toByteArray(), sEncoding);
		}
		catch (Throwable t) {
			Log.log(Log.ERROR, "lazyj.mail.Attachment", "getText exception", t); //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		}
	}
}
