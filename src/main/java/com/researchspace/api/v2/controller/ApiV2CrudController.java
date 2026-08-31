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
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
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
            query,
            fieldsets,
            registration.description(),
            registration.registry(),
            registration.runtimeFieldContext(subject(caller), this::runtimeFieldsOf));
    return registration.list(request, subject(caller));
  }

  /**
   * The runtime fields this caller may name on this collection.
   *
   * <p>A separate route rather than part of the OpenAPI document because the answer differs per
   * caller, and the document is generated once and cached globally. Marked private so no shared
   * cache can serve one user's field names to another.
   */
  @GetMapping("/{resource}/fields/{namespace}")
  public ResponseEntity<Map<String, Object>> runtimeFields(
      @PathVariable String resource,
      @PathVariable String namespace,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false) ApiV2Caller caller,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String ids,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(required = false) Integer limit) {
    ApiV2ResourceRegistration<?, ?> registration =
        requireResource(resource, ResourceOperation.LIST);
    requireNamespace(registration, namespace);
    RuntimeFieldCatalogQuery query = catalogQuery(search, ids, page, limit);
    RuntimeFieldCatalogPage catalog =
        registration.runtimeFieldCatalog(subject(caller), namespace, query);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("fields", catalog.fields().stream().map(ApiV2CrudController::catalogEntry).toList());
    body.put("hasMore", catalog.hasMore());
    if (catalog.total() != null) {
      body.put("totalFields", catalog.total());
    }
    body.put("page", query.page());
    body.put("limit", query.limit());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
  }

  private static void requireNamespace(
      ApiV2ResourceRegistration<?, ?> registration, String namespace) {
    if (registration.runtimeFields(namespace).isEmpty()) {
      throw new NotFoundException();
    }
  }

  private static RuntimeFieldCatalogQuery catalogQuery(
      String search, String ids, int page, Integer limit) {
    Set<String> requested = new LinkedHashSet<>();
    if (ids != null && !ids.isBlank()) {
      for (String id : ids.split(",", -1)) {
        String trimmed = id.trim();
        if (!trimmed.isEmpty()) {
          requested.add(trimmed);
        }
      }
    }
    if (requested.size() > RuntimeFieldCatalogQuery.MAX_IDS) {
      throw new ApiV2BadRequestException(
          "errors.api.v2.runtimeFields.ids.limit", RuntimeFieldCatalogQuery.MAX_IDS);
    }
    int size = limit == null ? RuntimeFieldCatalogQuery.DEFAULT_LIMIT : limit;
    if (size < 1 || size > RuntimeFieldCatalogQuery.MAX_LIMIT) {
      throw new ApiV2BadRequestException(
          "errors.api.v2.runtimeFields.query.invalid", RuntimeFieldCatalogQuery.MAX_LIMIT);
    }
    try {
      return new RuntimeFieldCatalogQuery(search, requested, page, size);
    } catch (IllegalArgumentException ex) {
      throw new ApiV2BadRequestException("errors.api.v2.runtimeFields.page.invalid");
    }
  }

  private static Map<String, Object> catalogEntry(RuntimeFieldDefinition definition) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("id", definition.id());
    entry.put("selector", definition.selector());
    entry.put("label", definition.label());
    entry.put("type", definition.type().name().toLowerCase(Locale.ROOT));
    entry.put("jsonType", definition.type().jsonType());
    entry.put(
        "operators",
        definition.operators().stream().map(ApiV2CrudController::token).sorted().toList());
    entry.put("supportsWildcards", definition.supportsWildcards());
    entry.put("columnSelectable", definition.columnSelectable());
    entry.put("sortable", false);
    entry.put("source", Map.of("id", definition.sourceId(), "label", definition.sourceLabel()));
    entry.put("options", definition.options());
    return entry;
  }

  private static String token(Operator operator) {
    return switch (operator) {
      case EQUAL -> "==";
      case NOT_EQUAL -> "!=";
      case GREATER_THAN -> "=gt=";
      case GREATER_THAN_OR_EQUAL -> "=ge=";
      case LESS_THAN -> "=lt=";
      case LESS_THAN_OR_EQUAL -> "=le=";
      case IN -> "=in=";
      case NOT_IN -> "=out=";
      case CONTAINS -> "=contains=";
      case LIKE -> "=like=";
      case EXISTS -> "=exists=";
    };
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
            where,
            registration.description(),
            registration.registry(),
            registration.runtimeFieldContext(subject(caller), this::runtimeFieldsOf)),
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
            depth,
            fieldsets,
            registration.description(),
            registration.registry(),
            registration.runtimeFieldContext(subject(caller), this::runtimeFieldsOf)),
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

  private List<RuntimeCollectionFields<?>> runtimeFieldsOf(String resourceName) {
    return resources
        .find(resourceName)
        .map(ApiV2ResourceRegistration::providers)
        .orElseGet(List::of);
  }

  private ApiV2ResourceRegistration<?, ?> requireResource(String name) {
    return resources.find(name).orElseThrow(NotFoundException::new);
  }

  /**
   * Selects a registration, refusing an operation the resource does not expose.
   *
   * <p>The refusal carries {@code Allow}, as RFC 9110 requires on every 405 and as Spring already
   * does for an unmapped method. A client that discovers routes by probing otherwise learns only
   * that this one is wrong, never which ones are right.
   */
  private ApiV2ResourceRegistration<?, ?> requireResource(
      String name, ResourceOperation operation) {
    ApiV2ResourceRegistration<?, ?> registration = requireResource(name);
    if (!registration.supports(operation)) {
      throw new ApiV2MethodNotAllowedException(allowedMethods(registration));
    }
    return registration;
  }

  /** The distinct HTTP methods the resource's exposed operations are reached by. */
  private static Set<HttpMethod> allowedMethods(ApiV2ResourceRegistration<?, ?> registration) {
    return registration.exposedOperations().stream()
        .map(ResourceOperation::httpMethod)
        .map(HttpMethod::valueOf)
        .collect(Collectors.toCollection(LinkedHashSet::new));
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
