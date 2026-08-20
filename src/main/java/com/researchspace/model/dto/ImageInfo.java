package com.researchspace.model.dto;

import java.io.Serializable;

import com.researchspace.model.EcatImage;

import lombok.Getter;
import lombok.Setter;

/**
 * Encapsulates information about an EcatImage.
 * 
 * Currently used just to retrieve original dimentions, name and caption of the image.
 */
@Getter
@Setter
public class ImageInfo implements Serializable {

	private static final long serialVersionUID = 7680090784229728654L;

	private Long id;

	private int width;
	private int height;

	private String name;
	private String description;

	public ImageInfo() {
		// empty constructor needed for json mapper
	}

	public ImageInfo(EcatImage image) {
		id = image.getId();
		width = image.getWidth();
		height = image.getHeight();
		name = image.getName();
		description = image.getDescription();
	}

}
