package com.researchspace.model.units;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Amount constraint validator for Quantifiable objects annotated with @ValidAmount
 */
public class AmountConstraintValidator implements ConstraintValidator<ValidAmount, Quantifiable> {

	@Override
	public void initialize(ValidAmount constraintAnnotation) {
		
	}

	@Override
	public boolean isValid(Quantifiable value, ConstraintValidatorContext context) {
		return AmountValidator.validate(value);
	} 

}
