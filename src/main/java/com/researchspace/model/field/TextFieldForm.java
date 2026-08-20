package com.researchspace.model.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

@Entity
@DiscriminatorValue(FieldType.TEXT_TYPE)
@Audited
public class TextFieldForm extends FieldForm {
	public TextFieldForm(String name) {
		super(name);
		setType(FieldType.TEXT);
	}

	public TextFieldForm() {
		setType(FieldType.TEXT);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 5093253483834901487L;

	private String defaultValue;

	@Override
	public String toString() {
		return "TextFieldTemplate [defaultValue=" + defaultValue + "]";
	}

	@Lob
	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public TextFieldForm shallowCopy() {
		TextFieldForm nft = new TextFieldForm();
		copyPropertiesToCopy(nft);
		nft.defaultValue = defaultValue;

		return nft;
	}

	public TextField _createNewFieldFromForm() {
		TextField cf = new TextField(this);
		cf.setRtfData(getDefaultValue());
		return cf;
	}
	
	@Transient
	public String getSummary() {
		StringBuffer sb = new StringBuffer();
		String def = StringUtils.isEmpty(getDefaultValue()) ? "Unspecified" : getDefaultValue();
		sb.append("Type: [" + getType() + "], ").append("Default : [" + def + "] ");
		return sb.toString();
	}

	@Override
	public ErrorList validate(String data) {
		return new ErrorList();
	}

	@Override
	@Transient
	public String getDefault() {
		return getDefaultValue();
	}

}
