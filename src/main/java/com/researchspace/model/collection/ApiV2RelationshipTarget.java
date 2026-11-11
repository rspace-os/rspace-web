package com.researchspace.model.collection;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares one resource and entity type that a relationship can reference. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface ApiV2RelationshipTarget {

  String resource();

  Class<?> entity();

  /** The stored kind for a polymorphic relationship. Empty means a monomorphic target. */
  String kind() default "";

  /** The prefix accepted for this target's global ID. Empty means global IDs are disabled. */
  String globalIdPrefix() default "";
}
