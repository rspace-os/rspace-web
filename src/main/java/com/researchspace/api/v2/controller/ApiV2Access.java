package com.researchspace.api.v2.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares how a REST API v2 endpoint authenticates a request. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiV2Access {

  Mode value();

  enum Mode {
    PUBLIC,
    RESOURCE_POLICY,
    AUTHENTICATED
  }
}
