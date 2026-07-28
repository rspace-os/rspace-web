package com.researchspace.model.inventory.field;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.FieldType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.hibernate.envers.Audited;

@Entity()
@DiscriminatorValue("date")
@Audited
public class InventoryDateField extends InventoryEntityField {

	private static final long serialVersionUID = 2350588560175243555L;

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	public InventoryDateField() {
		this("");
	}
	
	public InventoryDateField(String name) {
		super(FieldType.DATE, name);
	}

	@Override
	public ErrorList validate(String fieldData){
		ErrorList errors = super.validate(fieldData);
		if(dateHasContentAndIsInvalid(fieldData)) {
				errors.addErrorMsg(String.format("%s is an invalid date format. Valid format is yyyy-MM-dd.", fieldData));
			}
		return errors;
	}

	private boolean dateHasContentAndIsInvalid(String date){
		return date != null && !date.isEmpty() && !isValidDateFormat(date);
	}
	
	@Override
	public boolean isSuggestedFieldForData(String dateString) {
		return isValidDateFormat(dateString);
	}

	private boolean isValidDateFormat(String date){
		try {
			LocalDate.parse(date, DATE_FORMATTER);
		} catch (DateTimeParseException e) {
			return false;
		}
		return true;
	}
	
	@Override
	public InventoryDateField shallowCopy() {
		InventoryDateField dateField = new InventoryDateField();
		copyFields(dateField);
		return dateField;
	}

}
