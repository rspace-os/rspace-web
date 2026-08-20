package com.researchspace.model.field;

public class FieldDataValidationResult {

	private ErrorList errorList = new ErrorList();

	private boolean valid;

	public ErrorList getErrorList() {
		return errorList;
	}

	void setErrorList(ErrorList errorList) {
		this.errorList = errorList;
	}

	public boolean isValid() {
		return valid;
	}

	void setValid(boolean valid) {
		this.valid = valid;
	}

}
