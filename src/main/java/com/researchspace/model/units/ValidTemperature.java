package com.researchspace.model.units;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ ElementType.FIELD, ElementType.METHOD  })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TemperatureConstraintValidator.class)
@Documented
public @interface ValidTemperature {
	
	String message() default "Invalid temperature - must be a temperature measurement greater than absolute zero";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };

}
