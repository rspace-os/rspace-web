package com.researchspace.model.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

@Entity
@Audited
@DiscriminatorValue("att")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttachmentField extends FieldAsString {

	private static final long serialVersionUID = -9034869190666898322L;

	private AttachmentFieldForm fieldForm;

	public AttachmentField(AttachmentFieldForm fieldForm) {
		setFieldForm(fieldForm);
	}

	@Override
	public AttachmentField shallowCopy() {
		AttachmentField stringField = new AttachmentField(fieldForm);
		copyFields(stringField);
		return stringField;
	}

	@Transient
	public AttachmentFieldForm _getFieldForm() {
		return fieldForm;
	}

	void _setFieldForm(AttachmentFieldForm fieldForm) {
		this.fieldForm = fieldForm;
	}

	@Override
	protected void _setFieldForm(IFieldForm ft) {
		_setFieldForm(AttachmentFieldBehaviour.realOrProxy(ft));
	}

}
