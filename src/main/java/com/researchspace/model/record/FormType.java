package com.researchspace.model.record;

/**
 * A discriminator for the type, or category of form, used to group forms based on likely usage
 */
public enum FormType {
	/**
	 * For RSpace document forms
	 */
	NORMAL,
	/**
	 * @deprecated - don't use for new forms.
	 */
	TEMPLATE,

}