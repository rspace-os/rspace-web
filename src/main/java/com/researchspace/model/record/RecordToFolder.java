package com.researchspace.model.record;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * Represents a mapping between a Record and a Folder, also storing information
 * about the owner of the relationship. Equals/hashcode is defined in terms of
 * the equality of Record and Folder, so that duplicate entities with the same
 * record and child are not allowed.
 */
@Entity
public class RecordToFolder implements Serializable {

	@Override
	public String toString() {
		return "RecordToFolder [id=" + id + ", userName=" + userName + ", record="
				+ (record == null ? "null" : record.getName()) + ", folder="
				+ (folder == null ? "null" : folder.getName()) + ", recordInFolderDeleted=" + recordInFolderDeleted
				+ "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((folder == null) ? 0 : folder.hashCode());
		result = prime * result + ((record == null) ? 0 : record.hashCode());
		return result;
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
		RecordToFolder other = (RecordToFolder) obj;
		if (folder == null) {
			if (other.folder != null) {
				return false;
			}
		} else if (!folder.equals(other.folder)) {
			return false;
		}
		if (record == null) {
			if (other.record != null) {
				return false;
			}
		} else if (!record.equals(other.record)) {
			return false;
		}
		return true;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 7226810615671582426L;
	private Long id;
	private String userName;

	/**
	 * Convenient redundant method to help identify a folder/child mapping with
	 * a user without having to recurse up a folder tree to see which user this
	 * record/folder mapping belongs to.
	 * 
	 * @return
	 */
	public String getUserName() {
		return userName;
	}

	void setUserName(String userName) {
		this.userName = userName;
	}

	public RecordToFolder() {
	}

	@ManyToOne()
	public BaseRecord getRecord() {
		return record;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	/**
	 * Not public API; is public for auditing purposes
	 * 
	 * @param record
	 */
	public void setRecord(BaseRecord record) {
		this.record = record;
	}

	@ManyToOne
	public Folder getFolder() {
		return folder;
	}

	void setFolder(Folder folder) {
		this.folder = folder;
	}

	@Column(name = "recordInFolderDeleted")
	public boolean isRecordInFolderDeleted() {
		return recordInFolderDeleted;
	}
     /*
      * For Hibernate
      */
	 void setRecordInFolderDeleted(boolean isDeleted) {
		this.recordInFolderDeleted = isDeleted;
	}
	 
	public void markRecordInFolderDeleted(boolean isDeleted) {
		this.recordInFolderDeleted = isDeleted;
		// if isDeleted is false, we're restoring.
		setDeletedDate(isDeleted?new Date():null);
	}

	private BaseRecord record;

	private Folder folder;

	private boolean recordInFolderDeleted = false;
	
	private Date deletedDate;

	/**
	 * Gets when an item marked deleted. Can be null (if not deleted, or was deleted and restored).
	 * <br/>
	 * Returns a copy of the stored date object for better encapsulation
	 * @return
	 */
	@Temporal(TemporalType.TIMESTAMP)
	public Date getDeletedDate() {
		return (deletedDate!=null )?new Date(deletedDate.getTime()):null;
	}

	// for hibernate
	void setDeletedDate(Date deletedDate) {
		this.deletedDate = deletedDate;
	}

	public RecordToFolder(BaseRecord record, Folder folder, String username) {
		this.record = record;
		this.folder = folder;
		this.userName = username;
	}

}
