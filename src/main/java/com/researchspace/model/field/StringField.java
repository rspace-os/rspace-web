package com.researchspace.model.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

@Entity
@Audited
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DiscriminatorValue("string")
public class StringField extends FieldAsString {

	private static final long serialVersionUID = -9034869690666898322L;

	private StringFieldForm fieldForm;

	public StringField(StringFieldForm fieldTemplate) {
		setFieldForm(fieldTemplate);
	}

	@Transient
	public String getData() {
		return super.getData();
	}

	@Transient
	public String getDefaultStringValue() {
		return _getFieldForm().getDefaultStringValue();
	}

	@Transient
	public boolean getIfPassword() {
		return _getFieldForm().isIfPassword();
	}

	@Override
	public StringField shallowCopy() {
		StringField stringField = new StringField(fieldForm);
		copyFields(stringField);

		return stringField;
	}

	@Transient
	public StringFieldForm _getFieldForm() {
		return fieldForm;
	}

	void _setFieldForm(StringFieldForm fieldForm) {
		this.fieldForm = fieldForm;
	}

	@Override
	protected void _setFieldForm(IFieldForm ft) {
		_setFieldForm(StringFieldBehaviour.realOrProxy(ft));
	}

}
