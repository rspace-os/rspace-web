package com.researchspace.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RSDEV-1525: marks a {@code @Value} deployment property field that the browser may read. The
 * deployment properties endpoints return only fields marked with this or {@link
 * ClientReadableSecret}, so a new field stays server-side unless it is marked. The property name is
 * the field's {@code @Value} placeholder.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ClientReadable {}
