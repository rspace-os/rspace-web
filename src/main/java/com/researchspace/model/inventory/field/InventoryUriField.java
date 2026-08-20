package com.researchspace.model.inventory.field;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.FieldType;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

/**
 * Stores internal  links as a list of RSpace GlobalIds
 */
@Entity
@Audited
@DiscriminatorValue("uri")
public class InventoryUriField extends InventoryEntityField {

	private static final long serialVersionUID = -9034869390666898322L;

	public InventoryUriField() {
		this("");
	}
	
	public InventoryUriField(String name) {
		super(FieldType.URI, name);
	}

	
	@Transient
	public String getDefaultStringValue() {
		 return "";
	}

	@Override
	public InventoryUriField shallowCopy() {
		InventoryUriField sourceField = new InventoryUriField();
		copyFields(sourceField);
		return sourceField;
	}
	
	/**
	 * Fails if is not empty, and cannot be parsed into a URI,
	 */
	@Override
	public ErrorList validate(String fieldData) {
		ErrorList errorList = super.validate(fieldData);
		if (!StringUtils.isAllBlank(fieldData)) {
			try {
				new URI(fieldData);
			} catch (URISyntaxException e) {
				errorList.addErrorMsg("Invalid URI syntax; " + e.getMessage());
			}
		}
		return errorList;
	}

	/**
	 * Tries creating java.net.URI from the provided data string,
	 * returns error message, or null if conversion was successful. 
	 * 
	 * @param data
	 * @return error message or null if no error 
	 */
	private boolean canParseToUrl(String data) {
		try {
			new URL(data);
		} catch (MalformedURLException e) {
			return false;
		}
		return true;
	}

	@Override
	public boolean isSuggestedFieldForData(String data) {
		return canParseToUrl(data);
	}
	
	
}
