package com.researchspace.model.field;

import java.util.List;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

/**
 * Radio fields are stored as 'x=2&y=3' string format direct from request URL.
 */
@Entity
@Audited
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DiscriminatorValue("radio")
public class RadioField extends FieldAsString {

	private static final long serialVersionUID = -5576520929775736727L;

	private RadioFieldForm fieldForm;

	public RadioField(RadioFieldForm template) {
		setFieldForm(template);
	}

	@Transient
	public String getRadioOption() {
		return _getFieldForm().getRadioOption();
	}

	@Transient
	public String getDefaultRadioOption() {
		return _getFieldForm().getDefaultRadioOption();
	}

	/**
	 * Returns a <code>List</code> of radio options, or an empty list if no
	 * radio options have yet been set.
	 * 
	 * @return A possibly empty but not-null <code>List</code> of {@link String}
	 *         .
	 */
	@Transient
	public List<String> getRadioOptionAsList() {
		return fieldForm.getRadioOptionAsList();
	}

	@Override
	public RadioField shallowCopy() {
		RadioField radioField = new RadioField(fieldForm);
		copyFields(radioField);
		return radioField;
	}

	@Transient
	public RadioFieldForm _getFieldForm() {
		return fieldForm;
	}

	@Override
	protected void _setFieldForm(IFieldForm ft) {
		_setFieldForm(RadioBehaviour.realOrProxy(ft));		
	}

	void _setFieldForm(RadioFieldForm fieldForm) {
		this.fieldForm = fieldForm;
	}

}
