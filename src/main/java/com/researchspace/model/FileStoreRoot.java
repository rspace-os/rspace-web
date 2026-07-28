package com.researchspace.model;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.ColumnDefault;

/**
 * An absolute path to the top folder of an RSpace filestore.
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class FileStoreRoot implements Serializable {

	private static final long serialVersionUID = 3303411861066982850L;

	private Long id;
	private String fileStoreRoot;
	private Date creationDate;
	private boolean current;
	private boolean external = false;

	/**
	 * 
	 * @param fileStoreRoot
	 *            Must be a path ending in string 'file_store/'
	 */
	public FileStoreRoot(String fileStoreRoot) {
		this();
		validateRootPath(fileStoreRoot);
		this.fileStoreRoot = fileStoreRoot;
	}

	// for hibernate
	protected FileStoreRoot() {
		this.creationDate = new Date();
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	/**
	 * A file path to the top level folder of an RSpace filestore. Must be
	 * unique. Max length = 500 to allow for long file paths.<br>
	 * File strings should be a URI, e.g. for files start with 'file:' prefix.
	 * 
	 * @return
	 */
	@Column(nullable = false, columnDefinition = "varchar(500)", length = 500)
	public String getFileStoreRoot() {
		return fileStoreRoot;
	}

	@Transient
	public File getFileStoreRootDir() {
		return new File(fileStoreRoot);
	}

	/**
	 * Sets the file store root. Performs syntax validation but does not do any
	 * checks that the argument is a valid folder.
	 * 
	 * @param fileStoreRoot
	 * @throws IllegalArgumentException
	 *             if <code>fileStoreRoot</code> is empty or too long (see DB
	 *             constraint)
	 */
	public void setFileStoreRoot(String fileStoreRoot) {
		validateRootPath(fileStoreRoot);
		this.fileStoreRoot = fileStoreRoot;
	}

	private void validateRootPath(String fileStoreRoot) {
		Validate.isTrue(!StringUtils.isEmpty(fileStoreRoot), "Empty path");
		Validate.isTrue(fileStoreRoot.length() <= 500,
				String.format("Path is too long [%d] chars, must be <= %d chars", fileStoreRoot.length(), 500));
		// Validate.isTrue(fileStoreRoot.endsWith("file_store/"));
	}

	/**
	 * The time that this filestore root was first entered in the DB
	 * 
	 * @return
	 */
	@Temporal(TemporalType.TIMESTAMP)
	public Date getCreationDate() {
		return creationDate;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fileStoreRoot == null) ? 0 : fileStoreRoot.hashCode());
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
		FileStoreRoot other = (FileStoreRoot) obj;
		if (fileStoreRoot == null) {
			if (other.fileStoreRoot != null)
				return false;
		} else if (!fileStoreRoot.equals(other.fileStoreRoot))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "FileStoreRoot [id=" + id + ", fileStoreRoot=" + fileStoreRoot + ", creationDate=" + creationDate + "]";
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	/**
	 * Boolean test for whether this filestore is the current used filestore.
	 * 
	 * @return
	 */
	public boolean isCurrent() {
		return current;
	}

	public void setCurrent(boolean current) {
		this.current = current;
	}

	/**
	 * Whether or not this FileStoreRoot is external or not.
	 * @return
	 */
	@ColumnDefault("false")
	public boolean isExternal() {
		return external;
	}

	public void setExternal(boolean external) {
		this.external = external;
	}

}
