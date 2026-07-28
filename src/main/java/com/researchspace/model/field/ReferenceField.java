package com.researchspace.model.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

/**
 * Stores internal  links as a list of RSpace GlobalIds
 */
@Entity
@Audited
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DiscriminatorValue("reference")
public class ReferenceField extends FieldAsString {

	private static final long serialVersionUID = -9034869690666898322L;

	private ReferenceFieldForm fieldForm;

	public ReferenceField(ReferenceFieldForm fieldForm) {
		setFieldForm(fieldForm);
	}

	@Transient
	public String getDefaultStringValue() {
		 return "";
	}

	@Override
	public ReferenceField shallowCopy() {
		ReferenceField sourceField = new ReferenceField(fieldForm);
		copyFields(sourceField);
		return sourceField;
	}

	@Transient
	public ReferenceFieldForm _getFieldForm() {
		return fieldForm;
	}

	void _setFieldForm(ReferenceFieldForm fieldForm) {
		this.fieldForm = fieldForm;
	}

	@Override
	protected void _setFieldForm(IFieldForm ft) {	
		_setFieldForm(ReferenceFieldBehaviour.realOrProxy(ft));		
	}

}
