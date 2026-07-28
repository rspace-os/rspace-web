package com.researchspace.model.inventory.field;

import java.math.BigDecimal;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

import com.researchspace.model.field.FieldType;

import lombok.EqualsAndHashCode;

@Entity
@Audited
@EqualsAndHashCode(callSuper = true)
@DiscriminatorValue("number")
public class ExtraNumberField extends ExtraField {

	private static final long serialVersionUID = 616794358851234028L;
	private static final String DEFAULT_NAME = "Numeric data";
	
	public ExtraNumberField() {
		setName(DEFAULT_NAME);
	}
	
	

	/**
	 * Checks if the passed value contains valid number, then saves it as a field value. 
	 * @param data
	 */
	@Override
	public void setData(String data) {
		String validationMsg = validateNewData(data);
		if (validationMsg != null) {
			throw new IllegalArgumentException(validationMsg);
		}
		super.setData(data);
	}

	@Transient
	@Override
	public FieldType getType() {
		return FieldType.NUMBER;
	}

	@Override
	public String validateNewData(String data) {
		if (StringUtils.isNotEmpty(data)) {
			try {
				new BigDecimal(data);
			} catch (NumberFormatException nfe) {
				return "'" + data + "' cannot be parsed into number";
			}
		}
		return null;
	}

	@Override
	public ExtraNumberField shallowCopy() {
		ExtraNumberField copy = new ExtraNumberField();
		copyProperties(copy);
		return copy;
	}

}
