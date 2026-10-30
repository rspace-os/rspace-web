package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator.AUTHENTICATED_SECURITY;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;

import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.ResourceAccessSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessDocumentation.AuthenticationRequirement;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessPolicySchema;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.FieldSchema;
import com.researchspace.model.collection.ResourceSchema;
import com.researchspace.service.resourceaccess.ReplaceResourceAccessRequest;
import com.researchspace.service.resourceaccess.ResourceAccessDocument;
import com.researchspace.service.resourceaccess.ResourceAccessGrant;
import com.researchspace.service.resourceaccess.ResourceGranteeDirectoryEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ApiV2OpenApiResourceAccessDocs {

  private static final String JSON = "application/json";

  private enum ResourceAccessOperation {
    READ("get", "Access", "Get direct access assignments", "ACCESS_READ"),
    REPLACE("replace", "Access", "Replace direct access assignments", "ACCESS_REPLACE"),
    LEAVE("leave", "", "Remove the caller's direct assignment", "ACCESS_LEAVE"),
    SEARCH("search", "AccessGrantees", "Search eligible access grantees", "ACCESS_GRANTEE_SEARCH");

    private final String operationPrefix;
    private final String operationSuffix;
    private final String summary;
    private final String extensionValue;

    ResourceAccessOperation(
        String operationPrefix, String operationSuffix, String summary, String extensionValue) {
      this.operationPrefix = operationPrefix;
      this.operationSuffix = operationSuffix;
      this.summary = summary;
      this.extensionValue = extensionValue;
    }

    String operationId(String component) {
      return operationPrefix + component + operationSuffix;
    }

    String summary() {
      return summary;
    }

    String extensionValue() {
      return extensionValue;
    }

    boolean managesAssignments() {
      return this == REPLACE || this == SEARCH;
    }

    boolean changesAssignments() {
      return this == REPLACE || this == LEAVE;
    }
  }

  static void addResourceAccessSchemas(Map<String, Object> schemas) {
    ApiV2OpenApiSchemas.schemaFor(ResourceAccessDocument.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ResourceAccessGrant.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ResourceGranteeDirectoryEntry.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ReplaceResourceAccessRequest.class, schemas);
  }

  static void applyRegisteredAccessFieldSchemas(
      Map<String, Object> schemas,
      ApiV2ResourceRegistration<?, ?> resource,
      String component,
      Set<String> declaredComponents) {
    ResourceAccessSpec<?, ?> access = resource.resourceAccess().orElse(null);
    if (access == null || component == null) {
      return;
    }
    Map<String, Object> read = mutableObjectMap(schemas.get(component + "Read"));
    Map<String, Object> properties = mutableObjectMap(read.get("properties"));
    Object roleSources = properties.get("roleSources");
    if (roleSources instanceof Map<?, ?> array && array.get("items") instanceof Map<?, ?> items) {
      if (!declaredComponents.add("ResourceRoleSource")
          && !Objects.equals(schemas.get("ResourceRoleSource"), items)) {
        throw new IllegalArgumentException("Inconsistent declared ResourceRoleSource children");
      }
      schemas.put("ResourceRoleSource", items);
      mutableObjectMap(roleSources).put("items", ref("ResourceRoleSource"));
    }
    access
        .capabilitiesType()
        .ifPresent(
            type ->
                replaceFieldShape(
                    properties, "capabilities", ApiV2OpenApiSchemas.schemaFor(type, schemas)));
    access
        .ownerHealthType()
        .ifPresent(
            type ->
                replaceFieldShape(
                    properties, "ownerHealth", ApiV2OpenApiSchemas.schemaFor(type, schemas)));
  }

  private static void replaceFieldShape(
      Map<String, Object> properties, String field, Map<String, Object> shape) {
    Object current = properties.get(field);
    if (!(current instanceof Map<?, ?> currentMap)) {
      return;
    }
    Map<String, Object> replacement = new LinkedHashMap<>(shape);
    currentMap.forEach(
        (key, value) -> {
          if (key instanceof String name
              && !Set.of("type", "format", "items", "properties", "additionalProperties")
                  .contains(name)) {
            replacement.put(name, value);
          }
        });
    properties.put(field, replacement);
  }

  void addResourceAccessPaths(
      Map<String, Object> paths, ApiV2ResourceRegistration<?, ?> resource, String component) {
    if (resource.resourceAccess().isEmpty()) {
      return;
    }
    String root = "/api/v2/" + resource.resourceName() + "/{id}/access";
    paths.put(
        root,
        ordered(
            "get",
            resourceAccessOperation(resource, component, ResourceAccessOperation.READ),
            "put",
            resourceAccessOperation(resource, component, ResourceAccessOperation.REPLACE)));
    paths.put(
        root + "/me",
        ordered(
            "delete", resourceAccessOperation(resource, component, ResourceAccessOperation.LEAVE)));
    paths.put(
        root + "/grantees",
        ordered(
            "get", resourceAccessOperation(resource, component, ResourceAccessOperation.SEARCH)));
  }

  private Map<String, Object> resourceAccessOperation(
      ApiV2ResourceRegistration<?, ?> resource,
      String component,
      ResourceAccessOperation operationKind) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("operationId", operationKind.operationId(component));
    operation.put("summary", operationKind.summary());
    operation.put("tags", List.of(resource.resourceName()));
    operation.put("security", AUTHENTICATED_SECURITY);
    operation.put(
        "x-rspace-access",
        operationAccessExtension(
            operationKind.managesAssignments()
                ? "Authenticated callers with the registered assignment-management capability."
                : "Authenticated callers authorized by the registered protected resource.",
            List.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN)));
    List<Map<String, Object>> parameters = new ArrayList<>();
    FieldSchema id =
        field(resource.description().schema(), resource.description().schema().idField());
    parameters.add(
        parameter("id", "path", true, scalarSchema(id.type(), false), "Resource identifier."));
    if (operationKind == ResourceAccessOperation.REPLACE) {
      parameters.add(
          parameter(
              "If-Match",
              "header",
              true,
              ordered("type", "string", "pattern", "^\\\"[0-9]+\\\"$"),
              "Strong ETag from the latest access document."));
      operation.put(
          "requestBody",
          ordered(
              "required",
              true,
              "content",
              Map.of(JSON, ordered("schema", ref("ResourceAccessReplacement")))));
    }
    if (operationKind == ResourceAccessOperation.SEARCH) {
      parameters.add(
          parameter(
              "q",
              "query",
              true,
              ordered("type", "string", "minLength", 2),
              "Case-insensitive user or group search text."));
      parameters.add(
          parameter(
              "limit",
              "query",
              false,
              ordered("type", "integer", "minimum", 1, "maximum", 50, "default", 20),
              "Maximum grantees to return."));
    }
    operation.put("parameters", parameters);
    operation.put("responses", resourceAccessResponses(operationKind));
    operation.put("x-rspace-operation", operationKind.extensionValue());
    return operation;
  }

  private static Map<String, Object> resourceAccessResponses(
      ResourceAccessOperation operationKind) {
    Map<String, Object> responses = new LinkedHashMap<>();
    if (operationKind == ResourceAccessOperation.LEAVE) {
      responses.put("204", ordered("description", "The direct assignment is absent."));
    } else {
      Map<String, Object> schema =
          operationKind == ResourceAccessOperation.SEARCH
              ? ordered("type", "array", "items", ref("ResourceGranteeDirectoryEntry"))
              : ref("ResourceAccessDocument");
      Map<String, Object> success =
          ordered(
              "description",
              operationKind == ResourceAccessOperation.SEARCH
                  ? "Eligible users and groups."
                  : "Current direct assignments and caller capabilities.",
              "content",
              Map.of(JSON, ordered("schema", schema)));
      if (operationKind != ResourceAccessOperation.SEARCH) {
        success.put("headers", ordered("ETag", headerRef("ETag")));
      }
      responses.put("200", success);
    }
    responses.put("400", responseRef("BadRequest"));
    responses.put("401", responseRef("Unauthenticated"));
    responses.put("403", responseRef("Forbidden"));
    responses.put("404", responseRef("NotFound"));
    responses.put("406", responseRef("NotAcceptable"));
    if (operationKind.changesAssignments()) {
      responses.put(
          "409",
          problemResponse(
              "The assignment change would violate a resource invariant.",
              409,
              "errors.api.v2.resourceAccess.ownerRequired"));
    }
    if (operationKind == ResourceAccessOperation.REPLACE) {
      responses.put(
          "412",
          problemResponse(
              "The access document changed since it was read.",
              412,
              "errors.api.v2.resourceAccess.stale"));
      responses.put("415", responseRef("UnsupportedMediaType"));
      responses.put(
          "428",
          problemResponse(
              "A strong If-Match header is required.",
              428,
              "errors.api.v2.resourceAccess.ifMatchRequired"));
    }
    responses.put("429", responseRef("TooManyRequests"));
    responses.put("500", responseRef("UnexpectedError"));
    return responses;
  }

  static AccessDocumentation access(AccessPolicySchema access, ResourceOperation operation) {
    return switch (operation) {
      case LIST, COUNT, READ -> access.readAccess();
      case CREATE, BULK_CREATE -> access.createAccess();
      case UPDATE, BULK_UPDATE -> access.updateAccess();
      case DELETE, BULK_DELETE -> access.deleteAccess();
    };
  }

  static void applyAccess(Map<String, Object> operation, AccessDocumentation access) {
    operation.put("security", security(access.authenticationRequirement()));
    operation.put(
        "x-rspace-access",
        operationAccessExtension(access.description(), access.denialReasonCodes()));
  }

  private static List<Map<String, List<String>>> security(
      AuthenticationRequirement authenticationRequirement) {
    return authenticationRequirement == AuthenticationRequirement.PUBLIC
        ? List.of()
        : AUTHENTICATED_SECURITY;
  }

  static Map<String, Object> operationAccessExtension(
      String description, Collection<String> denialReasonCodes) {
    return ordered("description", description, "denialReasonCodes", denialReasonCodes);
  }

  private static Map<String, Object> scalarSchema(
      CollectionFieldType.Schema type, boolean nullable) {
    return ApiV2OpenApiResourceDocumentBuilder.scalarSchema(type, nullable);
  }

  private static FieldSchema field(ResourceSchema resource, String name) {
    return ApiV2OpenApiResourceDocumentBuilder.field(resource, name);
  }

  private static Map<String, Object> parameter(
      String name,
      String location,
      boolean required,
      Map<String, Object> schema,
      String description) {
    return ApiV2OpenApiQueryParameters.parameter(name, location, required, schema, description);
  }

  private static Map<String, Object> responseRef(String name) {
    return ApiV2OpenApiGenerator.responseRef(name);
  }

  private static Map<String, Object> headerRef(String name) {
    return ApiV2OpenApiGenerator.headerRef(name);
  }

  private static Map<String, Object> problemResponse(String description, int status, String code) {
    return ApiV2OpenApiGenerator.problemResponse(description, status, code);
  }

  private static Map<String, Object> problemResponse(
      String description, int status, Map<String, String> errors) {
    return ApiV2OpenApiGenerator.problemResponse(description, status, errors);
  }
}
