package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.Validate;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.DOCUMENT_CATEGORIES;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.RecordInformation;

@Entity
@Audited
@Indexed
public class EcatImage extends EcatMediaFile implements Serializable {

	private static final long serialVersionUID = -1L;
	private ImageBlob workingImage;
	private ImageBlob imageThumbnailed;
	private int widthResized;
	private int heightResized;
	private int width;
	private int height;
	private byte rotation;

	private EcatImage originalImage;
	private long originalImageVersion = 1;
	
	private FileProperty workingImageFP;
	
	private FileProperty thumbnailImageFP;
	
    public EcatImage () {}
	
    public EcatImage (ImportOverride override) {
		super(override);
	}
	
	/**
	 * RSPAC-2191 - new mechanism for storing working images
	 * @return
	 */
	@ManyToOne
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	public FileProperty getWorkingImageFP() {
		return workingImageFP;
	}

	public void setWorkingImageFP(FileProperty workingImageFP) {
		this.workingImageFP = workingImageFP;
	}

	/**
	 * RSPAC-2191 - new mechanism for storing thumbnails
	 * @return
	 */
	@ManyToOne
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	public FileProperty getThumbnailImageFP() {
		return thumbnailImageFP;
	}

	public void setThumbnailImageFP(FileProperty thumbnailImageFP) {
		this.thumbnailImageFP = thumbnailImageFP;
	}

	/**
	 * The maximum displayed width for an uploaded image
	 */
	public static final int MAX_PAGE_DISPLAY_WIDTH = 644;
	
	/**
	 * The maximum size of image we want to read into memory in one go
	 */
	public static final Long MAX_IMAGE_IN_MEMORY = 5_242_880L;// 5Mb

	@ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH},
			 fetch = FetchType.LAZY)
	@JoinColumn(name = "imageFileRezisedEditor_id") // for backwards compatibility
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	public ImageBlob getWorkingImage() {
		return workingImage;
	}

	public void setWorkingImage(ImageBlob workingImage) {
		this.workingImage = workingImage;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}
	/**
	 * The multiple of 90 degrees clockwise to rotate the image by.
	 * @param rotationDelta
	 * @return this for chaining
	 */
	public EcatImage rotate(byte rotationDelta) {
		validateRotation(rotationDelta);
		setRotation((byte)((this.rotation + rotationDelta) % 4));
		return this;
	}
	//package scoped for hbernate
	void setRotation(byte rotation) {
		validateRotation(rotation);
		this.rotation = rotation;
		
	}

	private void validateRotation(byte rotation) {
		Validate.isTrue(rotation >= 0 &&  rotation <= 3, "Rotation value must be one of 0,1,2,3");
	}
	public byte getRotation() {
		return rotation;
	}

	@Override
	public String toString() {
		return "EcatImage [widthResized=" + widthResized + ", heightResized=" + heightResized + ", width=" + width
				+ ", height=" + height + ", imageType=" + getExtension() + "]";
	}
	
	@ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH},
			 fetch = FetchType.LAZY)
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	public ImageBlob getImageThumbnailed() {
		return imageThumbnailed;
	}

	public void setImageThumbnailed(ImageBlob imageThumbnailed) {
		this.imageThumbnailed = imageThumbnailed;
	}

	public int getWidthResized() {
		return widthResized;
	}

	public void setWidthResized(int widthResized) {
		this.widthResized = widthResized;
	}

	public int getHeightResized() {
		return heightResized;
	}

	public void setHeightResized(int heightResized) {
		this.heightResized = heightResized;
	}

	/**
	 * @return image that was used as a source for creating current image, or null
	 */
	// Lazy is load-bearing: Hibernate 6 join-fetches eager to-one associations in one SQL, and
	// this self-reference re-inlines the whole record graph per level, exceeding MariaDB's
	// 61-table join limit (error 1116) on a plain load.
	@ManyToOne(fetch = FetchType.LAZY)
	@NotAudited
	public EcatImage getOriginalImage() {
		return originalImage;
	}

	public void setOriginalImage(EcatImage originalImage) {
		this.originalImage = originalImage;
	}

	/**
	 * @return version of the image that was used as a source for creating current image 
	 */
	@NotAudited
	public long getOriginalImageVersion() {
		return originalImageVersion;
	}

	public void setOriginalImageVersion(long originalImageVersion) {
		this.originalImageVersion = originalImageVersion;
	}

	/**
	 * @return versioned oid of the original image, or null if there is no original image
	 */
	@Transient
	public GlobalIdentifier getOriginalImageOid() {
		if (getOriginalImage() == null) {
			return null;
		}
		GlobalIdentifier originalOid = getOriginalImage().getOid();
		return new GlobalIdentifier(originalOid.getPrefix(), originalOid.getDbId(), getOriginalImageVersion());
	}

	@Transient
	@Override
	public String getRecordInfoType() {
		return DOCUMENT_CATEGORIES.ECATIMAGE;
	}

	@Override
	public EcatImage copy() {
		EcatImage copy = new EcatImage();
		copy.setWorkingImage(getWorkingImage());
		copy.setImageThumbnailed(getImageThumbnailed());
		copy.setWorkingImageFP(getWorkingImageFP());
		copy.setThumbnailImageFP(getThumbnailImageFP());
		copy.setWidth(getWidth());
		copy.setHeight(getHeight());
		copy.setHeightResized(getHeightResized());
		copy.setWidthResized(getWidthResized());
		copy.setOriginalImage(getOriginalImage());
		copy.setOriginalImageVersion(getOriginalImageVersion());
		this.shallowCopyEcatMedia(copy);
		return copy;
	}

	public RecordInformation toRecordInfo() {
		RecordInformation info = super.toRecordInfo();
		info.setWidthResized(getWidthResized());
		info.setHeightResized(getHeightResized());
		info.setRotation(getRotation());
		info.setOriginalImageOid(getOriginalImageOid());
		return info;
	}

	@Transient
	public boolean isImage() {
		return true;
	}

}
