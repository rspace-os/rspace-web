package com.researchspace.model.inventory.field;

import com.researchspace.model.field.FieldType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.hibernate.envers.Audited;

@Entity
@DiscriminatorValue("text")
@Audited
public class InventoryTextField extends InventoryEntityField {

	private static final long serialVersionUID = -1757280226055607179L;

	public InventoryTextField() {
		this("");
	}
	
	public InventoryTextField(String name) {
		super(FieldType.TEXT, name);
	}

	@Override
	public InventoryTextField shallowCopy() {
		InventoryTextField copy = new InventoryTextField();
		copyFields(copy);
		return copy;
	}

}
