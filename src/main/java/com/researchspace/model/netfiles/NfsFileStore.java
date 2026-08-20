package com.researchspace.model.netfiles;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.researchspace.model.User;

/**
 * Entity class for external file system locations saved by the user.
 */
@Entity
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class NfsFileStore implements Serializable {
	
	@Transient
	private static final long serialVersionUID = 15212L;

	private Long id; // primary key, also used for generating links inside documents
	private User user; // owner of the folder
	private NfsFileSystem fileSystem; // fileSystem this fileStore belongs to
	
	private String path; // the path that folder is pointing to
	private String name; // display name chosen by the user (special chars allowed) 

	private boolean deleted; // is the folder deleted (won't show among My Filestores)
	
	public NfsFileStore() { }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	@ManyToOne
	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}
	
	@ManyToOne
	public NfsFileSystem getFileSystem() {
		return fileSystem;
	}

	public void setFileSystem(NfsFileSystem filesystem) {
		this.fileSystem = filesystem;
	}
	
	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean isDeleted) {
		this.deleted = isDeleted;
	}

	/**
	 * Given an NfsElement representing a single link in a text field, return full path representation
	 * @param element
	 * @return An {@link NfsPathElements} 
	 */
	@Transient
	public NfsPathElements getFullPath(NfsElement element) {
		return new NfsPathElements(getFileSystem().getUrl(), getPath(), element.getPath());
	}

	/**
	 * Given a relative path on a filestore, return full path on a filesystem.
	 */
	@Transient
	public String getAbsolutePath(String relativePath) {
		 return validateTargetPath(getPath()) + relativePath;
	}

	public static String validateTargetPath(String target) {
		if (StringUtils.isEmpty(target)) {
			return "";
		}
		String validatedTarget = target.trim();
		if (validatedTarget.endsWith("/") && !validatedTarget.equals("/")) { 
			// get ride of last /
			validatedTarget = validatedTarget.substring(0, validatedTarget.length() - 1);
		}
		return validatedTarget;
	}
	
	public NfsFileStoreInfo toFileStoreInfo() {
		return new NfsFileStoreInfo(this);
	}

	@Override
	public int hashCode() {
		return id != null ? id.intValue() : 0;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof NfsFileStore)) {
			return false;
		}
		NfsFileStore other = (NfsFileStore) obj;
		if (id != null && id.equals(other.id)) {
			return true;
		}
		return false;
	}

	// for debug
	public String toString() {
		StringBuffer sbf = new StringBuffer();
		sbf.append("id = " + id);
		sbf.append(", user = " + user);
		sbf.append(", name = " + name);
		sbf.append(", path = " + path);
		return sbf.toString();
	}
	
}
