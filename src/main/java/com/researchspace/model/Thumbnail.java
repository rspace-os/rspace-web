package com.researchspace.model;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString
@Table(indexes = { @Index(columnList = "sourceType", name = "INDEX_THUMBNAIL_SOURCE_TYPE"),
		@Index(columnList = "sourceId", name = "INDEX_THUMBNAIL_SOURCE_ID"),
		@Index(columnList = "sourceParentId", name = "INDEX_THUMBNAIL_SOURCE_PARENT_ID"),
		@Index(columnList = "revision", name = "INDEX_THUMBNAIL_REVISION_ID"),
		@Index(columnList = "width", name = "INDEX_THUMBNAIL_WIDTH"),
		@Index(columnList = "height", name = "INDEX_THUMBNAIL_HEIGHT")
})
public class Thumbnail implements Serializable, IFieldLinkableElement {

	static final int MAX_THUMBNAIL_SIZE = 1500;

	private static final long serialVersionUID = -54338551053929180L;

	static final Pattern FIELD_ID = Pattern.compile("sourceParentId=(\\d+)");
	static final Pattern SRC_ID = Pattern.compile("sourceId=(\\d+)");
	static final Pattern SRC_TYPE = Pattern.compile("sourceType=(\\w+)");
	static final Pattern WIDTH = Pattern.compile("width=(\\d+)");
	static final Pattern HEIGHT = Pattern.compile("height=(\\d+)");
	// just get 1st digit if there is only 1 digit; else don't match
	static final Pattern ROTATION = Pattern.compile("rotation=([0-3])(?![\\d])");
	static final Pattern REV = Pattern.compile("revision=(\\d+)");

	/**
	 * Converts a thumbnail URL to Thumnail object
	 * 
	 * @param url
	 * @return a transient thumbnail or <code>null</code> if source/parent ids
	 *         could not be parsed.
	 */
	public static Thumbnail fromURL(String url) {
		Thumbnail thum = new Thumbnail();
		Matcher fieldMAtcher = FIELD_ID.matcher(url);
		if (fieldMAtcher.find()) {
			thum.setSourceParentId(Long.parseLong(fieldMAtcher.group(1)));
		} else {
			return null;
		}
		Matcher srcMAtcher = SRC_ID.matcher(url);
		if (srcMAtcher.find()) {
			thum.setSourceId(Long.parseLong(srcMAtcher.group(1)));
		} else {
			return null;
		}
		Matcher srcMatcher = SRC_TYPE.matcher(url);
		if (srcMatcher.find()) {
			thum.setSourceType(SourceType.valueOf(srcMatcher.group(1)));
		}
		Matcher widthMatcher = WIDTH.matcher(url);
		if (widthMatcher.find()) {
			thum.setWidth(Integer.parseInt(widthMatcher.group(1)));
		}
		Matcher heightMatcher = HEIGHT.matcher(url);
		if (heightMatcher.find()) {
			thum.setHeight(Integer.parseInt(heightMatcher.group(1)));
		}
		Matcher rotationMatcher = ROTATION.matcher(url);
		if (rotationMatcher.find()) {
			thum.setRotation(Byte.parseByte(rotationMatcher.group(1)));
		}
		Matcher revMatcher = REV.matcher(url);
		if (revMatcher.find()) {
			thum.setRevision(Long.parseLong(revMatcher.group(1)));
		}
		return thum;
	}

	public enum SourceType {
		IMAGE, CHEM
	}

	// The combination of sourceType and sourceId lets us know the table and row
	// of the object this is a thumbnail for.
	@Column(nullable = false)
	private SourceType sourceType;
	@Column(nullable = false)
	private Long sourceId;
	@Column(nullable = true)
	private Long sourceParentId;
	@Column(nullable = true)
	private Long revision;

	@Column(nullable = false)
	private int width;
	@Column(nullable = false)
	private int height;
	@Column(nullable=false)
	private byte rotation;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@Deprecated // should be null now, use file property instead
	private ImageBlob imageBlob;
	
	@ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
	private FileProperty thumbnailFP;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + height;
		result = prime * result + ((revision == null) ? 0 : revision.hashCode());
		result = prime * result + ((sourceId == null) ? 0 : sourceId.hashCode());
		result = prime * result + ((sourceParentId == null) ? 0 : sourceParentId.hashCode());
		result = prime * result + ((sourceType == null) ? 0 : sourceType.hashCode());
		result = prime * result + width;
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
		Thumbnail other = (Thumbnail) obj;
		if (height != other.height) {
			return false;
		}
		if (revision == null) {
			if (other.revision != null) {
				return false;
			}
		} else if (!revision.equals(other.revision)) {
			return false;
		}
		if (sourceId == null) {
			if (other.sourceId != null) {
				return false;
			}
		} else if (!sourceId.equals(other.sourceId)) {
			return false;
		}
		if (sourceParentId == null) {
			if (other.sourceParentId != null) {
				return false;
			}
		} else if (!sourceParentId.equals(other.sourceParentId)) {
			return false;
		}
		if (sourceType != other.sourceType) {
			return false;
		}
		if (width != other.width) {
			return false;
		}
		return true;
	}

	public void setWidth(int width) {
		this.width = Math.min(width, MAX_THUMBNAIL_SIZE);
	}

	public void setHeight(int height) {
		this.height = Math.min(height, 1500);
	}

	@Override
	@Transient
	public GlobalIdentifier getOid() {
		return new GlobalIdentifier(GlobalIdPrefix.TH, getId());
	}

	/**
	 * MAkes a copy of all properties, except ID
	 * 
	 * @return the newly copied object
	 */
	@Transient
	public Thumbnail getCopy() {
		Thumbnail rc = new Thumbnail();
		rc.setHeight(getHeight());
		rc.setImageBlob(getImageBlob());
		rc.setThumbnailFP(getThumbnailFP());
		rc.setRevision(getRevision());
		rc.setSourceId(getSourceId());
		rc.setSourceParentId(getSourceParentId());
		rc.setSourceType(getSourceType());
		rc.setWidth(getWidth());
        rc.setRotation(getRotation());
		return rc;
	}

	/**
	 * Convenience boolean test as to whether this is a thumbnail of a
	 * ChemElement or not
	 * 
	 * @return
	 */
	@Transient
	public boolean isChemThumbnail() {
		return SourceType.CHEM.equals(sourceType);
	}

	/**
	 * Convenience boolean test as to whether this is a thumbnail of an
	 * EcatImage or not
	 * 
	 * @return
	 */
	@Transient
	public boolean isImageThumbnail() {
		return SourceType.IMAGE.equals(sourceType);
	}
}
