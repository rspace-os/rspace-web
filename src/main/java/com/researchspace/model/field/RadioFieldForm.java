package com.researchspace.model.field;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;

@Entity
@DiscriminatorValue(FieldType.RADIO_TYPE)
@Audited
public class RadioFieldForm extends FieldForm {

	private RadioBehaviour radioBehaviour;
	public RadioFieldForm(String name) {
		super(name);
		setType(FieldType.RADIO);
		this.radioBehaviour= new RadioBehaviour();
	}

	public RadioFieldForm() {
		this(null);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 790708738154826979L;

	private String radioOption;
	private String defaultRadioOption;
	private boolean showAsPickList;
	private boolean sortAlphabetic;

	@Override
	public String toString() {
		return super.toString() + ", RadioFieldTemplate [radioOption=" + radioOption + ", defaultRadioOption="
				+ defaultRadioOption + "]";
	}

	@NotBlank(message = "radio options {errors.required.field}")
	public String getRadioOption() {
		return radioOption;
	}

	public void setRadioOption(String radioOption) {
		this.radioOption = radioOption;
	}

	public String getDefaultRadioOption() {
		return defaultRadioOption;
	}

	public void setDefaultRadioOption(String defaultRadioOption) {
		this.defaultRadioOption = defaultRadioOption;
	}

	public RadioFieldForm shallowCopy() {
		RadioFieldForm nft = new RadioFieldForm();
		copyPropertiesToCopy(nft);
		nft.defaultRadioOption = defaultRadioOption;
		nft.radioOption = radioOption;
		nft.showAsPickList = this.showAsPickList;
		nft.setSortAlphabetic(this.isSortAlphabetic());
		return nft;
	}

	@Transient
	public String getSummary() {
		StringBuffer sb = new StringBuffer();
		String def = StringUtils.isEmpty(getDefaultRadioOption()) ? "Unspecified" : getDefaultRadioOption();
		sb.append("Type: [" + getType() + "], ")
				.append("Choices: [" + Arrays.toString(getRadioOptionAsList().toArray()) + "], ")
				.append("Default selection: [" + def + "] ");
		return sb.toString();
	}

	public RadioField _createNewFieldFromForm() {
		RadioField cf = new RadioField(this);
		setDefaultIfPresent(cf);
		return cf;
	}

	@Transient
	public List<String> getRadioOptionAsList() {
		return radioBehaviour.getAsList(getRadioOption());
	}

	@Override
	public ErrorList validate(String data) {
		ErrorList el = new ErrorList();
		if (data.length() > 0 && !getRadioOptionAsList().contains(data)) {
			el.addErrorMsg("Invalid data for Radio Field [" + data + "]");
		}
		return el;
	}

	@Override
	@Transient
	public String getDefault() {
		return getDefaultRadioOption();
	}


	@Column(nullable = false)
	@ColumnDefault("false")
	public boolean isShowAsPickList() {
		return showAsPickList;
	}

	public void setShowAsPickList(boolean showAsPickList) {
		this.showAsPickList = showAsPickList;
	}

	@Column(nullable = false)
	@ColumnDefault("false")
	public boolean isSortAlphabetic() {
		return sortAlphabetic;
	}

	public void setSortAlphabetic(boolean sortAlphabetic) {
		this.sortAlphabetic = sortAlphabetic;
	}
}
