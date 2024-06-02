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
@Constraint(validatedBy = {ValidRadioFormFieldPostValidator.class})
@Documented
public @interface ValidRadioFormFieldPost {
  String message() default "Default option must be one of the possible options";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
