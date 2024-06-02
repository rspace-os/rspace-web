package com.researchspace.api.v1.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;

@NotNull
@NotBlank
@Target({
  ElementType.PARAMETER,
  ElementType.FIELD,
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {ValidRoRID.class})
@Documented
public @interface ValidRoR {
  String message() default "Not a valid RoR";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
