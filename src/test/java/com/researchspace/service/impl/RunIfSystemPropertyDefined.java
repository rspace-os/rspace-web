package com.researchspace.service.impl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables a test method to be conditionally run, depending on whether a system property is defined.
 * Needs to be used with a {@link ConditionalTestRunner} JUnit runner.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RunIfSystemPropertyDefined {
  /** The name of a system property that must be set for the test to run. */
  String value();
}
