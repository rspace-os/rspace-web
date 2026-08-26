package com.researchspace.model.units;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TemperatureConstraintValidator.class)
@Documented
public @interface ValidTemperature {

  String message() default "{validation.quantity.temperatureAboveAbsoluteZero}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
