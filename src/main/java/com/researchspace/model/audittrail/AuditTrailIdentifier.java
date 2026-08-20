package com.researchspace.model.audittrail;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * Use this annotation to annotate a JavaBean getXXX method that will
 *  retrieve a globalIdentifier primitive - a string or number
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditTrailIdentifier {

}
