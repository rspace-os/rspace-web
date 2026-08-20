package com.researchspace.model.units;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidA260InputValidator implements ConstraintValidator<ValidA260Input, A260Input> {

	@Override
	public void initialize(ValidA260Input constraintAnnotation) {
		;
	}

	@Override
	public boolean isValid(A260Input value, ConstraintValidatorContext context) {
		if(value == null) {
			return true;
		}
		// this will generate a denominator of 0
		if(value.getA280()!=null && value.getA320()!=null && value.getA280() <= value.getA320()) {
			return false;
		}
		if(value.getA320()!=null && value.getA260()  <= value.getA320()) {
		  return false;
		}
		return true;
	}

}
