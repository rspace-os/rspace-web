package com.researchspace.api.v1.model;

import static com.researchspace.model.dtos.AbstractFormFieldDTO.MAX_NAME_LENGTH;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.hibernate.validator.constraints.NotBlank;

/** Composed validation for a non-empty String of &gt;1 and &lt;= 50 characters */
@NotNull
@NotBlank
@Size(min = 1, max = MAX_NAME_LENGTH, message = "Name must be between 1 and 50 characters")
@Target({
  ElementType.METHOD,
  ElementType.FIELD,
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Documented
public @interface ValidName {
  String message() default "Invalid name";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
