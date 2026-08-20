package com.researchspace.model.inventory;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Basic model used to represent all files added as inventory attachments
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Audited
public class InventoryFile extends InventoryRecordConnectedEntity implements Serializable {

	private static final long serialVersionUID = -5314995639182094423L;

	private Long id;
	
	// indexing filename together with field data
	@FullTextField(name = "fieldData")
	private String fileName;
	private Date creationDate;
	private String createdBy;
	private String extension;
	private FileProperty fileProperty;
	private long size;
	private InventoryFileType fileType = InventoryFileType.GENERAL;
	private String contentMimeType;
	private boolean deleted;

	private EcatMediaFile mediaFile;
	private InventoryEntityField inventoryEntityField;
	
	public enum InventoryFileType {
		GENERAL, CHEMICAL
	}

	public InventoryFile(String fileName, FileProperty fileProperty) {
		setFileName(fileName);
		setFileProperty(fileProperty);
		if (fileProperty != null) {
			setSize(Long.parseLong(fileProperty.getFileSize()));
		}
		setCreationDate(new Date());
	}

	/**
	 * Creates InventoryFile based on ELN Gallery file
	 */
	public InventoryFile(EcatMediaFile mediaFile) {
		this(mediaFile.getName(), mediaFile.getFileProperty());
		setCreatedBy(mediaFile.getCreatedBy());
		setMediaFile(mediaFile);
		setExtension(mediaFile.getExtension());
		setSize(mediaFile.getSize());
		if (mediaFile.isChemistryFile()) {
			setFileType(InventoryFileType.CHEMICAL);
		}
		setContentMimeType(mediaFile.getContentType());
		setDeleted(mediaFile.isDeleted());
	}

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "inventory_file_gen")
	@TableGenerator(name = "inventory_file_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	public Long getId() {
		return id;
	}
	
	@Transient
	public GlobalIdentifier getOid() {
		return new GlobalIdentifier(GlobalIdPrefix.IF, id);
	}
	
	/**
	 * Date of entity creation, i.e. date of uploading inventory file to RSpace.
	 * Returns a copy of the stored date object for better encapsulation
	 */
	@Column(nullable = false, updatable = false)
	@Temporal(TemporalType.TIMESTAMP)
	public Date getCreationDate() {
		return creationDate == null ? null : new Date(creationDate.getTime());
	}

	@ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	public FileProperty getFileProperty() {
		return fileProperty;
	}

	public void setFileProperty(FileProperty fileProperty) {
		this.fileProperty = fileProperty;
	}

	@ManyToOne
	public EcatMediaFile getMediaFile() {
		return mediaFile;
	}

	@ManyToOne(cascade = CascadeType.MERGE)
	private InventoryEntityField getInventoryEntityField() {
		return inventoryEntityField;
	}

	@Transient
	@Override
	public GlobalIdentifier getConnectedRecordOid() {
		return inventoryEntityField != null ? inventoryEntityField.getOid() : super.getConnectedRecordOid();
	}

	@Transient
	public String getMediaFileGlobalIdentifier() {
		return mediaFile != null ? mediaFile.getOid().getIdString() : null;
	}

	@Transient
	@Override
	protected int getNonInventoryRecordParentCount() {
		return inventoryEntityField == null ? 0 : 1;
	}
	
	/**
	 * Performs shallow copy of the attachment with copied reference to FileProperty. 
	 * Does not set InventoryRecord relation.
	 */
	public InventoryFile shallowCopy() {
		InventoryFile copy = new InventoryFile(getFileName(), getFileProperty());
		copy.setCreatedBy(getCreatedBy());
		copy.setExtension(getExtension());
		copy.setSize(getSize());
		copy.setFileType(getFileType());
		copy.setContentMimeType(getContentMimeType());
		copy.setDeleted(isDeleted());
		return copy;
	}

}
