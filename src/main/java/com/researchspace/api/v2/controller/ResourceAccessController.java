package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceException;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.service.resourceaccess.ResourceAccessDirectoryManager;
import com.researchspace.service.resourceaccess.ResourceAccessDocument;
import com.researchspace.service.resourceaccess.ResourceAccessGrant;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.service.resourceaccess.ResourceGranteeDirectoryEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/** Generic access-document routes contributed only by registered protected resources. */
@RestController
@RequestMapping("/api/v2")
public class ResourceAccessController {

  private static final Set<String> ROOT_FIELDS = Set.of("assignments");
  private static final Set<String> GRANT_FIELDS = Set.of("granteeKey", "role");

  private final ApiV2ResourceCatalog resources;
  private final ResourceAccessManager accessManager;
  private final ResourceAccessDirectoryManager directoryManager;

  public ResourceAccessController(
      ApiV2ResourceCatalog resources,
      ResourceAccessManager accessManager,
      ResourceAccessDirectoryManager directoryManager) {
    this.resources = resources;
    this.accessManager = accessManager;
    this.directoryManager = directoryManager;
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

  @PutMapping("/{resource}/{id}/access")
  public ResponseEntity<ResourceAccessDocument> replace(
      @PathVariable String resource,
      @PathVariable String id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody JsonNode body,
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

  @GetMapping("/booking-settings/access-grantees")
  public List<ResourceGranteeDirectoryEntry> bookingSettingsGrantees(
      @RequestParam String query,
      @RequestParam(defaultValue = "20") int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    return directoryManager.searchForSettings(
        validateQuery(query), validateLimit(limit), requireCaller(caller).subject());
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

  private static String validateQuery(String query) {
    String trimmed = query == null ? "" : query.trim();
    if (trimmed.length() < 2) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    return trimmed;
  }

  private static int validateLimit(int limit) {
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
    if (value == null) {
      throw ApiV2ResourceException.of(
          HttpStatus.PRECONDITION_REQUIRED, "errors.api.v2.resourceAccess.ifMatchRequired");
    }
    if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    try {
      long version = Long.parseLong(value.substring(1, value.length() - 1));
      if (version < 0) {
        throw new NumberFormatException();
      }
      return version;
    } catch (NumberFormatException ex) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
  }

  private static List<ResourceAccessGrant> parseAssignments(JsonNode body) {
    if (body == null || !body.isObject() || !fieldNames(body).equals(ROOT_FIELDS)) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    JsonNode assignments = body.get("assignments");
    if (!assignments.isArray()) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    List<ResourceAccessGrant> grants = new ArrayList<>();
    for (JsonNode assignment : assignments) {
      if (!assignment.isObject() || !fieldNames(assignment).equals(GRANT_FIELDS)) {
        throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
      }
      JsonNode granteeKey = assignment.get("granteeKey");
      JsonNode role = assignment.get("role");
      if (!granteeKey.isTextual() || !role.isTextual()) {
        throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
      }
      try {
        grants.add(new ResourceAccessGrant(granteeKey.textValue(), role.textValue()));
      } catch (IllegalArgumentException ex) {
        throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
      }
    }
    return List.copyOf(grants);
  }

  private static Set<String> fieldNames(JsonNode object) {
    Set<String> names = new HashSet<>();
    object.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static ResponseEntity<ResourceAccessDocument> response(ResourceAccessDocument document) {
    return ResponseEntity.ok().eTag(Long.toString(document.version())).body(document);
  }
}
