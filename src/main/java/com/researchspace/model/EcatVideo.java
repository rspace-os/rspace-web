package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.researchspace.model.record.DOCUMENT_CATEGORIES;
import com.researchspace.model.record.ImportOverride;

@Entity
@Audited
@Indexed
public class EcatVideo extends EcatMediaFile implements Serializable {
	
	public EcatVideo () {}

	public EcatVideo (ImportOverride override) {
		super(override);
	}

	@Override
	public String toString() {
		return "EcatVideo [width=" + width + ", height=" + height + ", editInfo=" + getEditInfo() + "]";
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int width;
	private int height;

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

	@Transient
	@Override
	public String getRecordInfoType() {
		return DOCUMENT_CATEGORIES.ECATVIDEO;
	}

	@Override
	public EcatVideo copy() {
		EcatVideo copy = new EcatVideo();
		copy.setWidth(this.getWidth());
		copy.setHeight(this.getHeight());
		this.shallowCopyEcatMedia(copy);
		return copy;
	}

	@Override
	@Transient
	public boolean isAV() {
		return true;
	}
	
	@Transient
	@Override
	public boolean isVideo(){
		return true;
	}

}
