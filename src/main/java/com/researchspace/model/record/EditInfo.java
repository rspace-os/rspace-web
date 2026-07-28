package com.researchspace.model.record;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.apache.commons.lang3.StringUtils;

/**
 * Component class to be included in records /forms and future classes that need
 * to record user-editing.
 */
@Embeddable
public class EditInfo implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Max length of description field (RA - why is this constraint in DB 250 and not 255??)
	 */
	public static final int DESCRIPTION_LENGTH = 250;

	private String name;
	private String description;

	private Long creationDate;
	private Long creationDateMillis;

	private Long modificationDate;
	private Long modificationDateMillis;
	
	private String createdBy;
	private String modifiedBy;

	public EditInfo() { }

	@Column(nullable = false)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		if (StringUtils.isBlank(name)) {
			throw new IllegalArgumentException("Can't give record an empty name [" + name + "]");
		}
		// escape new lines from name so that they cannot be printed and break scripts.
		this.name = name.replaceAll("\n", "");
	}

	@Column(nullable = true, length = DESCRIPTION_LENGTH)
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Column(nullable = false, updatable = false)
	@Temporal(TemporalType.TIMESTAMP)
	public Date getCreationDate() {

		return new Date(creationDate);
	}

	/**
	 * Once this method has been called once for this object, future invocations
	 * will have no effect.
	 * 
	 * @param creationDate
	 */
	public void setCreationDate(Date creationDate) {
		synchronized (creationDate) {
			this.creationDate = creationDate.getTime();
			this.creationDateMillis = this.creationDate;
		}
	}

	/*
	 * Stored as long in MSQL, keeps millis so better as part of a business key.
	 */
	Long getCreationDateMillis() {
		return creationDateMillis;
	}

	void setCreationDateMillis(Long creationDateMillis) {
		this.creationDateMillis = creationDateMillis;
	}

	/*
	 * Stores as datetime type in MySQL, but loses millis.
	 */
	@Column(nullable = false)
	@Temporal(TemporalType.TIMESTAMP)
	public Date getModificationDate() {
		if (modificationDate == null) {
			modificationDate = new Date().getTime();
		}
		return new Date(modificationDate);
	}

	public void setModificationDate(Date modificationDate) {
		this.modificationDate = modificationDate.getTime();
		this.modificationDateMillis = this.modificationDate;
	}

	Long getModificationDateMillis() {
		return modificationDateMillis;
	}

	void setModificationDateMillis(Long modificationDateMillis) {
		this.modificationDateMillis = modificationDateMillis;
		this.modificationDate = modificationDateMillis;
	}
	
	@Column(nullable = false)
	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	@Column(nullable = false)
	public String getModifiedBy() {
		return modifiedBy;
	}

	public void setModifiedBy(String modifiedBy) {
		this.modifiedBy = modifiedBy;
	}

	/**
	 * Creates a copy of edit info, with new modification and creation date set as current time
	 * @return
	 */
	public EditInfo shallowCopy() {
		EditInfo copy = new EditInfo();
		copy.setName(getName());
		copy.setDescription(getDescription());
		Date d = new Date();
		copy.setCreationDate(d);
		copy.setModificationDate(d);
		copy.setCreatedBy(getCreatedBy());
		copy.setModifiedBy(getModifiedBy());

		return copy;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((createdBy == null) ? 0 : createdBy.hashCode());
		result = prime * result + ((creationDateMillis == null) ? 0 : creationDateMillis.hashCode());
		result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EditInfo other = (EditInfo) obj;
		if (createdBy == null) {
			if (other.createdBy != null)
				return false;
		} else if (!createdBy.equals(other.createdBy))
			return false;
		if (creationDateMillis == null) {
			if (other.creationDateMillis != null)
				return false;
		} else if (!creationDateMillis.equals(other.creationDateMillis))
			return false;
		if (getName() == null) {
			if (other.getName() != null)
				return false;
		} else if (!getName().equals(other.getName()))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "EditInfo [ name=" + getName() + ", description=" + description + ", creationDate=" + creationDate
				+ ", modificationDate=" + modificationDate + ", createdBy=" + createdBy + ", modifiedBy=" + modifiedBy
				+ "]";
	}

}
