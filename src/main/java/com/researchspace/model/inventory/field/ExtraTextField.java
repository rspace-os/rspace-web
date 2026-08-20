package com.researchspace.model.inventory.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.hibernate.envers.Audited;

import com.researchspace.model.field.FieldType;

import lombok.EqualsAndHashCode;

@Entity
@Audited
@EqualsAndHashCode(callSuper = true)
@DiscriminatorValue("text")
public class ExtraTextField extends ExtraField {

	private static final long serialVersionUID = 8082597651557916021L;
	private static final String DEFAULT_NAME = "Data";
	
	public ExtraTextField() {
		setName(DEFAULT_NAME);
	}

	@Transient
	@Override
	public FieldType getType() {
		return FieldType.TEXT;
	}

	@Override
	public String validateNewData(String data) {
		return null;
	}
	
	@Override
	public ExtraTextField shallowCopy() {
		ExtraTextField copy = new ExtraTextField();
		copyProperties(copy);
		return copy;
	}

}
