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
@DiscriminatorValue("number")
public class NumberField extends FieldAsString {

	private static final long serialVersionUID = -8836223099190381195L;
	
	public NumberField(NumberFieldForm template) {
		this.fieldForm = template;
	}

	private NumberFieldForm fieldForm;

	@Transient
	public String getDecimalPlaces() {
		return fieldForm.getDecimalPlaces() == null ? "" : fieldForm.getDecimalPlaces() + "";
	}

	@Transient
	public String getMinNumberValue() {
		return fieldForm.getMinNumberValue() == null ? "" : fieldForm.getMinNumberValue() + "";
	}

	@Transient
	public String getMaxNumberValue() {
		return fieldForm.getMaxNumberValue() == null ? "" : fieldForm.getMaxNumberValue() + "";
	}

	@Transient
	public String getDefaultNumberValue() {
		return fieldForm.getDefaultNumberValue() == null ? "" : fieldForm.getDefaultNumberValue() + "";
	}

	@Override
	public NumberField shallowCopy() {
		NumberField numberfield = new NumberField(fieldForm);
		copyFields(numberfield);
		return numberfield;
	}

	@Transient
	public FieldForm _getFieldForm() {
		return fieldForm;
	}

	void _setFieldForm(NumberFieldForm fieldForm) {
		this.fieldForm = fieldForm;
	}

	@Override
	protected void _setFieldForm(IFieldForm ft) {
		if (ft instanceof NumberFieldForm) {
			_setFieldForm((NumberFieldForm) ft);
		} else if (isAuditingProxy(ft)) {
			_setFieldForm(new NumberFieldForm());
		}
	}


}
