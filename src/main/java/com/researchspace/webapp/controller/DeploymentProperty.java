package com.researchspace.webapp.controller;

import com.researchspace.model.DeploymentPropertyType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate classes or methods that should only be accessed if a particular deployment property is
 * enabled.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeploymentProperty {

  DeploymentPropertyType[] value();
}
