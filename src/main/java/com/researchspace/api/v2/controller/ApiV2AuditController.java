package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.api.v2.model.ApiV2AuditPage;
import com.researchspace.api.v2.model.ApiV2AuditQuery;
import com.researchspace.api.v2.model.ApiV2CountResult;
import com.researchspace.api.v2.resource.ApiV2AuditLog;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
import java.util.Date;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  @GetMapping("/{resource}/{id}/audit")
  public ApiV2AuditPage<ApiV2AuditEvent> list(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false) ApiV2Caller caller,
      @Valid @ModelAttribute ApiV2AuditQuery query,
      BindingResult errors)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    return auditLog.search(requireResource(resource), id, query, subject(caller));
  }

  @GetMapping("/{resource}/{id}/audit/count")
  public ApiV2CountResult count(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false) ApiV2Caller caller,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Date dateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Date dateTo,
      @RequestParam(required = false) Set<AuditAction> actions) {
    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setDateFrom(dateFrom);
    query.setDateTo(dateTo);
    query.setActions(actions == null ? Set.of() : actions);
    query.setPage(1);
    query.setLimit(1);
    return new ApiV2CountResult(
        auditLog.search(requireResource(resource), id, query, subject(caller)).totalDocs());
  }

  private ApiV2ResourceRegistration<?, ?> requireResource(String name) {
    return resources.find(name).orElseThrow(NotFoundException::new);
  }

  private static void throwBindExceptionIfErrors(BindingResult errors) throws BindException {
    if (errors != null && errors.hasErrors()) {
      throw new BindException(errors);
    }
  }

  private static User subject(ApiV2Caller caller) {
    return caller == null ? null : caller.subject();
  }
}
