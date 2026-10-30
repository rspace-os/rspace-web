package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.api.v2.model.ApiV2AuditPage;
import com.researchspace.api.v2.model.ApiV2AuditQuery;
import com.researchspace.api.v2.resource.ApiV2AuditLog;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Supplies the default audit log routes for registered REST API v2 resources. */
@RestController
@RequestMapping("/api/v2")
public final class ApiV2AuditController {

  private final ApiV2ResourceCatalog resources;
  private final ApiV2AuditLog auditLog;

  public ApiV2AuditController(ApiV2ResourceCatalog resources, ApiV2AuditLog auditLog) {
    this.resources = resources;
    this.auditLog = auditLog;
  }

  @Operation(summary = "List audit events for a readable resource")
  @GetMapping("/{resource}/{id}/audit")
  public ApiV2AuditPage<ApiV2AuditEvent> list(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false) ApiV2Caller caller,
      @Valid @ModelAttribute ApiV2AuditQuery query,
      BindingResult errors)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    return auditLog.search(
        ApiV2ControllerSupport.requireResource(resources, resource),
        id,
        query,
        ApiV2ControllerSupport.subject(caller));
  }

  private static void throwBindExceptionIfErrors(BindingResult errors) throws BindException {
    if (errors != null && errors.hasErrors()) {
      throw new BindException(errors);
    }
  }
}
