package com.researchspace.model.inventory.field;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.FieldType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.hibernate.envers.Audited;

@Entity
@Audited
@DiscriminatorValue("number")
public class InventoryNumberField extends InventoryEntityField {

	private static final long serialVersionUID = -8836223099190381195L;

	public InventoryNumberField() {
		this("");
	}

	public InventoryNumberField(String name) {
		super(FieldType.NUMBER, name);
	}

	/**
	 * Fails if is not empty, and cannot be parsed into a Double,
	 */
	@Override
	public ErrorList validate(String fieldData) {
		ErrorList el = super.validate(fieldData);
		if (!StringUtils.isAllBlank(fieldData) && !NumberUtils.isCreatable(fieldData)) {
			 el.addErrorMsg("Invalid number: " + fieldData);
		}
		return el;
	}

	@Override
	public boolean isSuggestedFieldForData(String data) {
		return !validate(data).hasErrorMessages();
	}
	
	@Override
	public InventoryNumberField shallowCopy() {
		InventoryNumberField numberfield = new InventoryNumberField();
		copyFields(numberfield);
		return numberfield;
	}

}
