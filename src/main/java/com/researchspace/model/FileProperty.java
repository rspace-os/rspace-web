package com.researchspace.model;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.ColumnDefault;

import com.researchspace.core.util.EscapeReplacement;

import lombok.Builder;

/**
 * File Property
 * <p>
 * This is metadata describing the file. Its properties are also used to
 * construct the path that the file is stored in, so <strong>don't alter these
 * properties</strong>
 */
@Entity
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(indexes = {@Index(columnList = "contentsHash", name = "contentsHash_idx")})
public class FileProperty implements Serializable {

	@Override
	public String toString() {
		return "FileProperty{" +
				"sdf=" + sdf +
				", fileCategory='" + fileCategory + '\'' +
				", fileGroup='" + fileGroup + '\'' +
				", fileUser='" + fileUser + '\'' +
				", fileVersion='" + fileVersion + '\'' +
				", root=" + root +
				", createDate=" + createDate +
				", updateDate=" + updateDate +
				", fileName='" + fileName + '\'' +
				", fileSize='" + fileSize + '\'' +
				", fileOwner='" + fileOwner + '\'' +
				", relPath='" + relPath + '\'' +
				", external=" + external +
				", contentsHash='" + contentsHash + '\'' +
				", id=" + id +
				'}';
	}

	@Transient
	private static final long serialVersionUID = 1L;

	@Transient
	private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

	private String fileCategory;
	private String fileGroup;
	private String fileUser;
	private String fileVersion;
	private FileStoreRoot root;

	private Date createDate;
	private Date updateDate;

	private String fileName;
	private String fileSize;
	private String fileOwner;
	// relative path from within filestore, for 1.34
	private String relPath;
	private boolean external = false;
	private String contentsHash;

	/**
	 * This is a temp column so that we can safely refactor the table and
	 * generate relative paths without losing the fileUri functionality.
	 * 
	 * @return
	 */
	@Column(length = 400, columnDefinition = "varchar(400)", nullable = false)
	public String getRelPath() {
		return relPath;
	}

	public void setRelPath(String relPath) {
		this.relPath = relPath;
	}

	private Long id;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	public FileProperty() {
		Date today = new Date();
		this.createDate = today;
		this.updateDate = today;
		fileCategory = "General";
		fileGroup = "Research";
		fileUser = "Anyone";
		fileVersion = "V1";
		fileSize = "0";
	}
	
	/**
	 * Builder for supplying the essential properties needed to construct a file path in the FileStore.
	 */
	@Builder
	public FileProperty(String fileCategory, String fileGroup, String fileUser, String fileOwner,
			String fileVersion, String contentsHash) {
		this();
		this.fileCategory = fileCategory;
		this.fileGroup= fileGroup;
		this.fileUser = fileUser;
		this.fileOwner = fileOwner;
		this.fileVersion = fileVersion;
		this.contentsHash = contentsHash;
	}

	/**
	 * Main method to retrieve the absolute path to a FileStore resource. This
	 * just generates the string, it doesn't check if the File actually exists
	 * or not.
	 * @return An absolute path
	 */
	@Transient
	public String getAbsolutePathUri() {
		String rc = getRoot().getFileStoreRoot();
		if (!rc.endsWith("/") && relPath != null) {
			rc = rc + "/";
		}
		if (relPath != null) {
			rc = rc + relPath;
		}
		rc = rc.replaceAll("\\\\", "/");
		return rc;
	}

	public String getFileCategory() {
		return fileCategory;
	}

	public void setFileCategory(String fileCategory) {
		this.fileCategory = fileCategory;
	}

	public String getFileGroup() {
		return fileGroup;
	}

	public void setFileGroup(String fileGroup) {
		this.fileGroup = fileGroup;
	}

	public String getFileUser() {
		return fileUser;
	}

	public void setFileUser(String fileUser) {
		this.fileUser = fileUser;
	}

	public String getFileVersion() {
		return fileVersion;
	}

	public void setFileVersion(String fileVersion) {
		this.fileVersion = fileVersion;
	}

	@Temporal(TemporalType.DATE)
	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}

	@Temporal(TemporalType.DATE)
	public Date getUpdateDate() {
		return updateDate;
	}

	public void setUpdateDate(Date dt) {
		this.updateDate = dt;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFileSize() {
		return fileSize;
	}

	public void setFileSize(String fileSize) {
		this.fileSize = fileSize;
	}

	public String getFileOwner() {
		return fileOwner;
	}

	public void setFileOwner(String fileOwner) {
		this.fileOwner = fileOwner;
	}

	public void setContentsHash(String contentsHash) {
		this.contentsHash = contentsHash;
	}

	public String getContentsHash() {
		return contentsHash;
	}

	/**
	 * This generates the target path for storing and looking up from the
	 * filesystem - <strong> don't change this!!</strong> <br/>
	 * Doesn't modify this FileProperty at all.
	 * 
	 * @param includeFile
	 *            boolean as to whether to include the filename as the last
	 *            element
	 * @return A String of the relative path in the FileStore that this
	 *         FileProperties should be stored.
	 */
	@Transient
	public final String makeTargetPath(boolean includeFile) {

		StringBuffer sbf = new StringBuffer();
		sbf.append(EscapeReplacement.replaceChars(getFileCategory()) + File.separator);
		sbf.append(EscapeReplacement.replaceChars(getFileGroup()) + File.separator);
		sbf.append(EscapeReplacement.replaceChars(getFileUser()) + File.separator);
		sbf.append(EscapeReplacement.replaceChars(getFileVersion()) + File.separator);
		if (includeFile) {
			sbf.append(EscapeReplacement.replaceChars(getFileName()));
		}
		return sbf.toString();
	}

	/**
	 * Uses the file properties to create a relative path string, stored in
	 * 'relPath'
	 * 
	 * @param base
	 */
	@Transient
	public void generateURIFromProperties(File base) {
		String pth = makeTargetPath(true);
		this.relPath = pth;
	}

	@Transient
	public String parseFileKey() {
		int idx = relPath.lastIndexOf(File.separator);
		return relPath.substring(idx + 1);
	}

	@Transient
	public FileProperty copy() {
		FileProperty fps = new FileProperty();
		fps.relPath = this.relPath;
		fps.fileCategory = this.fileCategory;
		fps.fileGroup = this.fileGroup;
		fps.fileUser = this.fileUser;
		fps.fileVersion = this.fileVersion;
		fps.createDate = this.createDate;
		fps.updateDate = this.updateDate;
		fps.fileName = this.fileName;
		fps.fileSize = this.fileSize;
		fps.fileOwner = this.fileOwner;
		fps.root = this.root;
		fps.contentsHash = this.contentsHash;
		return fps;
	}

	@ManyToOne
	public FileStoreRoot getRoot() {
		return root;
	}

	public void setRoot(FileStoreRoot root) {
		this.root = root;
	}
	
	/**
	 * Boolean as to whether file is stored externally ( e.g. on Egnyte) or not. This will always be <code>false</code> if
	 *  an external FS is not used.
	 * @return a boolean
	 */
	@ColumnDefault("false")
	public boolean isExternal() {
		return external;
	}

	public void setExternal(boolean external) {
		this.external = external;
	}
}