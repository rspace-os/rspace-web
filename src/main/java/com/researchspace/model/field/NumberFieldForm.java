package com.researchspace.model.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

@Entity
@DiscriminatorValue(FieldType.NUMBER_TYPE)
@Audited
public class NumberFieldForm extends FieldForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2176630683512542543L;
	private Double minNumberValue;
	private Double maxNumberValue;
	private Byte decimalPlaces;
	// there is now no default default value, see RSPAC-65
	private Double defaultNumberValue = null;

	public NumberFieldForm(String name) {
		super(name);
		setType(FieldType.NUMBER);

	}

	public NumberFieldForm() {
		setType(FieldType.NUMBER);
	}

	@Override
	@Transient
	public String getDefault() {
		if (getDefaultNumberValue() == null) {
			return "";
		} else if (getDecimalPlaces() != null && getDecimalPlaces() == 0) {
			// handles case where we explicitly want integers
			return defaultNumberValue.intValue() + "";
		} else {
			return getDefaultNumberValue() + "";
		}

	}

	public Double getMinNumberValue() {
		return minNumberValue;
	}

	public void setMinNumberValue(Double minNumberValue) {
		this.minNumberValue = minNumberValue;
	}

	public Double getMaxNumberValue() {
		return maxNumberValue;
	}

	@Transient
	public String getSummary() {
		StringBuffer sb = new StringBuffer();
		sb.append("Type: [" + getType() + "], ").append("Min value: [" + minNumberValue + "], ")
				.append("Max value: [" + maxNumberValue + "], ").append("Default value: [" + defaultNumberValue + "]");
		return sb.toString();
	}

	public void setMaxNumberValue(Double maxNumberValue) {
		this.maxNumberValue = maxNumberValue;
	}

	public Byte getDecimalPlaces() {
		return decimalPlaces;
	}

	public void setDecimalPlaces(Byte decimalPlaces) {
		this.decimalPlaces = decimalPlaces;
	}

	public Double getDefaultNumberValue() {
		return defaultNumberValue;
	}

	public void setDefaultNumberValue(Double defaultNumberValue) {
		this.defaultNumberValue = defaultNumberValue;
	}

	@Override
	public String toString() {
		return "NumberFieldTemplate [minNumberValue=" + minNumberValue + ", maxNumberValue=" + maxNumberValue
				+ ", decimalPlaces=" + decimalPlaces + ", defaultNumberValue=" + defaultNumberValue + "]";
	}

	public NumberFieldForm shallowCopy() {
		NumberFieldForm nft = new NumberFieldForm();
		copyPropertiesToCopy(nft);
		nft.decimalPlaces = decimalPlaces;
		nft.defaultNumberValue = defaultNumberValue;
		nft.maxNumberValue = maxNumberValue;
		nft.minNumberValue = minNumberValue;
		return nft;
	}

	public NumberField _createNewFieldFromForm() {
		NumberField cf = new NumberField(this);
		return cf;
	}

	@Override
	public ErrorList validate(String data) {

		ErrorList el = new ErrorList();
		if (StringUtils.isEmpty(data)) {
			return el;
		}
		Double dataN = null;
		try {
			dataN = Double.parseDouble(data);
		} catch (NumberFormatException nfe) {
			el.addErrorMsg("Invalid number format [" + data + "].");
			return el;
		}

		if (getDecimalPlaces() != null) {
			int dpIndx = data.indexOf(".");
			if (dpIndx != -1) {
				int dplaces = data.substring(dpIndx).length() - 1;
				if (dplaces > getDecimalPlaces()) {
					el.addErrorMsg("Data [" + data + "] has too many decimal places (should be [" + getDecimalPlaces()
							+ "] ).");
				}
			}
		}

		if (getMinNumberValue() != null) {
			if (!defaultValueAndValueIsZero(dataN) && dataN < getMinNumberValue()) {
				el.addErrorMsg("Data [" + data + "] smaller than minimum [" + getMinNumberValue() + "]");
				return el;
			}
		}

		if (!defaultValueAndValueIsZero(dataN) && getMaxNumberValue() != null) {
			if (dataN > getMaxNumberValue()) {
				el.addErrorMsg("Data [" + data + "] greater than maximum [" + getMaxNumberValue() + "]");
				return el;
			}
		}
		return el;
	}

	// this is for backwards compatibility, where default value is set to 0
	// rather than null if not set by user.
	boolean defaultValueAndValueIsZero(Double dataN) {
		return dataN.equals(0.0d) && defaultNumberValue != null && defaultNumberValue.equals(dataN);
	}


}
