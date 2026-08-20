package com.researchspace.model.units;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that the annotted object represents non-negative amount in mass, volume or dimensionless units
 */
@Target({ ElementType.FIELD, ElementType.METHOD  })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AmountConstraintValidator.class)
@Documented
public @interface ValidAmount {
	
	String message() default "Invalid amount - must be a positive volume, mass or quantity";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };

}
