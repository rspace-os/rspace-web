package com.researchspace.model.field;

import java.util.List;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import org.hibernate.envers.Audited;

/**
 * Choices are listed as a=1&b=2 syntax
 */
@Entity
@Audited
@DiscriminatorValue("choice")
public class ChoiceField extends FieldAsString {
	private ChoiceBehaviour internalChoiceBehaviour;

	protected ChoiceField() {
		this.internalChoiceBehaviour = new ChoiceBehaviour();
	}

	public ChoiceField(ChoiceFieldForm template) {
		this();
		setFieldForm(template);
	}

	private ChoiceFieldForm fieldForm;

	/**
	 * 
	 */
	private static final long serialVersionUID = -6222866058180609072L;

	@Transient
	public boolean isMultipleChoice() {
		return _getFieldForm().isMultipleChoice();
	}

	@Transient
	public String getChoiceOption() {
		return _getFieldForm().getChoiceOptions();
	}

	@Transient
	public String getDefaultChoiceOption() {
		return _getFieldForm().getDefaultChoiceOption();
	}

	@Override
	public ChoiceField shallowCopy() {
		ChoiceField cf = new ChoiceField(fieldForm);
		copyFields(cf);
		return cf;
	}

	/**
	 * Returns a <code>List</code> of choices, or an empty list if no choice
	 * options have yet been set.
	 * 
	 * @return
	 */
	@Transient
	public List<String> getChoiceOptionAsList() {
		return fieldForm.getChoiceOptionAsList();
	}

	@Transient
	public List<String> getChoiceOptionSelectedAsList() {
		return internalChoiceBehaviour.getChoiceOptionSelectedAsList(this::getFieldData);
	}

	@Transient
	public String getChoiceOptionSelectedAsString() {
		return internalChoiceBehaviour.getChoiceOptionSelectedAsString(this::getFieldData);
	}

	@Transient
	protected ChoiceFieldForm _getFieldForm() {
		return fieldForm;
	}

	void _setFieldForm(ChoiceFieldForm fieldTemplate) {
		this.fieldForm = fieldTemplate;
	}

	@Override
	protected void _setFieldForm(IFieldForm ft) {		
			_setFieldForm(ChoiceBehaviour.realOrProxy(ft));
	}

}
