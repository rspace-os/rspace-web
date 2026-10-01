package com.researchspace.model.collection;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Defines one field of an annotated resource record. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface ApiV2ResourceField {

  /** Per-request access presets, resolved to an {@link AccessFunction}. */
  enum AccessPreset {
    /** Visible to anyone who may read the collection. */
    INHERITED,
    /** Visible only to an authenticated caller. */
    AUTHENTICATED,
    /** Visible only to a system administrator. */
    SYSADMIN,
    /** Never rendered, whoever asks: a write-only secret such as a password. */
    NEVER;

    AccessFunction resolve() {
      return switch (this) {
        case INHERITED -> AccessFunction.inherited();
        case AUTHENTICATED -> AccessFunction.authenticated();
        case SYSADMIN -> AccessFunction.sysadmin();
        case NEVER -> AccessFunction.never();
      };
    }
  }

  /**
   * Who may read this field.
   *
   * <p>A preset rather than a lambda because annotations cannot carry code. Denial omits the value
   * from output and makes the field invalid as a {@code where}/{@code sort} target; it never
   * changes which rows are returned. Row narrowing belongs on the collection's {@link
   * AccessPolicy}.
   */
  AccessPreset readAccess() default AccessPreset.INHERITED;

  /**
   * Who may supply this field when creating a resource.
   *
   * <p>A preset, for the same reason {@link #readAccess} is: annotations cannot carry code. Build
   * the {@code CollectionDescription} programmatically and use {@code
   * Field.creatableBy(AccessFunction)} when access depends on the caller or sibling input. A custom
   * create function receives the complete parsed candidate through {@link
   * AccessContext#requireInput()}.
   */
  AccessPreset createAccess() default AccessPreset.INHERITED;

  /** Who may supply this field when updating a resource. */
  AccessPreset updateAccess() default AccessPreset.INHERITED;

  boolean requiredOnCreate() default false;

  boolean nullable() default false;

  /** Whether clients can use this field in a {@code where} expression. */
  boolean filterable() default true;

  /** Whether clients can use this field in a {@code sort} expression. */
  boolean sortable() default true;

  int maxLength() default -1;

  String property() default "";

  String title() default "";

  String description() default "";

  String example() default "";

  /** OpenAPI string format, such as {@code email}, {@code uri}, or {@code date-time}. */
  String format() default "";

  /** A wire-format default value. It is converted to the field's scalar type in the schema. */
  String defaultValue() default "";

  int minLength() default -1;

  /** Additional wire-format examples beyond {@link #example()}. */
  String[] additionalExamples() default {};

  String pattern() default "";

  String minimum() default "";

  String maximum() default "";

  String[] enumValues() default {};

  boolean deprecated() default false;
}
