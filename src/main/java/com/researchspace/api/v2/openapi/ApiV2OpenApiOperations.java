package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.objectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.putIfNotNull;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.PARAMETERS;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.ifMatchParameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.parameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.parameters;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.access;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.applyAccess;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.PROBLEM_JSON;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.RESPONSES;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.problemResponse;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.putStandardResponses;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.rateLimitHeaders;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.standardResponses;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResultSchemas.bulkResult;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResultSchemas.listResult;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.researchspace.api.v2.ApiV2ErrorCodes;
import com.researchspace.api.v2.openapi.ApiV2OpenApiText.Key;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.IdempotencyPolicy;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.ResourceSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Builds the OpenAPI operation object for one standard resource operation. */
final class ApiV2OpenApiOperations {

  private final ApiV2OpenApiRuntimeFieldDocs runtimeFields;
  private final ApiV2OpenApiText text;

  ApiV2OpenApiOperations(ApiV2OpenApiRuntimeFieldDocs runtimeFields, ApiV2OpenApiText text) {
    this.runtimeFields = runtimeFields;
    this.text = text;
  }

  Map<String, Object> operation(
      ApiV2ResourceRegistration<?, ?> resource,
      ResourceOperation operation,
      String component,
      Map<String, ResourceSchema> resourceSchemas,
      Map<String, Object> schemas,
      ApiV2OpenApiComponents components) {
    ResourceSchema schema = resource.description().schema();
    OpenApiOperationDocumentation documentation = resource.operationDocumentation(operation);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
        "operationId",
        ApiV2OpenApiNames.operationId(
            operation, component, ApiV2OpenApiNames.singularName(resource)));
    result.put(
        "summary",
        documentation.summary() == null
            ? ApiV2OpenApiNames.summary(resource, operation, text)
            : documentation.summary());
    result.put(
        "description",
        documentation.description() == null
            ? ApiV2OpenApiNames.description(resource, operation, text)
            : documentation.description());
    result.put(
        "tags",
        documentation.tags().isEmpty() ? List.of(resource.resourceName()) : documentation.tags());
    if (documentation.deprecated()) {
      result.put("deprecated", true);
    }

    AccessDocumentation access = access(schema.access(), operation);
    applyAccess(result, access);
    List<Map<String, Object>> parameters =
        parameters(
            operation,
            component,
            components,
            schema,
            resourceSchemas,
            resource.registry(),
            runtimeFields.runtimeFieldsExtension(resource),
            text);
    if (operation == ResourceOperation.CREATE
        && resource.mutationPolicy().idempotency() == IdempotencyPolicy.CREATOR_SCOPED) {
      parameters.add(
          components.reference(
              PARAMETERS,
              "IdempotencyKey",
              parameter(
                  "Idempotency-Key",
                  "header",
                  false,
                  ordered(
                      "type",
                      "string",
                      "minLength",
                      1,
                      "maxLength",
                      64,
                      "pattern",
                      "^[A-Za-z0-9._:-]+$"),
                  text.get(Key.PARAMETER_IDEMPOTENCY_KEY))));
    }
    if (resource.mutationPolicy().version().field().isPresent()
        && (operation == ResourceOperation.UPDATE || operation == ResourceOperation.DELETE)) {
      boolean required = requiresIfMatch(resource, operation);
      parameters.add(
          components.reference(
              PARAMETERS,
              required ? "IfMatch" : "IfMatchOptional",
              ifMatchParameter(required, text.get(Key.PARAMETER_IF_MATCH))));
    }
    if (operation == ResourceOperation.DELETE
        && resource.mutationPolicy().delete().permanentOption()) {
      parameters.add(
          components.reference(
              PARAMETERS,
              "Permanent",
              parameter(
                  "permanent",
                  "query",
                  false,
                  ordered("type", "boolean", "default", false),
                  text.get(Key.PARAMETER_PERMANENT))));
    }
    if (!parameters.isEmpty()) {
      result.put("parameters", parameters);
    }
    if (hasRequestBody(operation)) {
      result.put("requestBody", requestBody(operation, component, documentation));
    }
    result.put(
        "responses", responses(resource, operation, component, documentation, schemas, components));
    result.put("x-rspace-operation", operation.name());
    result.putAll(documentation.extensions());
    return result;
  }

  private static Map<String, Object> requestBody(
      ResourceOperation operation, String component, OpenApiOperationDocumentation documentation) {
    Map<String, Object> media = new LinkedHashMap<>();
    if (operation == ResourceOperation.BULK_CREATE) {
      media.put(
          "schema",
          ordered(
              "type",
              "object",
              "required",
              List.of("docs"),
              "additionalProperties",
              false,
              "properties",
              Map.of(
                  "docs",
                  ordered("type", "array", "minItems", 1, "items", ref(component + "Create")))));
    } else {
      String requestComponent =
          component + (operation == ResourceOperation.CREATE ? "Create" : "Update");
      media.put("schema", ref(requestComponent));
    }
    putIfNotNull(media, "example", documentation.requestExample());
    return ordered("required", true, "content", Map.of(APPLICATION_JSON_VALUE, media));
  }

  private static boolean hasRequestBody(ResourceOperation operation) {
    return operation == ResourceOperation.CREATE
        || operation == ResourceOperation.BULK_CREATE
        || operation == ResourceOperation.UPDATE
        || operation == ResourceOperation.BULK_UPDATE;
  }

  private static boolean requiresIfMatch(
      ApiV2ResourceRegistration<?, ?> resource, ResourceOperation operation) {
    return (operation == ResourceOperation.UPDATE
            && resource.mutationPolicy().version().missingCode().isPresent())
        || (operation == ResourceOperation.DELETE
            && resource.mutationPolicy().version().requires(ResourceOperation.DELETE));
  }

  private Map<String, Object> responses(
      ApiV2ResourceRegistration<?, ?> resource,
      ResourceOperation operation,
      String component,
      OpenApiOperationDocumentation documentation,
      Map<String, Object> schemas,
      ApiV2OpenApiComponents components) {
    Map<String, Object> responses = new LinkedHashMap<>();
    String successStatus =
        operation == ResourceOperation.CREATE || operation == ResourceOperation.BULK_CREATE
            ? "201"
            : "200";
    Map<String, Object> successSchema =
        switch (operation) {
          case LIST -> listResult(ref(component + "Read"), text);
          case COUNT -> ref("ApiV2CountResult");
          case BULK_CREATE, BULK_UPDATE, BULK_DELETE -> bulkResult(ref(component + "Read"));
          default -> ref(component + "Read");
        };
    Map<String, Object> successMedia = new LinkedHashMap<>();
    successMedia.put("schema", successSchema);
    putIfNotNull(successMedia, "example", documentation.responseExample());
    Map<String, Object> success =
        ordered(
            "description",
            documentation
                .responses()
                .getOrDefault(
                    Integer.valueOf(successStatus),
                    new OpenApiOperationDocumentation.Response(
                        ApiV2OpenApiNames.success(operation, text), Map.of()))
                .description(),
            "headers",
            rateLimitHeaders(),
            "content",
            Map.of(APPLICATION_JSON_VALUE, successMedia));
    boolean documentedSuccess =
        documentation.responseExample() != null
            || documentation.responses().containsKey(Integer.valueOf(successStatus));
    responses.put(
        successStatus,
        operation == ResourceOperation.COUNT && !documentedSuccess
            ? components.reference(RESPONSES, "CountResult", success)
            : success);
    if (operation == ResourceOperation.CREATE
        && resource.mutationPolicy().idempotency() == IdempotencyPolicy.CREATOR_SCOPED) {
      responses.put(
          "200",
          ordered(
              "description",
              text.get(Key.RESPONSE_CREATE_RETRY),
              "headers",
              rateLimitHeaders(),
              "content",
              Map.of(APPLICATION_JSON_VALUE, successMedia)));
    }
    if (operation == ResourceOperation.DELETE
        && resource.mutationPolicy().delete().permanentOption()) {
      responses.put(
          "204",
          sharedUnlessDocumented(
              components,
              documentation,
              204,
              "PermanentlyRemoved",
              ordered(
                  "description",
                  text.get(Key.RESPONSE_PERMANENTLY_REMOVED),
                  "headers",
                  rateLimitHeaders())));
    }
    putStandardResponses(responses, "400", "401", "403", "406", "429", "500");
    if (operation == ResourceOperation.READ
        || operation == ResourceOperation.UPDATE
        || operation == ResourceOperation.DELETE) {
      putStandardResponses(responses, "404");
    }
    if (hasRequestBody(operation)) {
      putStandardResponses(responses, "415");
    }
    if (operation == ResourceOperation.BULK_UPDATE || operation == ResourceOperation.BULK_DELETE) {
      putStandardResponses(responses, "422");
    }
    if (requiresIfMatch(resource, operation)) {
      responses.put(
          "428",
          sharedUnlessDocumented(
              components,
              documentation,
              428,
              component + "IfMatchRequired",
              problemResponse(
                  text.get(Key.RESPONSE_IF_MATCH_REQUIRED),
                  428,
                  resource
                      .mutationPolicy()
                      .version()
                      .missingCode()
                      .orElse(ApiV2ErrorCodes.messageKeyFor(HttpStatus.PRECONDITION_REQUIRED)))));
    }
    if (resource.mutationPolicy().version().field().isPresent()
        && (operation == ResourceOperation.UPDATE || operation == ResourceOperation.DELETE)) {
      responses.put(
          "412",
          sharedUnlessDocumented(
              components,
              documentation,
              412,
              "VersionChanged",
              problemResponse(
                  text.get(Key.RESPONSE_VERSION_CHANGED), 412, "errors.concurrentChange")));
    }
    if (operation == ResourceOperation.DELETE && documentation.responses().containsKey(204)) {
      responses.remove("200");
      responses.put("204", Map.of("description", documentation.responses().get(204).description()));
    }
    documentation.responses().entrySet().stream()
        .filter(entry -> !entry.getValue().errors().isEmpty())
        .forEach(
            entry ->
                responses.put(
                    String.valueOf(entry.getKey()),
                    problemResponse(
                        entry.getValue().description(),
                        entry.getKey(),
                        entry.getValue().errors(),
                        components)));
    documentation
        .responses()
        .forEach(
            (status, responseDocumentation) -> {
              Object existing = responses.get(String.valueOf(status));
              if (existing instanceof Map<?, ?> response) {
                Map<String, Object> mutable = objectMap(response);
                mutable.put("description", responseDocumentation.description());
                responses.put(String.valueOf(status), mutable);
              }
            });
    documentation
        .problemSchemas()
        .forEach(
            (status, type) -> {
              Object existing = responses.get(String.valueOf(status));
              if (existing == null)
                throw new IllegalArgumentException(
                    "Problem schema needs a documented response: " + status);
              Map<String, Object> response = new LinkedHashMap<>(mutableObjectMap(existing));
              if (response.containsKey("$ref")) {
                String name =
                    response.remove("$ref").toString().replace("#/components/responses/", "");
                response.putAll(mutableObjectMap(standardResponses(text).get(name)));
              }
              Map<String, Object> content =
                  new LinkedHashMap<>(mutableObjectMap(response.get("content")));
              Map<String, Object> media =
                  new LinkedHashMap<>(mutableObjectMap(content.get(PROBLEM_JSON)));
              media.put("schema", ApiV2OpenApiSchemas.responseSchemaFor(type, schemas));
              content.put(PROBLEM_JSON, media);
              response.put("content", content);
              responses.put(String.valueOf(status), response);
            });
    return responses;
  }

  /**
   * A reference to the shared {@code response}, unless the operation documents that status itself:
   * its description then replaces the shared one, so the response stays inline.
   */
  private static Map<String, Object> sharedUnlessDocumented(
      ApiV2OpenApiComponents components,
      OpenApiOperationDocumentation documentation,
      int status,
      String name,
      Map<String, Object> response) {
    return documentation.responses().containsKey(status)
        ? response
        : components.reference(RESPONSES, name, response);
  }
}
