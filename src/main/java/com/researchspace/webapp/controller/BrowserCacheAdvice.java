package com.researchspace.webapp.controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a controller class to specify the advice that should be returned to the browser re
 * caching. <br>
 * This will apply to all URL paths handled by the controller. <br>
 * The annotation is interpreted by the {@link BrowserCacheAdviceInterceptor} Spring Interceptor.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BrowserCacheAdvice {
  /**
   * Constant specifying that the response should not be cached by the browser. This is important
   * for any pages displaying sensitive information.
   */
  long NEVER = 0L;

  /** A default length of time, in seconds to cache potentially sensitive data */
  long DEFAULT = 1 * 60L;

  /** A default length of time, in seconds to cache potentially sensitive data */
  long DAY = 60L * 60L * 24;

  /**
   * Constant specifying that the response can be cached by the browser forever. This is useful for
   * any responses displaying static information.
   */
  long FOREVER = DAY * 365;

  /**
   * The length of time, in seconds, to advise the browser to cache the page for; defaults to NEVER
   *
   * @return
   */
  long cacheTime() default NEVER;
}
