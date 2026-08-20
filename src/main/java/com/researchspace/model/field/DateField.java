package com.researchspace.model.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

@Entity
@DiscriminatorValue("date")
@Audited
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DateField extends FieldAsString {

	private DateFieldForm fieldForm;

	public DateField(DateFieldForm template) {
		super();
		setFieldForm(template);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 2350588520175243555L;

	@Transient
	public String getFormat() {
		return _getFieldForm().getFormat();
	}

	@Transient
	public long getDefaultDate() {
		return _getFieldForm().getDefaultDate();
	}

	@Transient
	public long getMinValue() {
		return _getFieldForm().getMinValue();
	}

	@Transient
	public long getMaxValue() {
		return _getFieldForm().getMaxValue();
	}

	@Override
	public DateField shallowCopy() {
		DateField dateField = new DateField(_getFieldForm());
		copyFields(dateField);

		return dateField;
	}

	@Transient
	public String getDefaultDateAsString() {
		return fieldForm.getDefaultDateAsString();

	}

	@Transient
	public String getMaxDateAsString() {
		return fieldForm.getMaxDateAsString();
	}

	@Transient
	public String getMinDateAsString() {
		return fieldForm.getMinDateAsString();
	}

	@Transient
	public DateFieldForm _getFieldForm() {
		return fieldForm;
	}

	@Override
	protected void _setFieldForm(IFieldForm ft) {
		_setFieldForm(DateFieldBehaviour.realOrProxy(ft));
	}

	protected void _setFieldForm(DateFieldForm fieldForm) {
		this.fieldForm = fieldForm;
	}

}
