package com.researchspace.model.collection;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Identifies a record that defines one exposed resource. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ApiV2ResourceDefinition {

  String name();

  /** Explicit singular for operation names when removing a trailing s is insufficient. */
  String singular() default "";

  Class<?> entity();

  String id();

  /** Whether the framework adds its standard read-only audit fields and user relationships. */
  boolean auditFields() default true;
}
