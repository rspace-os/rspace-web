package com.researchspace.model.audit;

import java.util.Date;

import org.hibernate.envers.RevisionType;

/**
 * Wraps an audited entity with its revision number and type.
 *
 * @param <T>
 */
public class AuditedEntity<T> {

	T entity;

	Number revision;

	private RevisionType revType = RevisionType.MOD;// will be the default
	
	protected Date deletedDate;

	/**
	 * Neither argument should be <code>null</code>.
	 * 
	 * @param entity
	 *            An entity that has been retrieved from an audit table.
	 * @param revision
	 *            A revision number
	 */
	public AuditedEntity(T entity, Number revision) {
		this.entity = entity;
		this.revision = revision;
	}

	/**
	 * Public no-args constructor for reflection; not to be used in regular
	 * code.
	 */
	public AuditedEntity() { }

	/**
	 * Alternative constructor that takes a {@link RevisionType} a 3rd argument.
	 */
	public AuditedEntity(T entity, Number revision, RevisionType revType) {
		this(entity, revision);
		this.revType = revType;
	}
	
	/**
	 * Alternative constructor that takes a deletedDate as 4th argument (can be null if not deleted).
	 */
	public AuditedEntity(T entity, Number revision, RevisionType revType, Date deletedDate) {
		this(entity, revision, revType);
		this.deletedDate = deletedDate != null ? new Date(deletedDate.getTime()) : null;
	}

	public T getEntity() {
		return entity;
	}

	public void setEntity(T entity) {
		this.entity = entity;
	}

	/**
	 * Gets the revision number of this document. Revisions are global, so
	 * revision numbers may not be consecutive for an entity.
	 */
	public Number getRevision() {
		return revision;
	}

	public void setRevision(Number revision) {
		this.revision = revision;
	}

	public RevisionType getRevType() {
		return revType;
	}

	public void setRevType(RevisionType revType) {
		this.revType = revType;
	}

	/**
	 * One of MOD, DEL, ADD
	 * 
	 * @return
	 */
	public String getRevisionTypeString() {
		return revType.toString();
	}

		/**
	 * Returns a copy of the deleted date or null if record is not deleted.
	 * @return a possibly null Date
	 */
	public Date getDeletedDate() {
	   return (deletedDate!= null)?new Date(deletedDate.getTime()):null;	
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "AuditedEntity [entity=" + entity + ", revision=" + revision + ", revType=" + revType + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entity == null) ? 0 : entity.hashCode());
		result = prime * result + ((revision.intValue() == 0) ? 0 : revision.hashCode());
		return result;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AuditedEntity other = (AuditedEntity) obj;
		if (entity == null) {
			if (other.entity != null)
				return false;
		} else if (!entity.equals(other.entity))
			return false;
		if (revision == null) {
			if (other.revision != null)
				return false;
		} else if (revision.intValue() != other.revision.intValue())
			return false;
		return true;
	}

}
