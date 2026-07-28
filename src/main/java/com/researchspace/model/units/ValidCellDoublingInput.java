package com.researchspace.model.units;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Performs validation on whole class.
 */
@Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { ValidCellDoublingInputValidator.class })
@Documented
public @interface ValidCellDoublingInput {

    String message() default "{valid.doublingTime.classConstraint1}";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };
}
