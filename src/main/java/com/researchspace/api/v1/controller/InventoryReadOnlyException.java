package com.researchspace.api.v1.controller;

import com.researchspace.licensews.LicenseExpiredException;

/**
 * Thrown by {@link InventoryLicenseInterceptor} for an Inventory write while the license has
 * expired, so that only Inventory requests get the read-only message.
 */
public class InventoryReadOnlyException extends LicenseExpiredException {

  public InventoryReadOnlyException(String message) {
    super(message);
  }
}
