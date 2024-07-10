package com.researchspace.webapp.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a service-level method with this annotation if automated logging with {@link
 * ServiceLoggerAspct} should be limited for annotated method.
 *
 * <p>The class follows convention of {@link IgnoreInLoggingInterceptor}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreInServiceLoggerAspct {

  /**
   * If set to true, the method invocation will be recorded, but not the request parameters. This is
   * useful for logging events that contain sensitive information, but where we still want to record
   * the fact that the method was called.
   *
   * @return <code>true</code> if the method call should be logged, without request params, or
   *     <code>false</code>( default) if the method should not be logged at all.
   */
  boolean ignoreAllRequestParams() default false;
}
