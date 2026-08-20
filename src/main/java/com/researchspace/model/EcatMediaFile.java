
package com.researchspace.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;

/**
 * Basic model used to represent all media Files (images,video,audio,documents).
 * 
 */
@Entity
@Audited
@XmlRootElement
public abstract class EcatMediaFile extends Record implements Serializable, Convertible {

	private static final long serialVersionUID = 427646974518947698L;

	private String fileName;
	private String contentType;
	private String extension;
	private long size;
	private long version = 1;

	private Set<FieldAttachment> linkedFields = new HashSet<>();
	private Set<RecordAttachment> linkedRecords = new HashSet<>();
	private FileProperty fileProperty;

	public EcatMediaFile() {
		addType(RecordType.MEDIA_FILE);
	}
	
	public EcatMediaFile(ImportOverride override) {
		super(override);
		addType(RecordType.MEDIA_FILE);
	}

	@Transient
	protected GlobalIdPrefix getGlobalIdPrefix() {
		return GlobalIdPrefix.GL;
	}

	@Transient
	public abstract String getRecordInfoType();

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	@Transient
	@Override
	public boolean isMediaRecord() {
		return true;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	/**
	 * Delegates to FileProperty to find URI of underlying file in file store
	 */
	@Transient
	public String getFileUri() {
		if (getFileProperty() != null) {
			return getFileProperty().getAbsolutePathUri();
		} else {
			return "unknown";
		}
	}

	public String getExtension() {
		return extension;
	}

	public void setExtension(String extension) {
		this.extension = extension;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	public EcatMediaFile shallowCopyEcatMedia(EcatMediaFile copy) {
		copy.setContentType(this.getContentType());
		copy.setExtension(this.getExtension());
		copy.setSize(this.size);
		this.shallowCopyRecordInfo(copy);
		return copy;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "mediaFile")
	public Set<FieldAttachment> getLinkedFields() {
		return linkedFields;
	}

	void setLinkedFields(Set<FieldAttachment> linkedFields) {
		this.linkedFields = linkedFields;
	}

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "mediaFile")
	public Set<RecordAttachment> getLinkedRecords() {
		return linkedRecords;
	}

	void setLinkedRecords(Set<RecordAttachment> linkedRecords) {
		this.linkedRecords = linkedRecords;
	}

	public RecordInformation toRecordInfo() {
		RecordInformation info = super.toRecordInfo();
		info.setExtension(getExtension());
		info.setSize(getSize());
		info.setType(getRecordInfoType());
		info.setVersion(getVersion());
		return info;
	}

	/**
	 * Boolean test for whether records is an Audio/Video file or not.
	 * 
	 * @return
	 */
	@Transient
	public boolean isAV() {
		return false;
	}
	
	@Transient
	public boolean isAudio(){
		return false;
	}
	
	@Transient
	public boolean isVideo(){
		return false;
	}

	@Transient
	public boolean isChemistryFile(){
		return false;
	}

	@Override
	@Transient
	public GlobalIdentifier getOidWithVersion() {
		return new GlobalIdentifier(getGlobalIdPrefix(), getId(), getVersion());
	}

	@ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	public FileProperty getFileProperty() {
		return fileProperty;
	}

	public void setFileProperty(FileProperty fileProperty) {
		this.fileProperty = fileProperty;
	}

}
