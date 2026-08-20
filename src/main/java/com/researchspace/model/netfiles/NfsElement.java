package com.researchspace.model.netfiles;

import java.io.Serializable;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;

import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * This does not correspond to a DB entity - we do not store individual links to external filesystem files,
 * so ID is an invention based on hashcode of filestore ID and the path, which should be unique in most cases.
 */
@Data
@NoArgsConstructor
public class NfsElement implements IFieldLinkableElement, Serializable {
	
	private static final long serialVersionUID = -2900833776423439208L;

	public static final Pattern ExpectedLinkFormat = Pattern.compile("\\d+:/?.+");
	private static final String NFS_ELEMENT_LINK_FORMAT = "%d:%s";

	public static final String FULL_PATH_DATA_ATTR_NAME = "data-fullpath";
	public static final String LINKTYPE_FILE = "file";
	public static final String LINKTYPE_DIR = "directory";
	
	/**
	 * Get the filestore ID that contains this file resource
	 */
	private Long fileStoreId;

	/**
	 * The identifier in the actual net file store, e.g. iRODS DATA_ID
	 */
	private Long nfsId;
	private String path;
	private String linkType = LINKTYPE_FILE;

	public NfsElement(Long fileStoreId, String relPath) {
		this.fileStoreId = fileStoreId;
		this.path = relPath;
	}

	@Override
	@JsonIgnore
	public Long getId() {
		return (long) fileStoreId.hashCode() + path.hashCode();
	}

	@Override
	@JsonIgnore
	public GlobalIdentifier getOid() {
		return new GlobalIdentifier(GlobalIdPrefix.NF, getId());
	}

	public void setLinkType(String linkType) {
		if (!LINKTYPE_FILE.equals(linkType) && !LINKTYPE_DIR.equals(linkType)) {
			throw new IllegalArgumentException("unexpected link type: " + linkType);
		}
		this.linkType = linkType;
	}
	
	@JsonIgnore
	public boolean isFolderLink() {
		return LINKTYPE_DIR.equals(linkType);
	}
	
	public String toString () {
		return String.format(NFS_ELEMENT_LINK_FORMAT, fileStoreId, path);
	}

}
