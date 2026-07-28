package com.researchspace.model;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import com.researchspace.core.util.SecureStringUtils;
import org.hibernate.annotations.CreationTimestamp;

import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;

/**
 * Keeps track of records shared with a group or user
 */
@Entity
@AuditTrailData(auditDomain = AuditDomain.GROUP)
public class RecordGroupSharing implements Serializable {

	private static final long serialVersionUID = -419985705388086299L;
	public static final String ANONYMOUS_USER = "rspace anonymous guest";

	private AbstractUserOrGroupImpl sharee;

	private BaseRecord shared;

	private Folder targetFolder;

	private PermissionType permType;

	private String publicLink;
	private String publicationSummary;
	private boolean displayContactDetails;
	private boolean publishOnInternet;

	private Long id;

	private Date creationDate;
	private User sharedBy;

	/**
	 * For display in the UI - permission is stored in Group permissions table
	 * 
	 * @return
	 */
	@Transient
	public PermissionType getPermType() {
		return permType;
	}

	public void setPermType(PermissionType permType) {
		this.permType = permType;
	}

	public String getPublicLink() {
		return publicLink;
	}

	public void setPublicLink(String publicLink) {
		this.publicLink = publicLink;
	}
	/**
	 * REcords the creation timestamp of this document sharing.
	 * @return
	 */
	@CreationTimestamp
	@Temporal(TemporalType.TIMESTAMP)
	@Column(updatable=false)
	public Date getCreationDate() {
		if(creationDate != null) {
			return new Date(creationDate.getTime());
		}
		return creationDate;
	}

	void setCreationDate(Date creationDate) {
		if(creationDate!=null) {
			this.creationDate = new Date(creationDate.getTime());
		}		
	}

	@Id()
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "record_group_sharing_gen")
	@TableGenerator(name = "record_group_sharing_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	@AuditTrailIdentifier()
	public Long getId() {
		return id;
	}

	/*
	 * For hibernate
	 */
	void setId(Long id) {
		this.id = id;
	}

	public RecordGroupSharing(AbstractUserOrGroupImpl sharee, BaseRecord shared) {
		super();
		this.sharee = sharee;
		this.shared = shared;
		if(sharee.isUser() && sharee.asUser().isAnonymousGuestAccount()){
			publicLink = SecureStringUtils.getURLSafeSecureRandomString(16);
		}
	}

	/*
	 * For hibernate
	 */
	public RecordGroupSharing() {
	}

	/**
	 * The user or group with whom this record is shared
	 * 
	 * @return
	 */
	@ManyToOne
	@AuditTrailProperty(name = "sharee", properties = { "uniqueName", "oid" })
	public AbstractUserOrGroupImpl getSharee() {
		return sharee;
	}

	public void setSharee(AbstractUserOrGroupImpl sharee) {
		this.sharee = sharee;
	}
	
	@OneToOne
	public User getSharedBy() {
		return sharedBy;
	}

	public void setSharedBy(User sharedBy) {
		this.sharedBy = sharedBy;
	}

	/**
	 * The record that is shared
	 * 
	 * @return
	 */
	@ManyToOne
	@AuditTrailProperty(name = "sharedRecordId", properties = "globalIdentifier")
	public BaseRecord getShared() {
		return shared;
	}

	public void setShared(BaseRecord shared) {
		this.shared = shared;
	}

	/**
	 * Returns the folder (notebook) that the record was "shared into", or null
	 * if the record was shared normally (into group/individual shared folder).
	 */
	@ManyToOne
	@AuditTrailProperty(name = "targetFolderId", properties = "globalIdentifier")
	public Folder getTargetFolder() {
		return targetFolder;
	}

	public void setTargetFolder(Folder targetFolder) {
		this.targetFolder = targetFolder;
	}

	public String getPublicationSummary() {
		return publicationSummary;
	}

	public void setPublicationSummary(String publicationSummary) {
		this.publicationSummary = publicationSummary;
	}

	public boolean isDisplayContactDetails() {
		return displayContactDetails;
	}

	public void setDisplayContactDetails(boolean displayContactDetails) {
		this.displayContactDetails = displayContactDetails;
	}

	public boolean isPublishOnInternet() {
		return publishOnInternet;
	}

	public void setPublishOnInternet(boolean publishOnInternet) {
		this.publishOnInternet = publishOnInternet;
	}
}
