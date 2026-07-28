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
@DiscriminatorValue("uri")
public class UriField extends FieldAsString {

	private static final long serialVersionUID = -9034869390666898322L;

	private URIFieldForm fieldForm;

	public UriField(URIFieldForm fieldForm) {
		setFieldForm(fieldForm);
	}

	@Transient
	public String getDefaultStringValue() {
		 return "";
	}

	@Override
	public UriField shallowCopy() {
		UriField sourceField = new UriField(fieldForm);
		copyFields(sourceField);
		return sourceField;
	}

	@Transient
	public URIFieldForm _getFieldForm() {
		return fieldForm;
	}

	void _setFieldForm(URIFieldForm fieldForm) {
		this.fieldForm = fieldForm;
	}

	@Override
	protected void _setFieldForm(IFieldForm ft) {	
		_setFieldForm(UriFieldBehaviour.realOrProxy(ft));		
	}

}
