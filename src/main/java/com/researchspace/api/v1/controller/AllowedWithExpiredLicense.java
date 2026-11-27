package com.researchspace.api.v1.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an Inventory API handler method that changes no Inventory data, so {@link
 * InventoryLicenseInterceptor} lets it run while the license has expired.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedWithExpiredLicense {}
