package com.researchspace.api.v1.controller;

import com.researchspace.service.LicenseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Makes the Inventory API read-only while the license has expired. GET, HEAD and OPTIONS requests
 * always pass, as do handler methods marked {@link AllowedWithExpiredLicense}; every other request
 * is rejected with {@link InventoryReadOnlyException}, including one with no resolved handler
 * method.
 */
public class InventoryLicenseInterceptor implements HandlerInterceptor {

  private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  @Autowired private LicenseService licenseService;

  void setLicenseService(LicenseService licenseService) {
    this.licenseService = licenseService;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (READ_METHODS.contains(request.getMethod()) || isAllowedWithExpiredLicense(handler)) {
      return true;
    }
    if (!licenseService.isLicenseActive()) {
      throw new InventoryReadOnlyException("Inventory is read-only as the license has expired");
    }
    return true;
  }

  private static boolean isAllowedWithExpiredLicense(Object handler) {
    return handler instanceof HandlerMethod handlerMethod
        && handlerMethod.hasMethodAnnotation(AllowedWithExpiredLicense.class);
  }
}
