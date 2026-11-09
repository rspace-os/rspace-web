package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.objectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.putIfNotNull;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.parameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.parameters;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.access;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.addResourceAccessSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.applyAccess;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.applyRegisteredAccessFieldSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.addSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.addStandardSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.withNullableReferenceProperties;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.problemResponse;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.rateLimitHeaders;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.responseRef;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.standardHeaders;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.standardResponseReference;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.standardResponses;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResultSchemas.bulkResult;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResultSchemas.listResult;

import com.researchspace.api.v2.ApiV2ErrorCodes;
import com.researchspace.api.v2.model.ApiV2RuntimeFieldCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.DeletePolicy;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ResourceSchema;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

public final class ApiV2OpenApiGenerator {

  private static final String JSON = "application/json";
  static final List<Map<String, List<String>>> AUTHENTICATED_SECURITY =
      List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of()));

  private final ApiV2ResourceCatalog catalog;
  private final String title;
  private final String version;
  private final String serverUrl;
  private final ApiV2OpenApiQueryParameters queryParameters;

  public ApiV2OpenApiGenerator(ApiV2ResourceCatalog catalog, String title, String version) {
    this(catalog, title, version, "/");
  }

  public ApiV2OpenApiGenerator(
      ApiV2ResourceCatalog catalog, String title, String version, String serverUrl) {
    this.catalog = Objects.requireNonNull(catalog, "Resource catalog");
    this.title = requireText(title, "OpenAPI title");
    this.version = requireText(version, "OpenAPI version");
    this.serverUrl = requireText(serverUrl, "OpenAPI server URL");
    this.queryParameters = new ApiV2OpenApiQueryParameters(catalog);
  }

  public OpenAPI generate() {
    Map<String, Object> paths = new LinkedHashMap<>();
    Map<String, Object> schemas = new LinkedHashMap<>();
    Map<String, String> componentNames = componentNames(catalog.allSchemas());
    Map<String, ResourceSchema> resourceSchemas = new LinkedHashMap<>();
    catalog
        .allSchemas()
        .forEach(
            description -> resourceSchemas.put(description.resourceName(), description.schema()));
    Set<String> routableNames =
        catalog.routableResources().stream()
            .map(ApiV2ResourceRegistration::resourceName)
            .collect(Collectors.toUnmodifiableSet());

    addStandardSchemas(schemas);
    addResourceAccessSchemas(schemas);
    ApiV2OpenApiSchemas.responseSchemaFor(ApiV2RuntimeFieldCatalog.class, schemas);

    catalog
        .allSchemas()
        .forEach(
            description ->
                addSchemas(
                    schemas,
                    description.schema(),
                    componentNames,
                    routableNames.contains(description.resourceName())));
    Set<String> declaredComponents = new LinkedHashSet<>();
    catalog
        .routableResources()
        .forEach(
            resource ->
                applyRegisteredAccessFieldSchemas(
                    schemas,
                    resource,
                    componentNames.get(resource.resourceName()),
                    declaredComponents));
    catalog
        .routableResources()
        .forEach(
            resource ->
                queryParameters.applyRuntimeProjectionSchemas(
                    schemas, resource, componentNames.get(resource.resourceName())));
    catalog
        .routableResources()
        .forEach(resource -> addPaths(paths, resource, componentNames, resourceSchemas));

    schemas.replaceAll((name, schema) -> withNullableReferenceProperties(schema));
    Map<String, Object> components = new LinkedHashMap<>();
    components.put("schemas", schemas);
    components.put("responses", standardResponses());
    components.put("headers", standardHeaders());
    components.put("securitySchemes", securitySchemes());

    return new OpenAPI()
        .openapi("3.1.0")
        .info(
            new Info()
                .title(title)
                .version(version)
                .description(
                    "Generated contract for RSpace REST API v2. Resource operations and schemas "
                        + "are derived from the same definitions used at runtime."))
        .servers(
            List.of(
                new Server()
                    .url(serverUrl)
                    .description("RSpace deployment root for this generated document.")))
        .paths(Json31.mapper().convertValue(paths, Paths.class))
        .components(Json31.mapper().convertValue(components, Components.class));
  }

  private static Map<String, Object> privateNoStoreHeaders() {
    return ordered(
        "Cache-Control",
        ordered(
            "description",
            "Prevents storage of caller-specific subscription state.",
            "schema",
            ordered("type", "string", "const", "private, no-store")));
  }

  private void addPaths(
      Map<String, Object> paths,
      ApiV2ResourceRegistration<?, ?> resource,
      Map<String, String> componentNames,
      Map<String, ResourceSchema> resourceSchemas) {
    String collectionPath = "/api/v2/" + resource.resourceName();
    String itemPath = collectionPath + "/{id}";
    String countPath = collectionPath + "/count";
    String bulkPath = collectionPath + "/bulk";
    String component = componentNames.get(resource.resourceName());
    for (ResourceOperation operation : resource.exposedOperations()) {
      String path =
          switch (operation) {
            case COUNT -> countPath;
            case BULK_CREATE -> bulkPath;
            case READ, UPDATE, DELETE -> itemPath;
            default -> collectionPath;
          };
      Map<String, Object> pathItem =
          mutableObjectMap(paths.computeIfAbsent(path, ignored -> new LinkedHashMap<>()));
      String method = operation.httpMethod().toLowerCase(Locale.ROOT);
      if (pathItem.containsKey(method)) {
        throw new IllegalArgumentException("Duplicate OpenAPI operation " + method + " " + path);
      }
      pathItem.put(method, operation(resource, operation, component, resourceSchemas));
    }
    var namespaces = catalog.runtimeFieldsOf(resource.resourceName());
    if (!namespaces.isEmpty()) {
      Map<String, Object> responses = new LinkedHashMap<>();
      responses.put(
          "200",
          ordered(
              "description",
              "Caller-specific runtime field catalog.",
              "headers",
              Map.of("Cache-Control", Map.of("schema", Map.of("type", "string"))),
              "content",
              Map.of(JSON, Map.of("schema", ref("ApiV2RuntimeFieldCatalog")))));
      for (String code : List.of("400", "401", "403", "404", "406", "429", "500"))
        responses.put(code, standardResponseReference(code));
      paths.put(
          collectionPath + "/fields/{namespace}",
          Map.of(
              "get",
              ordered(
                  "operationId",
                  "get" + singularName(resource) + "RuntimeFields",
                  "tags",
                  List.of(resource.resourceName()),
                  "security",
                  AUTHENTICATED_SECURITY,
                  "responses",
                  responses,
                  "parameters",
                  List.of(
                      parameter(
                          "namespace",
                          "path",
                          true,
                          ordered(
                              "type",
                              "string",
                              "enum",
                              namespaces.stream().map(n -> n.namespace()).toList()),
                          "Runtime field namespace."),
                      parameter("q", "query", false, Map.of("type", "string"), "Search text."),
                      parameter(
                          "ids",
                          "query",
                          false,
                          Map.of("type", "string"),
                          "Comma-separated field identifiers."),
                      parameter(
                          "page",
                          "query",
                          false,
                          Map.of("type", "integer", "minimum", 1, "default", 1),
                          "Page number."),
                      parameter(
                          "limit",
                          "query",
                          false,
                          Map.of(
                              "type",
                              "integer",
                              "minimum",
                              1,
                              "maximum",
                              RuntimeFieldCatalogQuery.MAX_LIMIT,
                              "default",
                              RuntimeFieldCatalogQuery.DEFAULT_LIMIT),
                          "Page size.")))));
    }
  }

  private void addResourceAccessPaths(
      Map<String, Object> paths, ApiV2ResourceRegistration<?, ?> resource, String component) {
    new ApiV2OpenApiResourceAccessDocs().addResourceAccessPaths(paths, resource, component);
  }

  private Map<String, Object> operation(
      ApiV2ResourceRegistration<?, ?> resource,
      ResourceOperation operation,
      String component,
      Map<String, ResourceSchema> resourceSchemas) {
    ResourceSchema schema = resource.description().schema();
    OpenApiOperationDocumentation documentation = resource.operationDocumentation(operation);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("operationId", operationId(operation, component, singularName(resource)));
    result.put(
        "summary",
        documentation.summary() == null ? summary(resource, operation) : documentation.summary());
    result.put(
        "description",
        documentation.description() == null
            ? description(resource, operation)
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
            queryParameters.runtimeFieldsExtension(resource));
    if (requiresIfMatch(resource, operation)) {
      parameters.add(
          parameter(
              "If-Match",
              "header",
              true,
              ordered("type", "string", "pattern", "^\\\"[0-9]+\\\"$"),
              "Strong entity tag containing the current resource version."));
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
    if (operation == ResourceOperation.CREATE
        || operation == ResourceOperation.BULK_CREATE
        || operation == ResourceOperation.UPDATE
        || operation == ResourceOperation.BULK_UPDATE) {
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
      result.put("requestBody", ordered("required", true, "content", Map.of(JSON, media)));
    }
    result.put("responses", responses(resource, operation, component, documentation));
    result.put("x-rspace-operation", operation.name());
    result.putAll(documentation.extensions());
    return result;
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
            Map.of(JSON, successMedia)));
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
    responses.put("400", responseRef("BadRequest"));
    responses.put("401", responseRef("Unauthenticated"));
    responses.put("403", responseRef("Forbidden"));
    if (operation == ResourceOperation.READ
        || operation == ResourceOperation.UPDATE
        || operation == ResourceOperation.DELETE) {
      responses.put("404", responseRef("NotFound"));
    }
    if (operation == ResourceOperation.CREATE
        || operation == ResourceOperation.BULK_CREATE
        || operation == ResourceOperation.UPDATE
        || operation == ResourceOperation.BULK_UPDATE) {
      responses.put("415", responseRef("UnsupportedMediaType"));
    }
    responses.put("406", responseRef("NotAcceptable"));
    if (operation == ResourceOperation.BULK_UPDATE || operation == ResourceOperation.BULK_DELETE) {
      responses.put("422", responseRef("BulkLimit"));
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
    responses.put("429", responseRef("TooManyRequests"));
    responses.put("500", responseRef("UnexpectedError"));
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

  private static Map<String, Object> securitySchemes() {
    return ordered(
        "apiKey",
        ordered("type", "apiKey", "in", "header", "name", "apiKey"),
        "bearerAuth",
        ordered("type", "http", "scheme", "bearer"),
        "browserSession",
        ordered(
            "type",
            "apiKey",
            "in",
            "cookie",
            "name",
            "JSESSIONID",
            "description",
            "Authenticated browser session. A deployment can rename this cookie."));
  }

  private static Map<String, String> componentNames(
      Collection<CollectionDescription<?>> resources) {
    Map<String, String> result = new LinkedHashMap<>();
    Set<String> used = new LinkedHashSet<>();
    for (CollectionDescription<?> resource : resources) {
      String component = pascalCase(resource.resourceName());
      if (!used.add(component)) {
        throw new IllegalArgumentException("OpenAPI component name collision: " + component);
      }
      result.put(resource.resourceName(), component);
    }
    return Map.copyOf(result);
  }

  static String singularName(ApiV2ResourceRegistration<?, ?> resource) {
    String name = resource.resourceName();
    if (!resource.description().singular().isBlank())
      return pascalCase(resource.description().singular());
    if (!name.endsWith("s") || name.endsWith("ss") || name.endsWith("ies")) {
      throw new IllegalArgumentException("Resource needs an explicit singular definition: " + name);
    }
    return pascalCase(name.substring(0, name.length() - 1));
  }

  private static String pascalCase(String value) {
    StringBuilder result = new StringBuilder();
    for (String part : value.split("[^A-Za-z0-9]+")) {
      if (!part.isEmpty()) {
        result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
      }
    }
    if (result.isEmpty() || Character.isDigit(result.charAt(0))) {
      throw new IllegalArgumentException("Invalid OpenAPI component name for " + value);
    }
    return result.toString();
  }

  private static String operationId(
      ResourceOperation operation, String component, String singular) {
    String prefix =
        switch (operation) {
          case LIST -> "list";
          case COUNT -> "count";
          case READ -> "get";
          case CREATE -> "create";
          case BULK_CREATE -> "createMany";
          case UPDATE -> "update";
          case BULK_UPDATE -> "updateMany";
          case DELETE -> "delete";
          case BULK_DELETE -> "deleteMany";
        };
    return prefix
        + switch (operation) {
          case READ, CREATE, UPDATE, DELETE -> singular;
          default -> component;
        };
  }

  private static String summary(
      ApiV2ResourceRegistration<?, ?> registration, ResourceOperation operation) {
    String resource = registration.resourceName();
    return switch (operation) {
      case LIST -> "List " + resource;
      case COUNT -> "Count " + resource;
      case READ -> "Get one " + resource;
      case CREATE -> "Create one " + resource;
      case BULK_CREATE -> "Create many " + resource;
      case UPDATE -> "Update one " + resource;
      case BULK_UPDATE -> "Update matching " + resource;
      case DELETE ->
          (registration.mutationPolicy().delete().kind() == DeletePolicy.Kind.SOFT
                  ? "Archive one "
                  : "Delete one ")
              + resource;
      case BULK_DELETE ->
          (registration.mutationPolicy().delete().kind() == DeletePolicy.Kind.SOFT
                  ? "Archive matching "
                  : "Delete matching ")
              + resource;
    };
  }

  private static String description(
      ApiV2ResourceRegistration<?, ?> registration, ResourceOperation operation) {
    String resource = registration.resourceName();
    return switch (operation) {
      case LIST ->
          "Returns a paginated view of "
              + resource
              + ". Filtering, sorting, sparse fieldsets, exclusions, and relationship depth are"
              + " applied before rendering.";
      case COUNT -> "Counts " + resource + " matching the optional bounded filter.";
      case READ ->
          "Returns one visible "
              + resource
              + ". A resource that is absent or not readable is reported as not found.";
      case CREATE -> "Creates one " + resource + " from the documented create schema.";
      case BULK_CREATE ->
          "Creates the supplied "
              + resource
              + " documents in input order. The batch is handled as one service-layer mutation;"
              + " validation failure prevents the entire batch from being created.";
      case UPDATE -> "Partially updates one " + resource + " and returns the updated document.";
      case BULK_UPDATE ->
          "Updates every "
              + resource
              + " matching the required filter. The operation is handled as one service-layer"
              + " mutation; resource-specific limits are reported with status 422.";
      case DELETE ->
          registration.mutationPolicy().delete().kind() == DeletePolicy.Kind.SOFT
              ? "Archives one "
                  + resource
                  + " and returns the archived document. Repeating ordinary DELETE is a no-op."
                  + (registration.mutationPolicy().delete().permanentOption()
                      ? " The explicit permanent=true option permanently removes the resource for"
                          + " a direct sysadmin and returns no document."
                      : "")
              : "Deletes one " + resource + " and returns the deleted document.";
      case BULK_DELETE ->
          (registration.mutationPolicy().delete().kind() == DeletePolicy.Kind.SOFT
                  ? "Archives every "
                  : "Deletes every ")
              + resource
              + " matching the required filter. The operation is handled as one service-layer"
              + " mutation; resource-specific limits are reported with status 422.";
    };
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
