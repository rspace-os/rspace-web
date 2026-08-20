package com.researchspace.model.inventory.field;

import com.researchspace.model.field.FieldType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.hibernate.envers.Audited;

/**
 * Stores internal  links as a list of RSpace GlobalIds
 */
@Entity
@Audited
@DiscriminatorValue("reference")
public class InventoryReferenceField extends InventoryEntityField {

	private static final long serialVersionUID = -9034869690666898322L;

	public InventoryReferenceField() {
		this("");
	}
	
	public InventoryReferenceField(String name) {
		super(FieldType.REFERENCE, name);
	}

	@Transient
	public String getDefaultStringValue() {
		 return "";
	}

	@Override
	public InventoryReferenceField shallowCopy() {
		InventoryReferenceField sourceField = new InventoryReferenceField();
		copyFields(sourceField);
		return sourceField;
	}

}
