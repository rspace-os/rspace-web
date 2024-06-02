package com.researchspace.api.v1.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {ValidSharePostValidator.class})
@Documented
public @interface ValidSharePost {
  String message() default
      "At least one user ID or group ID must be provided in 'users' or 'groups' list";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
