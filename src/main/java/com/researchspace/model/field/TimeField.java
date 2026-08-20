package com.researchspace.model.field;

import java.text.ParseException;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

@Entity
@DiscriminatorValue("time")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Audited
public class TimeField extends FieldAsString {

	private TimeFieldForm fieldForm;

	public TimeField(TimeFieldForm template) {
		setFieldForm(template);
	}

	private static final long serialVersionUID = 8709980361506172103L;

	@Transient
	public String getTimeFormat() {
		return _getFieldForm().getTimeFormat();
	}

	@Transient
	public long getDefaultTime() {
		return _getFieldForm().getDefaultTime();
	}

	@Transient
	public long getMinTime() {
		return _getFieldForm().getMinTime();
	}

	@Transient
	public long getMaxTime() {
		return _getFieldForm().getMaxTime();
	}

	@Transient
	public String getDefaultTimeAsString() {
		return fieldForm.getDefaultTimeAsString();
	}

	@Transient
	public String getmaxTimeAsString() {
		return fieldForm.getmaxTimeAsString();
	}

	@Transient
	public int getmaxHour() throws ParseException {
		return fieldForm.getmaxHour();
	}

	@Transient
	public int getmaxMinutes() throws ParseException {
		return fieldForm.getmaxMinutes();
	}

	@Transient
	public int getminHour() throws ParseException {
		return fieldForm.getminHour();
	}

	@Transient
	public int getminMinutes() throws ParseException {
		return fieldForm.getminMinutes();
	}

	@Transient
	public String getMinTimeAsString() {
		return fieldForm.getMinTimeAsString();
	}

	@Override
	public TimeField shallowCopy() {
		TimeField timeField = new TimeField(fieldForm);
		copyFields(timeField);
		return timeField;
	}

	@Transient
	public TimeFieldForm _getFieldForm() {
		return fieldForm;
	}

	void _setFieldForm(TimeFieldForm fieldForm) {
		this.fieldForm = fieldForm;
	}

	@Override
	protected void _setFieldForm(IFieldForm ft) {
		_setFieldForm(TimeFieldBehaviour.realOrProxy(ft));
	}

}
