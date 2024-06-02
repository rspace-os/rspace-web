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
@Constraint(validatedBy = {ValidNumberFormFieldPostValidator.class})
@Documented
public @interface ValidNumberFormFieldPost {
  String message() default "Min must be < max";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
