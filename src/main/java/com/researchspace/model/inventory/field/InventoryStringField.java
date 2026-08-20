package com.researchspace.model.inventory.field;

import com.researchspace.model.field.FieldType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.hibernate.envers.Audited;

@Entity
@Audited
@DiscriminatorValue("string")
public class InventoryStringField extends InventoryEntityField {

	private static final long serialVersionUID = -9034869690666898322L;

	public InventoryStringField() {
		this("");
	}
	
	public InventoryStringField(String name) {
		super(FieldType.STRING, name);
	}


	@Override
	public InventoryStringField shallowCopy() {
		InventoryStringField stringField = new InventoryStringField();
		copyFields(stringField);
		return stringField;
	}

}
