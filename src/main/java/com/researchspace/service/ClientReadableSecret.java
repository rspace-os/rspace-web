package com.researchspace.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RSDEV-1525: marks an endpoint or value that deliberately sends a secret to the browser, which is
 * otherwise never done. Every use must say why the browser needs the secret, usually because
 * client-side code calls the provider directly, so each exception is explicit and searchable. On a
 * deployment property field it also exposes the field, as {@link ClientReadable} does.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface ClientReadableSecret {

  /** Why the browser needs this secret. */
  String value();
}
