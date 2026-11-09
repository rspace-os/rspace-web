package com.researchspace.model.collection;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines one relationship member of an annotated API resource record.
 *
 * <p>The component type is {@link ResourceReference} for documentation; the resource record is
 * never instantiated. The factory reads the related entity property because annotations cannot
 * carry a reader lambda. One target with no kind declares a monomorphic relationship. Non-empty
 * kinds on every target declare a polymorphic relationship.
 *
 * <p>The default query paths are {@code <property>.id} and {@code <property>.kind}. A path starting
 * with {@code <property>.} names the accessor on the related value; other paths use {@code id} or
 * {@code kind}. JavaBean getters and public no-argument methods both work.
 *
 * <p>The defaults are nullable and read-only for both relationship forms. Set {@link #writeOn()} to
 * accept input. {@link #requiredOnCreate()} implies non-null even when {@link #nullable()} retains
 * its default value.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface ApiV2ResourceRelationship {

  /** The resources and entity types this relationship may reference. */
  ApiV2RelationshipTarget[] relationTo();

  /** Entity property that contains the related value. Defaults to the record component name. */
  String property() default "";

  /** Persistent ID path used by queries. Defaults to {@code <property>.id}. */
  String idProperty() default "";

  /** Persistent kind path used by polymorphic queries. Defaults to {@code <property>.kind}. */
  String kindProperty() default "";

  /** Requires a value on create and implies non-null, regardless of {@link #nullable()}. */
  boolean requiredOnCreate() default false;

  boolean nullable() default true;

  /** Operations that accept relationship input. Empty means read-only. */
  WriteOperation[] writeOn() default {};

  /** Operations that accept global IDs; each target must declare a prefix. */
  WriteOperation[] acceptGlobalIdOn() default {};

  boolean allowSelfReference() default false;

  ApiV2ResourceField.AccessPreset readAccess() default ApiV2ResourceField.AccessPreset.INHERITED;

  ApiV2ResourceField.AccessPreset writeAccess() default ApiV2ResourceField.AccessPreset.INHERITED;

  String title() default "";

  String description() default "";

  String example() default "";

  boolean deprecated() default false;
}
