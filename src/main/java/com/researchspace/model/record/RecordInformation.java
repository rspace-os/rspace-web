package com.researchspace.model.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.session.SessionTimeZoneUtils;
import java.util.Date;
import lombok.Data;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Class with the most relevant information of a record. It's used for some ajax
 * responses where it doesn't need the whole information of a record. It's also
 * used by FieldContents (from rspace-web) as a reference to linked document.
 */
@Data
public class RecordInformation implements IFieldLinkableElement {

	/* base record fields */
	private Long id;
	private Long revision;
	private String name;
	private String type;
	private long iconId = -1;
	private Date creationDate;
	private Date modificationDate;
	private String ownerUsername;
	private String ownerFullName;
	private String createdBy;
	private Long ownerId;
	private String description;
	private GlobalIdentifier oid;

	/* record fields */
	private long version;
	
	/* media file fields */
	private long parentId;
	private String originalFileName;
	private String extension;
	private int widthResized;
	private int heightResized;
	private byte rotation = 0;
	private GlobalIdentifier originalImageOid;
	private boolean isOnRoot;
	private Long thumbnailId;
	private long templateId = -1;
	private long size;
	private String chemString;
	private Long chemElementId;

	/* dmp file fields */
	private String dmpLink;
	private String doiLink;
	private DMPSource dmpSource;


	// only relevant/true/non-null if this document was imported from an XML archive
	private boolean fromImport;
	private String originalOwnerUsernamePreImport;
	//not intended to be part of equals or hashcode
	private boolean shared;

	public RecordInformation() {
	}

	public RecordInformation(BaseRecord baseRecord) {
		setId(baseRecord.getId());
		setName(baseRecord.getName());
		setType(baseRecord.getType());
		setIconId(baseRecord.getIconId());
		setCreationDate(baseRecord.getCreationDateAsDate());
		setModificationDate(baseRecord.getModificationDateAsDate());
		setOwnerUsername(baseRecord.getOwner().getUsername());
		setOwnerFullName(baseRecord.getOwner().getFullName());
		setOwnerId(baseRecord.getOwner().getId());
		setDescription(baseRecord.getDescription());
		setOid(baseRecord.getOid());
		setFromImport(baseRecord.isFromImport());
		setOriginalOwnerUsernamePreImport(baseRecord.getOriginalCreatorUsername());
		setShared(baseRecord.isShared());
	}

	public RecordInformation(RSForm form) {
		setId(form.getId());
		setName(form.getName());
		setIconId(form.getIconId());		
		setCreationDate(form.getCreationDateAsDate());
		setModificationDate(form.getModificationDateAsDate());
		setOwnerUsername(form.getOwner().getUsername());
		setOwnerFullName(form.getOwner().getFullName());
		setOwnerId(form.getOwner().getId());
		setCreatedBy(form.getCreatedBy());
		setDescription(form.getDescription());
		setOid(form.getOid());
	}

	public Date getCreationDate() {
		return creationDate == null ? null : new Date(creationDate.getTime());
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate == null ? null : new Date(creationDate.getTime());
	}

	@JsonProperty
	public String getCreationDateWithClientTimezoneOffset() {
		return new SessionTimeZoneUtils().formatDateTimeForClient(creationDate, false, true);
	}

	@JsonIgnore
	public void setCreationDateWithClientTimezoneOffset(String unused) {
		// nothing, method only added so json deserializer is not confused
	}

	public Date getModificationDate() {
		return modificationDate == null ? null : new Date(modificationDate.getTime());
	}

	public void setModificationDate(Date modificationDate) {
		this.modificationDate = modificationDate == null ? null : new Date(modificationDate.getTime());
	}

	@JsonProperty
	public String getModificationDateWithClientTimezoneOffset() {
		return new SessionTimeZoneUtils().formatDateTimeForClient(modificationDate, false, true);
	}

	@JsonIgnore
	public void setModificationDateWithClientTimezoneOffset(String unused) {
		// nothing, method only added so json deserializer is not confused
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(creationDate).append(description).append(extension).append(heightResized)
				.append(iconId).append(id).append(isOnRoot).append(modificationDate).append(name).append(oid)
				.append(originalFileName).append(ownerFullName).append(ownerId).append(ownerUsername).append(parentId).append(size)
				.append(templateId).append(thumbnailId).append(type).append(version).append(widthResized)
				.append(rotation)
				.toHashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}

		RecordInformation other = (RecordInformation) obj;
		return new EqualsBuilder().append(creationDate, other.creationDate).append(description, other.description)
				.append(extension, other.extension).append(heightResized, other.heightResized)
				.append(iconId, other.iconId).append(id, other.id).append(isOnRoot, other.isOnRoot)
				.append(modificationDate, other.modificationDate).append(name, other.name).append(oid, other.oid)
				.append(originalFileName, other.originalFileName).append(ownerFullName, other.ownerFullName)
				.append(ownerId, other.ownerId)
				.append(ownerUsername, other.ownerUsername).append(parentId, other.parentId).append(size, other.size)
				.append(templateId, other.templateId).append(thumbnailId, other.thumbnailId).append(type, other.type)
				.append(version, other.version).append(rotation, other.rotation)
				.append(widthResized, other.widthResized)
				.append(originalImageOid, other.originalImageOid).isEquals();
	}

	@Override
	public String toString() {
		return "RecordInformation [id=" + id + ", name=" + name + ", originalFileName=" + originalFileName + ", type="
				+ type + ", extension=" + extension + ", creationDate=" + creationDate + ", modificationDate="
				+ modificationDate + ", widthResized=" + widthResized + ", heightResized=" + heightResized
				+ ", parentId=" + parentId + ", isOnRoot=" + isOnRoot + ", version=" + version
				+ ", thumbnailId=" + thumbnailId + ", iconId=" + iconId + ", templateId=" + templateId
				+ ", ownerUsername=" + ownerUsername + ", ownerFullName=" + ownerFullName + ",ownerId=" + ownerId + ", size=" + size
				+ ", description=" + description + ",rotation=" + rotation +", oid=" + oid + "]";
	}

}
