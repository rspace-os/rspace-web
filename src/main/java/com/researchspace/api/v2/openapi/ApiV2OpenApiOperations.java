package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.objectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.putIfNotNull;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.ifMatchParameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.parameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.parameters;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.access;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.applyAccess;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.problemResponse;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.putStandardResponses;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.rateLimitHeaders;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResultSchemas.bulkResult;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResultSchemas.listResult;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.researchspace.api.v2.ApiV2ErrorCodes;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.ResourceSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Builds the OpenAPI operation object for one standard resource operation. */
final class ApiV2OpenApiOperations {

  private final ApiV2OpenApiRuntimeFieldDocs runtimeFields;

  ApiV2OpenApiOperations(ApiV2OpenApiRuntimeFieldDocs runtimeFields) {
    this.runtimeFields = runtimeFields;
  }

  Map<String, Object> operation(
      ApiV2ResourceRegistration<?, ?> resource,
      ResourceOperation operation,
      String component,
      Map<String, ResourceSchema> resourceSchemas) {
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
            ? ApiV2OpenApiNames.summary(resource, operation)
            : documentation.summary());
    result.put(
        "description",
        documentation.description() == null
            ? ApiV2OpenApiNames.description(resource, operation)
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
            schema,
            resourceSchemas,
            resource.registry(),
            runtimeFields.runtimeFieldsExtension(resource));
    if (requiresIfMatch(resource, operation)) {
      parameters.add(
          ifMatchParameter("Strong entity tag containing the current resource version."));
    }
    if (operation == ResourceOperation.DELETE
        && resource.mutationPolicy().delete().permanentOption()) {
      parameters.add(
          parameter(
              "permanent",
              "query",
              false,
              ordered("type", "boolean", "default", false),
              "When true, permanently removes the resource. This is restricted to"
                  + " a direct sysadmin and returns 204. The default false performs the ordinary"
                  + " retry-safe archive transition."));
    }
    if (!parameters.isEmpty()) {
      result.put("parameters", parameters);
    }
    if (hasRequestBody(operation)) {
      result.put("requestBody", requestBody(operation, component, documentation));
    }
    result.put("responses", responses(resource, operation, component, documentation));
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

  private static Map<String, Object> responses(
      ApiV2ResourceRegistration<?, ?> resource,
      ResourceOperation operation,
      String component,
      OpenApiOperationDocumentation documentation) {
    Map<String, Object> responses = new LinkedHashMap<>();
    String successStatus =
        operation == ResourceOperation.CREATE || operation == ResourceOperation.BULK_CREATE
            ? "201"
            : "200";
    Map<String, Object> successSchema =
        switch (operation) {
          case LIST -> listResult(ref(component + "Read"));
          case COUNT -> ref("ApiV2CountResult");
          case BULK_CREATE, BULK_UPDATE, BULK_DELETE -> bulkResult(ref(component + "Read"));
          default -> ref(component + "Read");
        };
    Map<String, Object> successMedia = new LinkedHashMap<>();
    successMedia.put("schema", successSchema);
    putIfNotNull(successMedia, "example", documentation.responseExample());
    responses.put(
        successStatus,
        ordered(
            "description",
            documentation
                .responses()
                .getOrDefault(
                    Integer.valueOf(successStatus),
                    new OpenApiOperationDocumentation.Response(
                        "Successful " + operation.name().toLowerCase(Locale.ROOT) + ".", Map.of()))
                .description(),
            "headers",
            rateLimitHeaders(),
            "content",
            Map.of(APPLICATION_JSON_VALUE, successMedia)));
    if (operation == ResourceOperation.DELETE
        && resource.mutationPolicy().delete().permanentOption()) {
      responses.put(
          "204",
          ordered(
              "description",
              "The resource and its dependent live data were permanently removed.",
              "headers",
              rateLimitHeaders()));
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
          problemResponse(
              "A strong If-Match header is required.",
              428,
              resource
                  .ifMatchRequiredCode()
                  .orElse(ApiV2ErrorCodes.messageKeyFor(HttpStatus.PRECONDITION_REQUIRED))));
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
                        entry.getValue().errors())));
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
    return responses;
  }
}
