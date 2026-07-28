package com.researchspace.model.field;

import jakarta.persistence.Transient;

public class NumberBehaviour {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8836223099190381195L;
	private NumberFieldForm fieldForm;

	NumberBehaviour() {
	}

	public NumberBehaviour(NumberFieldForm template) {
		this.fieldForm = template;
	}

	

	@Transient
	public String getDecimalPlaces() {
		return fieldForm.getDecimalPlaces() == null ? "" : fieldForm.getDecimalPlaces() + "";
	}

	@Transient
	public String getMinNumberValue() {
		return fieldForm.getMinNumberValue() == null ? "" : fieldForm.getMinNumberValue() + "";
	}

	@Transient
	public String getMaxNumberValue() {
		return fieldForm.getMaxNumberValue() == null ? "" : fieldForm.getMaxNumberValue() + "";
	}

	@Transient
	public String getDefaultNumberValue() {
		return fieldForm.getDefaultNumberValue() == null ? "" : fieldForm.getDefaultNumberValue() + "";
	}
	
	public static NumberFieldForm realOrProxyFieldForm (IFieldForm ft) {
		if (ft instanceof NumberFieldForm) {
			return (NumberFieldForm) ft;
		} else if (AbstractField.isAuditingProxy(ft)) {
			return new NumberFieldForm();
		} else 
			throw new IllegalStateException();
	}

}
