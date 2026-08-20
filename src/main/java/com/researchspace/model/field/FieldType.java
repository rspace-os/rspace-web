package com.researchspace.model.field;

import static org.apache.commons.lang3.StringUtils.capitalize;

/**
 * Defines constants for the data type of fields in a Structured Document.
 */
public enum FieldType {

	NUMBER("Number"), STRING("String"), TEXT("Text"), RADIO("Radio"),
	CHOICE("Choice"), DATE("Date"), TIME("Time"), REFERENCE("Reference"), ATTACHMENT("Attachment"),
	URI("Uri"), IDENTIFIER("Identifier"), LINK("Link");

	private String type;
	public static final String NUMBER_TYPE = "Number";
	public static final String STRING_TYPE = "String";
	public static final String TEXT_TYPE = "Text";
	public static final String CHOICE_TYPE = "Choice";
	public static final String RADIO_TYPE = "Radio";
	public static final String DATE_TYPE = "Date";
	public static final String TIME_TYPE = "Time";
	public static final String REFERENCE_TYPE = "Reference";
	public static final String ATTACHMENT_TYPE = "Attachment";
	public static final String URI_TYPE = "Uri";
	public static final String IDENTIFIER_TYPE = "Identifier";
	public static final String LINK_TYPE = "Link";

	/**
	 * Gets the field type for an input String or <code>null</code> if the argument
	 * could not be matched to an enum. <br/>
	 * Accepted inputs are String representations of the enum constant - comparison
	 * is case-insensitive
	 * 
	 * @param fieldType
	 * @return A {@link FieldType} or null if no FieldType could be matched to an
	 *         enum.
	 */
	public static FieldType getFieldTypeForString(String fieldType) {
		switch (capitalize(fieldType.toLowerCase())) {

		case NUMBER_TYPE:
			return NUMBER;
		case STRING_TYPE:
			return STRING;
		case TEXT_TYPE:
			return TEXT;
		case RADIO_TYPE:
			return RADIO;
		case CHOICE_TYPE:
			return CHOICE;
		case DATE_TYPE:
			return DATE;
		case TIME_TYPE:
			return TIME;
		case REFERENCE_TYPE:
			return REFERENCE;
		case ATTACHMENT_TYPE:
			return ATTACHMENT;
		case URI_TYPE:
			return URI;
		case IDENTIFIER_TYPE:
			return IDENTIFIER;
		case LINK_TYPE:
			return LINK;
		default:
			return null;
		}
	}

	private FieldType(String type) {
		this.type = type;
	}

	/**
	 * Gets a CamelCased representation of the enum.
	 * 
	 * @return A <code>String</code>.
	 */
	public String getType() {
		return type;
	}

}