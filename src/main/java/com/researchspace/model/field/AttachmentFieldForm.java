package com.researchspace.model.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.hibernate.envers.Audited;

import lombok.ToString;

/**
 * AttachmentFieldForm defines a field to support file upload and hold a reference to a file.
 * It performs no validation (unclear what should be validated)
 *
 */
@Entity
@DiscriminatorValue(FieldType.ATTACHMENT_TYPE)
@Audited
@ToString
public class AttachmentFieldForm extends FieldForm {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -1027880121882290768L;

	public AttachmentFieldForm(String name) {
		super(name);
		setType(FieldType.ATTACHMENT);
	}

	public AttachmentFieldForm() {
		setType(FieldType.ATTACHMENT);
	}

	public AttachmentFieldForm shallowCopy() {
		AttachmentFieldForm nft = new AttachmentFieldForm();
		copyPropertiesToCopy(nft);
		return nft;
	}

	public Field _createNewFieldFromForm() {
		AttachmentField cf = new AttachmentField(this);
		return cf;
	}

	@Transient
	public String getSummary() {
		return toString();
	}

	@Override
	public ErrorList validate(String data) {
		return new ErrorList();
	}


	@Override
	@Transient
	public String getDefault() {
		return "";
	}

}
