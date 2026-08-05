package com.researchspace.api.v1.model;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {ValidSharePostValidator.class})
@Documented
public @interface ValidSharePost {
  String message() default "{sharing.errors.recipientsRequired}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
