package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator.AUTHENTICATED_SECURITY;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.content;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.idParameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.ifMatchParameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.parameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.headerRef;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.problemResponse;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.putStandardResponses;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.researchspace.api.v2.openapi.ApiV2OpenApiText.Key;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessDocumentation.AuthenticationRequirement;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessPolicySchema;
import com.researchspace.service.resourceaccess.ReplaceResourceAccessRequest;
import com.researchspace.service.resourceaccess.ResourceAccessDocument;
import com.researchspace.service.resourceaccess.ResourceAccessGrant;
import com.researchspace.service.resourceaccess.ResourceGranteeDirectoryEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ApiV2OpenApiResourceAccessDocs {

  private enum ResourceAccessOperation {
    READ("get", "Access", Key.ACCESS_READ_SUMMARY, "ACCESS_READ"),
    REPLACE("replace", "Access", Key.ACCESS_REPLACE_SUMMARY, "ACCESS_REPLACE"),
    LEAVE("leave", "Access", Key.ACCESS_LEAVE_SUMMARY, "ACCESS_LEAVE"),
    SEARCH("search", "AccessGrantees", Key.ACCESS_SEARCH_SUMMARY, "ACCESS_GRANTEE_SEARCH");

    private final String operationPrefix;
    private final String operationSuffix;
    private final Key summary;
    private final String extensionValue;

    ResourceAccessOperation(
        String operationPrefix, String operationSuffix, Key summary, String extensionValue) {
      this.operationPrefix = operationPrefix;
      this.operationSuffix = operationSuffix;
      this.summary = summary;
      this.extensionValue = extensionValue;
    }

    String operationId(String component) {
      return operationPrefix + component + operationSuffix;
    }

    Key summary() {
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
    ApiV2OpenApiSchemas.responseSchemaFor(ResourceAccessDocument.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ResourceAccessGrant.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ResourceGranteeDirectoryEntry.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ReplaceResourceAccessRequest.class, schemas);
  }

  private final ApiV2OpenApiText text;

  ApiV2OpenApiResourceAccessDocs(ApiV2OpenApiText text) {
    this.text = text;
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
    operation.put(
        "operationId", operationKind.operationId(ApiV2OpenApiNames.singularName(resource)));
    operation.put("summary", text.get(operationKind.summary()));
    operation.put("tags", List.of(resource.resourceName()));
    operation.put("security", AUTHENTICATED_SECURITY);
    operation.put(
        "x-rspace-access",
        ApiV2OpenApiAccessPolicies.extension(
            operationKind.managesAssignments()
                ? new AccessDocumentation(
                    "ResourceAccessManagers",
                    text.get(Key.ACCESS_MANAGING_CALLERS),
                    Set.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN))
                : new AccessDocumentation(
                    "ResourceAccessAuthorized",
                    text.get(Key.ACCESS_AUTHORIZED_CALLERS),
                    Set.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN))));
    List<Map<String, Object>> parameters = new ArrayList<>();
    parameters.add(idParameter(resource.description().schema(), text));
    if (operationKind == ResourceAccessOperation.REPLACE) {
      parameters.add(ifMatchParameter(text.get(Key.ACCESS_IF_MATCH)));
      operation.put(
          "requestBody",
          ordered(
              "required",
              true,
              "content",
              content(APPLICATION_JSON_VALUE, ref("ResourceAccessReplacement"))));
    }
    if (operationKind == ResourceAccessOperation.SEARCH) {
      parameters.add(
          parameter(
              "query",
              "query",
              true,
              ordered("type", "string", "minLength", 2),
              text.get(Key.ACCESS_QUERY)));
      parameters.add(
          parameter(
              "limit",
              "query",
              false,
              ordered("type", "integer", "minimum", 1, "maximum", 50, "default", 20),
              text.get(Key.ACCESS_LIMIT)));
    }
    operation.put("parameters", parameters);
    operation.put("responses", resourceAccessResponses(operationKind));
    operation.put("x-rspace-operation", operationKind.extensionValue());
    return operation;
  }

  private Map<String, Object> resourceAccessResponses(ResourceAccessOperation operationKind) {
    Map<String, Object> responses = new LinkedHashMap<>();
    if (operationKind == ResourceAccessOperation.LEAVE) {
      responses.put("204", ordered("description", text.get(Key.ACCESS_ASSIGNMENT_ABSENT)));
    } else {
      Map<String, Object> schema =
          operationKind == ResourceAccessOperation.SEARCH
              ? ordered("type", "array", "items", ref("ResourceGranteeDirectoryEntry"))
              : ref("ResourceAccessDocument");
      Map<String, Object> success =
          ordered(
              "description",
              operationKind == ResourceAccessOperation.SEARCH
                  ? text.get(Key.ACCESS_GRANTEES)
                  : text.get(Key.ACCESS_DOCUMENT),
              "content",
              content(APPLICATION_JSON_VALUE, schema));
      if (operationKind != ResourceAccessOperation.SEARCH) {
        success.put("headers", ordered("ETag", headerRef("ETag")));
      }
      responses.put("200", success);
    }
    putStandardResponses(responses, "400", "401", "403", "404", "406", "429", "500");
    if (operationKind.changesAssignments()) {
      responses.put(
          "409",
          problemResponse(
              text.get(Key.ACCESS_OWNER_REQUIRED),
              409,
              "errors.api.v2.resourceAccess.ownerRequired"));
    }
    if (operationKind == ResourceAccessOperation.REPLACE) {
      responses.put(
          "412",
          problemResponse(text.get(Key.ACCESS_STALE), 412, "errors.api.v2.resourceAccess.stale"));
      putStandardResponses(responses, "415");
      responses.put(
          "428",
          problemResponse(
              text.get(Key.RESPONSE_IF_MATCH_REQUIRED),
              428,
              "errors.api.v2.resourceAccess.ifMatchRequired"));
    }
    return responses;
  }

  static AccessDocumentation access(AccessPolicySchema access, ResourceOperation operation) {
    return switch (operation) {
      case LIST, COUNT, READ, RUNTIME_FIELDS -> access.readAccess();
      case CREATE, BULK_CREATE -> access.createAccess();
      case UPDATE, BULK_UPDATE -> access.updateAccess();
      case DELETE, BULK_DELETE -> access.deleteAccess();
    };
  }

  static void applyAccess(Map<String, Object> operation, AccessDocumentation access) {
    operation.put("security", security(access.authenticationRequirement()));
    operation.put("x-rspace-access", ApiV2OpenApiAccessPolicies.extension(access));
  }

  private static List<Map<String, List<String>>> security(
      AuthenticationRequirement authenticationRequirement) {
    return authenticationRequirement == AuthenticationRequirement.PUBLIC
        ? List.of()
        : AUTHENTICATED_SECURITY;
  }
}
