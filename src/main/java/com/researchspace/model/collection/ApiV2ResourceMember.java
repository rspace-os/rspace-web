package com.researchspace.model.collection;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a member whose read or write behavior needs code beyond the declarative field and
 * relationship annotations.
 *
 * <p>The named method must be a static no-argument factory on the resource record. Startup checks
 * that its member name matches this component and that its generic entity type accepts the resource
 * entity type.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface ApiV2ResourceMember {

  String factory();
}
