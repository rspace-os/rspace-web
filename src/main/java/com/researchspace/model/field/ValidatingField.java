package com.researchspace.model.field;

/**
 * Mixin for field validation
 */
public interface ValidatingField  {
	
	 FieldType getType();
	
	 ErrorList validate(String data);
	 
	 String getName();

}
