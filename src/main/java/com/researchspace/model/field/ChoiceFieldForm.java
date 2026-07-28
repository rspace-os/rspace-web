package com.researchspace.model.field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;

@Entity
@DiscriminatorValue(FieldType.CHOICE_TYPE)
@Audited
public class ChoiceFieldForm extends FieldForm {

	public ChoiceFieldForm(String name) {
		super(name);
		setType(FieldType.CHOICE);
	}

	public ChoiceFieldForm() {
		setType(FieldType.CHOICE);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1974158800405393625L;

	private boolean multipleChoice;
	private String choiceOptions;
	private String defaultChoiceOption;

	public boolean isMultipleChoice() {
		return multipleChoice;
	}

	public void setMultipleChoice(boolean multipleChoice) {
		this.multipleChoice = multipleChoice;
	}

	@Column(length = 1000)
	@NotBlank(message = "choice options {errors.required.field}")
	public String getChoiceOptions() {
		return choiceOptions;
	}

	public void setChoiceOptions(String choiceOptions) {
		this.choiceOptions = choiceOptions;
	}

	public String getDefaultChoiceOption() {
		return defaultChoiceOption;
	}

	public void setDefaultChoiceOption(String defaultChoiceOption) {
		this.defaultChoiceOption = defaultChoiceOption;
	}

	@Override
	public ChoiceFieldForm shallowCopy() {
		ChoiceFieldForm cft = new ChoiceFieldForm();
		copyPropertiesToCopy(cft);
		cft.multipleChoice = multipleChoice;
		cft.defaultChoiceOption = defaultChoiceOption;
		cft.choiceOptions = choiceOptions;
		return cft;
	}

	@Override
	public ChoiceField _createNewFieldFromForm() {
		ChoiceField cf = new ChoiceField(this);
		return cf;
	}

	@Transient
	public List<String> getChoiceOptionAsList() {
		List<String> result = new ArrayList<>();
		if (StringUtils.isEmpty(getChoiceOptions())) {
			return result;
		}

		String[] options = getChoiceOptions().split("&");

		for (String v : options) {
			result.add(v.split("=")[1]);
		}

		return result;
	}

	@Transient
	public String getSummary() {
		StringBuffer sb = new StringBuffer();
		sb.append("Type: [" + getType() + "], ")
				.append("Choices: [" + Arrays.toString(getChoiceOptionAsList().toArray()) + "], ")
				.append("Default selection: [" + Arrays.toString(getDefaultChoiceOptionAsList().toArray()) + "] ");
		return sb.toString();
	}

	@Transient
	public List<String> getDefaultChoiceOptionAsList() {
		List<String> result = new ArrayList<>();
		if (StringUtils.isEmpty(getDefaultChoiceOption())) {
			return result;
		}
		String[] options = getDefaultChoiceOption().split("&");

		for (String v : options) {
			result.add(v.split("=")[1]);
		}
		return result;
	}

	@Override
	public ErrorList validate(String data) {
		ErrorList el = new ErrorList();
		if (data.length() > 0 && !FieldUtils.isValidRadioOrChoiceString(data)) {
			el.addErrorMsg("Invalid data for Choice Field [" + data + "]");
		}
		return el;
	}

	@Transient
	@Override
	public String getDefault() {
		return getDefaultChoiceOption();
	}


}
