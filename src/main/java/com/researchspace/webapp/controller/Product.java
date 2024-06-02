package com.researchspace.webapp.controller;

import com.researchspace.model.ProductType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate classes or methods that should only be accessed in a particular product variant. <br>
 * A class-based annotation will apply to all methods; annotations on methods will override the
 * class annotation if there is one.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Product {
  /**
   * One or more {@link ProductType} to which this method or class are restricted.
   *
   * @return
   */
  ProductType[] value();
}
