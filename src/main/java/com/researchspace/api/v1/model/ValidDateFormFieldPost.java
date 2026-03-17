package com.researchspace.api.v1.model;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Validates Date relations */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {ValidDateFormFieldPostValidator.class})
@Documented
public @interface ValidDateFormFieldPost {
  String message() default
      "Min date must be before max date, and default date must be between them";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
