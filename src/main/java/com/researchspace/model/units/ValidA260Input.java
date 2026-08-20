package com.researchspace.model.units;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { ValidA260InputValidator.class })
@Documented
public @interface ValidA260Input {

	String message() default "{valid.od260.classConstraint1}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
