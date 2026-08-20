package com.researchspace.model.record;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.researchspace.model.BaseEntity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import static com.researchspace.core.util.imageutils.ImageUtils.scaleImageToWidthWithAspectRatio;
import static com.researchspace.core.util.imageutils.ImageUtils.toBytes;

/**
 * class mapping for small size image store in database
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@ToString(of={"id", "parentId", "width", "height", "imgName", "imgType"})
@EqualsAndHashCode(of={"imgName", "width", "height", "id"})
public class IconEntity implements Serializable {
	@Transient
	private static final long serialVersionUID = BaseEntity.serialVersionUID;
	
	/**
	 * Static factory class to create a transient IconEntity for an image.
	 * <br/> This method scales image if possible to default thumbnail size of 64
	 * @param parentId The Id of the entity that owns this icon (typically a Form)
	 * @param img A buffered image
	 * @param optionalImageType an image type file suffix, e.g. 'png', 'jpeg'. Can be null or empty
	 * @return The created IconEntity 
	 * @throws IOException
	 */
	public static  IconEntity createIconEntityFromImage(Long parentId,  BufferedImage img, String optionalImageType)
			throws IOException {
		final int thumnailSize = 64;
		BufferedImage scaled = scaleImageToWidthWithAspectRatio(img, thumnailSize);
		IconEntity ice = new IconEntity();
		ice.setHeight(scaled.getHeight());
		ice.setWidth(scaled.getWidth());
		String imageType ="png";
		if(!StringUtils.isBlank(optionalImageType)) {
			imageType = optionalImageType;
		}
		ice.setIconImage(toBytes(scaled,imageType));
		ice.setImgType(imageType);

		String inm = parentId + "_icon";
		ice.setImgName(inm);
		ice.setParentId(parentId);	
		return ice;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id = -1L;
	private Long parentId;// form id
	// otherwise is long blob -4Gb, too	 big
	@Column(columnDefinition = "MEDIUMBLOB") 
	private byte[] iconImage;
	private int width = 32;
	private int height = 32;
	private String imgName;
	private String imgType; // file extension

	public byte[] getIconImage() {
		return iconImage == null ? null : iconImage.clone();
	}

	public void setIconImage(byte[] iconImage) {
		this.iconImage = iconImage == null ? null : iconImage.clone();
	}

}
