package com.researchspace.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;

import org.apache.commons.io.FilenameUtils;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.RecordInformation;

import lombok.Setter;

@Entity
@Audited
@Indexed
@Setter
public class EcatDocumentFile extends EcatMediaFile {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4458103838837449215L;

	private String documentType;
	private ImageBlob thumbNail;
	
    public EcatDocumentFile () {}
	
    public EcatDocumentFile (ImportOverride override) {
		super(override);
	}

	public String getDocumentType() {
		return documentType;
	}
    /**
     * This is being replaced by storing thumbnail as FileProperty
     * @return
     */
	@Deprecated()
	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	public ImageBlob getThumbNail() {
		return thumbNail;
	}
	
	private FileProperty docThumbnailFP;
	private byte numThumbnailConversionAttemptsMade = 0;
	/**
	 * Number of attempts to generate document thumbnails. This can be used to prevent continual
	 * attempts that always result in failure
	 * @return
	 */
	public byte getNumThumbnailConversionAttemptsMade() {
		return numThumbnailConversionAttemptsMade;
	}

	/**
	 * Replacement for storing doc thumbnails - these will be stored in the filestore
	 * @return
	 */
	@ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	public FileProperty getDocThumbnailFP() {
		return docThumbnailFP;
	}
	

	@Transient
	public boolean isEcatDocument() {
		return true;
	}

	@Transient
	@Override
	public String getRecordInfoType() {
		return documentType;
	}

	@Override
	public EcatDocumentFile copy() {
		EcatDocumentFile copy = new EcatDocumentFile();
		copy.setDocumentType(this.getDocumentType());
		copy.setNumThumbnailConversionAttemptsMade(numThumbnailConversionAttemptsMade);
		copy.setDocThumbnailFP(docThumbnailFP);
		this.shallowCopyEcatMedia(copy);
		return copy;
	}

	public RecordInformation toRecordInfo() {
		RecordInformation info = super.toRecordInfo();
		info.setOriginalFileName(FilenameUtils.getName(getFileUri()));
		// new storage for thumbnials
		if(getDocThumbnailFP()!=null) {
			info.setThumbnailId(getDocThumbnailFP().getId());
		}
		// obsolete after migration
		else if (getThumbNail() != null) {
			info.setThumbnailId(getThumbNail().getId());
		}
		return info;
	}

}
