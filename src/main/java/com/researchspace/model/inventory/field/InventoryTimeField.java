package com.researchspace.model.inventory.field;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.FieldType;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.hibernate.envers.Audited;

@Entity
@DiscriminatorValue("time")
@Audited
public class InventoryTimeField extends InventoryEntityField {

	private static final long serialVersionUID = 8709980361506172103L;

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(
			ResolverStyle.STRICT);
	
	public InventoryTimeField() {
		this("");
	}
	
	public InventoryTimeField(String name) {
		super(FieldType.TIME, name);
	}

	@Override
	public boolean isSuggestedFieldForData(String data) {
		return isValidTimeFormat(data);
	}

	@Override
	public ErrorList validate(String fieldData){
		ErrorList errors = super.validate(fieldData);
		if(timeHasContentAndIsInvalid(fieldData)){
				errors.addErrorMsg(String.format("%s is an invalid 24hour time format. Valid format is HH:mm.", fieldData));
		}
		return errors;
	}

	private boolean timeHasContentAndIsInvalid(String time){
		return time != null && !time.isEmpty() && !isValidTimeFormat(time);
	}

	private boolean isValidTimeFormat(String time){
		try {
			LocalTime.parse(time, TIME_FORMATTER);
		} catch (DateTimeParseException e) {
			return false;
		}
		return true;
	}
	
	@Override
	public InventoryTimeField shallowCopy() {
		InventoryTimeField timeField = new InventoryTimeField();
		copyFields(timeField);
		return timeField;
	}
}
