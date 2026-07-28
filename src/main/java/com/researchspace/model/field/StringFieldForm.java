package com.researchspace.model.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

@Entity
@DiscriminatorValue(FieldType.STRING_TYPE)
@Audited
public class StringFieldForm extends FieldForm {

	public StringFieldForm(String name) {
		super(name);
		setType(FieldType.STRING);
	}

	public StringFieldForm() {
		setType(FieldType.STRING);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -1027880121882290768L;
	private String defaultStringValue = "";
	private boolean ifPassword;

	public String getDefaultStringValue() {
		return defaultStringValue;
	}

	public void setDefaultStringValue(String defaultStringValue) {
		this.defaultStringValue = defaultStringValue;
	}

	public boolean isIfPassword() {
		return ifPassword;
	}

	public void setIfPassword(boolean ifPassword) {
		this.ifPassword = ifPassword;
	}

	@Override
	public String toString() {
		return "StringFieldTemplate [defaultStringValue=" + defaultStringValue + ", ifPassword=" + ifPassword + "]"
				+ super.toString();
	}

	public StringFieldForm shallowCopy() {
		StringFieldForm nft = new StringFieldForm();
		copyPropertiesToCopy(nft);
		nft.defaultStringValue = defaultStringValue;
		nft.ifPassword = ifPassword;
		return nft;
	}

	public StringField _createNewFieldFromForm() {
		StringField cf = new StringField(this);
		return cf;
	}

	@Transient
	public String getSummary() {
		StringBuffer sb = new StringBuffer();
		String def = StringUtils.isEmpty(getDefaultStringValue()) ? "Unspecified" : getDefaultStringValue();
		sb.append("Type: [" + getType() + "], ").append("Is password: [" + isIfPassword() + "], ")
				.append("Default : [" + def + "] ");
		return sb.toString();
	}

	@Override
	public ErrorList validate(String data) {
		return new ErrorList();
	}

	@Override
	@Transient
	public String getDefault() {
		return getDefaultStringValue();
	}

}
