package com.researchspace.api.v1.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

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
