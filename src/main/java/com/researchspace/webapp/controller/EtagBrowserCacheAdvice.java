package com.researchspace.webapp.controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <br>
 * The annotation is interpreted by the {@link BrowserCacheAdviceInterceptor} Spring Interceptor.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EtagBrowserCacheAdvice {
  /**
   * Indicates that the URL is used as the value of the etag, this is useful when URLs are unique
   * and contain modification information.
   */
  String URL = "url";

  /**
   * The name of the model attribute that holds the object we want to etag - optional
   *
   * @return
   */
  String modelAttribute() default "";

  String cacheStrategy() default "";
}
