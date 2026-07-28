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
public class EcatAudio extends EcatMediaFile implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public EcatAudio () {}
	
    public EcatAudio (ImportOverride override) {
		super(override);
	}

	@Transient
	@Override
	public String getRecordInfoType() {
		return DOCUMENT_CATEGORIES.ECATAUDIO;
	}

	@Override
	public EcatAudio copy() {
		EcatAudio copy = new EcatAudio();
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
	public boolean isAudio(){
		return true;
	}

}
