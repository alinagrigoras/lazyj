package lazyj.mail;

/**
 * Wrapper that extracts typical attachment keys from the corresponding header
 * 
 * @author costing
 * @since 2006-10-06
 */
public class AttachHeader extends Header {
	/**
	 * The Content-Type field of the header
	 */
	public String	sContentType;

	/**
	 * If the attachment is a file, this is the file name
	 */
	public String	sFileName;

	/**
	 * If the attachment has a name, not necessarely a file name, then this field will be set.
	 */
	public String	sName;

	/**
	 * Encoding used to store this attachment in the mail body
	 */
	public String	sContentEncoding;

	/**
	 * How to display this attachment
	 */
	public String	sContentDisposition;

	/**
	 * Unique identifier, used to reference attachments from the mail body (images ...)
	 */
	public String	sContentID;

	/**
	 * This will be set by the decoder ({@link lazyj.mail.Mail}) to the actual size of this attachment
	 */
	public int	iFileSize;

	/**
	 * Unique mail ID, set by higher level code
	 */
	public int	iMailID;

	/**
	 * Unique attachment ID, set by the decoder
	 */
	public int	iAttachID;

	/**
	 * Constructor based on the attachment header, extracted from the body part of the attachment
	 * 
	 * @param sHeader complete attachment header
	 */
	public AttachHeader(final String sHeader) {
		super(sHeader);

		this.sContentType = getValue("Content-Type"); //$NON-NLS-1$
		this.sFileName = getValue("filename"); //$NON-NLS-1$
		this.sName = getValue("name"); //$NON-NLS-1$
		this.sContentEncoding = getValue("Content-Transfer-Encoding"); //$NON-NLS-1$
		this.sContentDisposition = getValue("Content-Disposition"); //$NON-NLS-1$
		this.sContentID = getValue("Content-ID"); //$NON-NLS-1$
		if (this.sContentID.length() > 0 && this.sContentID.startsWith("<") && this.sContentID.endsWith(">")) //$NON-NLS-1$ //$NON-NLS-2$
			this.sContentID = this.sContentID.substring(1, this.sContentID.length() - 1);
	}

	/**
	 * Reconstruct an attachment header from the appropriate fields. Currently not used, but it would be nice :)
	 * 
	 * @return the reconstructed header
	 */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(500);
		
		if (this.sFileName.length() > 0)
			this.sName = this.sFileName;

		if (this.sContentType.length() > 0)
			sb.append("Content-Type: ").append(this.sContentType).append(";\r\n");
		
		if (this.sFileName.length() > 0)
			sb.append("       name=\"").append(this.sName).append("\"\r\n");
		
		if (this.sContentEncoding.length() > 0)
			sb.append("Content-Transfer-Encoding: ").append(this.sContentEncoding).append("\r\n");
		
		if (this.sContentDisposition.length() > 0)
			sb.append("Content-Disposition: ").append(this.sContentDisposition).append(";\r\n");
		
		if (this.sFileName.length() > 0)
			sb.append("        filename=\"").append(this.sFileName).append("\"\r\n");
		
		if (this.sContentID.length() > 0)
			sb.append("Content-ID: <").append(this.sContentID).append(">\r\n");
		
		return sb.toString();
	}
}