package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.json.ApiV2JsonBody;
import com.researchspace.api.v2.json.ApiV2JsonMappers;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.service.resourceaccess.ReplaceResourceAccessRequest;
import com.researchspace.service.resourceaccess.ResourceAccessDirectoryManager;
import com.researchspace.service.resourceaccess.ResourceAccessDocument;
import com.researchspace.service.resourceaccess.ResourceAccessGrant;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.service.resourceaccess.ResourceGranteeDirectoryEntry;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/** Generic access-document routes contributed only by registered protected resources. */
@RestController
@Hidden // Concrete resource access paths are documented by ApiV2OpenApiResourceAccessDocs.
@RequestMapping("/api/v2")
public class ResourceAccessController {

  static final int MAX_ASSIGNMENTS = ResourceAccessManager.MAX_NAMED_ASSIGNMENTS;

  private final ApiV2ResourceCatalog resources;
  private final ResourceAccessManager accessManager;
  private final ResourceAccessDirectoryManager directoryManager;
  private final ObjectProvider<RequestMappingHandlerAdapter> handlerAdapter;

  public ResourceAccessController(
      ApiV2ResourceCatalog resources,
      ResourceAccessManager accessManager,
      ResourceAccessDirectoryManager directoryManager,
      ObjectProvider<RequestMappingHandlerAdapter> handlerAdapter) {
    this.resources = resources;
    this.accessManager = accessManager;
    this.directoryManager = directoryManager;
    this.handlerAdapter = handlerAdapter;
  }

  @GetMapping("/{resource}/{id}/access")
  public ResponseEntity<ResourceAccessDocument> get(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    ResourceAccessDocument document =
        requireRegistration(resource).getAccess(id, requireCaller(caller).subject(), accessManager);
    return response(document);
  }

  @PutMapping(value = "/{resource}/{id}/access", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ResourceAccessDocument> replace(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody ApiV2JsonBody body,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    ResourceAccessDocument document =
        requireRegistration(resource)
            .replaceAccess(
                id,
                parseIfMatch(ifMatch),
                parseAssignments(body),
                requireCaller(caller),
                accessManager);
    return response(document);
  }

  @DeleteMapping("/{resource}/{id}/access/me")
  public ResponseEntity<Void> removeSelf(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    requireRegistration(resource).removeSelfAccess(id, requireCaller(caller), accessManager);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{resource}/{id}/access/grantees")
  public List<ResourceGranteeDirectoryEntry> grantees(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestParam String query,
      @RequestParam(defaultValue = "20") int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    ApiV2ResourceRegistration<?, ?> registration = requireRegistration(resource);
    return search(
        registration, id, validateQuery(query), validateLimit(limit), requireCaller(caller));
  }

  private <T, ID> List<ResourceGranteeDirectoryEntry> search(
      ApiV2ResourceRegistration<T, ID> registration,
      String rawId,
      String query,
      int limit,
      ApiV2Caller caller) {
    var access = registration.resourceAccess().orElseThrow(jakarta.ws.rs.NotFoundException::new);
    return directoryManager.searchForResource(
        access.protectedResource(),
        registration.parseResourceId(rawId),
        query,
        limit,
        caller.subject());
  }

  static String validateQuery(String query) {
    String trimmed = query == null ? "" : query.trim();
    if (trimmed.length() < 2) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    return trimmed;
  }

  static int validateLimit(int limit) {
    if (limit < 1 || limit > 50) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    return limit;
  }

  private ApiV2ResourceRegistration<?, ?> requireRegistration(String resource) {
    return resources
        .find(resource)
        .filter(registration -> registration.resourceAccess().isPresent())
        .orElseThrow(jakarta.ws.rs.NotFoundException::new);
  }

  private static ApiV2Caller requireCaller(ApiV2Caller caller) {
    if (caller == null) {
      throw new ApiV2AuthenticationException();
    }
    return caller;
  }

  private static long parseIfMatch(String value) {
    return ApiV2ConditionalRequest.parseVersion(
        value, "errors.api.v2.resourceAccess.ifMatchRequired");
  }

  private List<ResourceAccessGrant> parseAssignments(ApiV2JsonBody body) {
    if (body == null || body.node() == null) {
      throw invalidRequest();
    }
    ObjectMapper base =
        handlerAdapter.getObject().getMessageConverters().stream()
            .filter(MappingJackson2HttpMessageConverter.class::isInstance)
            .map(MappingJackson2HttpMessageConverter.class::cast)
            .map(MappingJackson2HttpMessageConverter::getObjectMapper)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No Jackson HTTP message converter"));
    List<ResourceAccessGrant> grants;
    try {
      ReplaceResourceAccessRequest request =
          ApiV2JsonMappers.strict(base)
              .treeToValue(body.node(), ReplaceResourceAccessRequest.class);
      if (request == null) {
        throw invalidRequest();
      }
      grants = request.assignments();
    } catch (MismatchedInputException | ValueInstantiationException ex) {
      throw ApiV2JsonMappers.validationException(ex);
    } catch (JsonProcessingException | IllegalArgumentException ex) {
      throw invalidRequest();
    }
    long namedAssignments =
        grants.stream().filter(grant -> !"audience:all-users".equals(grant.granteeKey())).count();
    if (namedAssignments > MAX_ASSIGNMENTS) {
      throw new ApiV2BadRequestException("errors.api.v2.resourceAccess.assignmentLimit");
    }
    return List.copyOf(grants);
  }

  private static ApiV2BadRequestException invalidRequest() {
    return new ApiV2BadRequestException("errors.api.v2.invalidRequest");
  }

  private static ResponseEntity<ResourceAccessDocument> response(ResourceAccessDocument document) {
    return ResponseEntity.ok().eTag(Long.toString(document.version())).body(document);
  }
}
