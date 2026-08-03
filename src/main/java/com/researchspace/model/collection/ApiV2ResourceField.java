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

  /** Per-request read visibility presets, resolved to an {@link AccessFunction}. */
  enum ReadAccess {
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
        case INHERITED -> AccessFunction.anyone();
        case AUTHENTICATED -> AccessFunction.authenticated();
        case SYSADMIN -> AccessFunction.sysadmin();
        case NEVER -> AccessFunction.never();
      };
    }
  }

  /**
   * Per-request write presets, resolved to an {@link AccessFunction}.
   *
   * <p>{@code NEVER} also removes the field from that operation's input schema. It is the explicit
   * way to describe server-managed fields whose entity property still has a persistence setter.
   */
  enum WriteAccess {
    /** Writable by anyone who may perform the operation on the collection. */
    INHERITED,
    /** Writable only by an authenticated caller. */
    AUTHENTICATED,
    /** Writable only by a system administrator. */
    SYSADMIN,
    /** Not accepted by this operation. */
    NEVER;

    AccessFunction resolve() {
      return switch (this) {
        case INHERITED -> AccessFunction.anyone();
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
  ReadAccess readAccess() default ReadAccess.INHERITED;

  /**
   * Who may supply this field when creating a resource.
   *
   * <p>A preset, for the same reason {@link #readAccess} is: annotations cannot carry code. Build
   * the {@code CollectionDescription} programmatically and use {@code
   * Field.creatableBy(AccessFunction)} when access depends on the caller or sibling input. A custom
   * create function receives the complete parsed candidate through {@link
   * AccessContext#requireInput()}.
   */
  WriteAccess createAccess() default WriteAccess.INHERITED;

  /** Who may supply this field when updating a resource. */
  WriteAccess updateAccess() default WriteAccess.INHERITED;

  boolean requiredOnCreate() default false;

  boolean nullable() default false;

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
