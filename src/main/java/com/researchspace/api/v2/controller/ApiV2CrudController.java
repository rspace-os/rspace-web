package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.model.ApiV2BulkResult;
import com.researchspace.api.v2.model.ApiV2CollectionQuery;
import com.researchspace.api.v2.model.ApiV2CountResult;
import com.researchspace.api.v2.model.ApiV2FieldsetQuery;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.User;
import com.researchspace.model.collection.ResourceRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Supplies the standard Payload-shaped CRUD routes for every registered REST v2 collection. */
@RestController
@RequestMapping("/api/v2")
public class ApiV2CrudController {

  private final ApiV2ResourceCatalog resources;

  public ApiV2CrudController(ApiV2ResourceCatalog resources) {
    this.resources = resources;
  }

  @ModelAttribute
  void validateRawQuery(HttpServletRequest request) {
    ApiV2ResourceRequestParser.validateRawWhere(request.getQueryString());
    ApiV2ResourceRequestParser.validateFieldsetParameters(request.getParameterMap());
  }

  @InitBinder("fieldsets")
  void bindFieldsets(WebDataBinder binder) {
    binder.setAllowedFields("fields[*]", "exclude[*]");
  }

  @GetMapping("/{resource}")
  public ApiV2ListResult<Map<String, Object>> list(
      @PathVariable String resource,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false) ApiV2Caller caller,
      @Valid @ModelAttribute ApiV2CollectionQuery query,
      BindingResult queryErrors,
      @ModelAttribute("fieldsets") ApiV2FieldsetQuery fieldsets,
      BindingResult fieldsetErrors)
      throws BindException {
    throwBindExceptionIfErrors(queryErrors);
    throwBindExceptionIfErrors(fieldsetErrors);
    ApiV2ResourceRegistration<?, ?> registration =
        requireResource(resource, ResourceOperation.LIST);
    ResourceRequest request =
        ApiV2ResourceRequestParser.parse(
            query, fieldsets, registration.description(), registration.registry());
    return registration.list(request, subject(caller));
  }

  @GetMapping("/{resource}/count")
  public ApiV2CountResult count(
      @PathVariable String resource,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false) ApiV2Caller caller,
      @RequestParam(required = false) String where) {
    ApiV2ResourceRegistration<?, ?> registration =
        requireResource(resource, ResourceOperation.COUNT);
    return registration.count(
        ApiV2ResourceRequestParser.filtered(
            where, registration.description(), registration.registry()),
        subject(caller));
  }

  @GetMapping("/{resource}/{id}")
  public Map<String, Object> get(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false) ApiV2Caller caller,
      @RequestParam(defaultValue = "0") int depth,
      @ModelAttribute("fieldsets") ApiV2FieldsetQuery fieldsets,
      BindingResult fieldsetErrors)
      throws BindException {
    throwBindExceptionIfErrors(fieldsetErrors);
    ApiV2ResourceRegistration<?, ?> registration =
        requireResource(resource, ResourceOperation.READ);
    return registration.get(
        id,
        ApiV2ResourceRequestParser.item(
            depth, fieldsets, registration.description(), registration.registry()),
        subject(caller));
  }

  @PostMapping("/{resource}")
  public ResponseEntity<Map<String, Object>> create(
      @PathVariable String resource,
      @RequestBody JsonNode body,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(requireResource(resource, ResourceOperation.CREATE).create(body, caller));
  }

  @PostMapping("/{resource}/bulk")
  public ResponseEntity<ApiV2BulkResult<Map<String, Object>>> createMany(
      @PathVariable String resource,
      @RequestBody JsonNode body,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(requireResource(resource, ResourceOperation.BULK_CREATE).createMany(body, caller));
  }

  @PatchMapping("/{resource}/{id}")
  public Map<String, Object> update(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestBody JsonNode body,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    return requireResource(resource, ResourceOperation.UPDATE).update(id, body, caller);
  }

  @PatchMapping("/{resource}")
  public ApiV2BulkResult<Map<String, Object>> updateMany(
      @PathVariable String resource,
      @RequestParam(required = false) String where,
      @RequestBody JsonNode body,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    ApiV2ResourceRegistration<?, ?> registration =
        requireResource(resource, ResourceOperation.BULK_UPDATE);
    return registration.updateMany(
        ApiV2ResourceRequestParser.bulk(where, registration.description(), registration.registry()),
        body,
        caller);
  }

  @DeleteMapping("/{resource}/{id}")
  public Map<String, Object> delete(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    return requireResource(resource, ResourceOperation.DELETE).delete(id, caller);
  }

  @DeleteMapping("/{resource}")
  public ApiV2BulkResult<Map<String, Object>> deleteMany(
      @PathVariable String resource,
      @RequestParam(required = false) String where,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    ApiV2ResourceRegistration<?, ?> registration =
        requireResource(resource, ResourceOperation.BULK_DELETE);
    return registration.deleteMany(
        ApiV2ResourceRequestParser.bulk(where, registration.description(), registration.registry()),
        caller);
  }

  private ApiV2ResourceRegistration<?, ?> requireResource(String name) {
    return resources.find(name).orElseThrow(NotFoundException::new);
  }

  private ApiV2ResourceRegistration<?, ?> requireResource(
      String name, ResourceOperation operation) {
    ApiV2ResourceRegistration<?, ?> registration = requireResource(name);
    if (!registration.supports(operation)) {
      throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED);
    }
    return registration;
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
