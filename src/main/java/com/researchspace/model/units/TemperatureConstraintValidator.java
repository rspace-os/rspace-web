package com.researchspace.model.units;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Temperature constraint validator for Quantifiable objects annotated with @ValidTemperature 
 */
public class TemperatureConstraintValidator implements ConstraintValidator<ValidTemperature, Quantifiable> {

	@Override
	public void initialize(ValidTemperature constraintAnnotation) {
		
	}

	@Override
	public boolean isValid(Quantifiable value, ConstraintValidatorContext context) {
		return TemperatureValidator.validate(value);
	} 

}
