package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.objectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessPolicySchema;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceSchema;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ApiV2OpenApiGenerator {

  private static final String JSON = "application/json";
  private static final String PROBLEM_JSON = "application/problem+json";
  static final List<Map<String, List<String>>> AUTHENTICATED_SECURITY =
      List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of()));
  private static final Map<String, String> STANDARD_RESPONSE_NAMES =
      Map.of(
          "400", "BadRequest",
          "401", "Unauthenticated",
          "403", "Forbidden",
          "404", "NotFound",
          "406", "NotAcceptable",
          "415", "UnsupportedMediaType",
          "422", "BulkLimit",
          "429", "TooManyRequests",
          "500", "UnexpectedError");

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
                applyRuntimeProjectionSchemas(
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

    List<Tag> tags = new ArrayList<>();
    catalog
        .routableResources()
        .forEach(
            resource ->
                tags.add(
                    new Tag()
                        .name(resource.resourceName())
                        .description(
                            "Operations on the " + resource.resourceName() + " collection.")));
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
        .tags(tags)
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
    addResourceAccessPaths(paths, resource, component);
  }

  private static void addResourceAccessSchemas(Map<String, Object> schemas) {
    ApiV2OpenApiResourceAccessDocs.addResourceAccessSchemas(schemas);
  }

  private static void applyRegisteredAccessFieldSchemas(
      Map<String, Object> schemas,
      ApiV2ResourceRegistration<?, ?> resource,
      String component,
      Set<String> declaredComponents) {
    ApiV2OpenApiResourceAccessDocs.applyRegisteredAccessFieldSchemas(
        schemas, resource, component, declaredComponents);
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
    result.put("operationId", operationId(operation, component));
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
            runtimeFieldsExtension(resource));
    if ((operation == ResourceOperation.UPDATE && resource.ifMatchRequiredCode().isPresent())
        || (operation == ResourceOperation.DELETE && resource.deleteRequiresIfMatch())) {
      parameters.add(
          parameter(
              "If-Match",
              "header",
              true,
              ordered("type", "string", "pattern", "^\\\"[0-9]+\\\"$"),
              "Strong entity tag containing the current resource version."));
    }
    if (operation == ResourceOperation.DELETE && resource.supportsPermanentDelete()) {
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

  private static AccessDocumentation access(
      AccessPolicySchema access, ResourceOperation operation) {
    return ApiV2OpenApiResourceAccessDocs.access(access, operation);
  }

  private static void applyAccess(Map<String, Object> operation, AccessDocumentation access) {
    ApiV2OpenApiResourceAccessDocs.applyAccess(operation, access);
  }

  private void applyRuntimeProjectionSchemas(
      Map<String, Object> schemas, ApiV2ResourceRegistration<?, ?> resource, String component) {
    queryParameters.applyRuntimeProjectionSchemas(schemas, resource, component);
  }

  private List<Map<String, Object>> runtimeFieldsExtension(
      ApiV2ResourceRegistration<?, ?> resource) {
    return queryParameters.runtimeFieldsExtension(resource);
  }

  private static List<Map<String, Object>> parameters(
      ResourceOperation operation,
      ResourceSchema schema,
      Map<String, ResourceSchema> resourceSchemas,
      ResourceRegistry registry,
      List<Map<String, Object>> runtimeFields) {
    return ApiV2OpenApiQueryParameters.parameters(
        operation, schema, resourceSchemas, registry, runtimeFields);
  }

  private static Map<String, Object> parameter(
      String name,
      String location,
      boolean required,
      Map<String, Object> schema,
      String description) {
    return ApiV2OpenApiQueryParameters.parameter(name, location, required, schema, description);
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
    if (operation == ResourceOperation.DELETE && resource.supportsPermanentDelete()) {
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

  private static void addSchemas(
      Map<String, Object> components,
      ResourceSchema resource,
      Map<String, String> names,
      boolean routable) {
    ApiV2OpenApiResourceDocumentBuilder.addSchemas(components, resource, names, routable);
  }

  private static void addStandardSchemas(Map<String, Object> schemas) {
    ApiV2OpenApiResourceDocumentBuilder.addStandardSchemas(schemas);
  }

  private static void putIfNotNull(Map<String, Object> target, String name, Object value) {
    ApiV2OpenApiResourceDocumentBuilder.putIfNotNull(target, name, value);
  }

  static Map<String, Object> listResult(Map<String, Object> item) {
    return ordered(
        "type",
        "object",
        "required",
        List.of("docs", "totalDocs", "limit", "page", "totalPages"),
        "properties",
        ordered(
            "docs",
            ordered("type", "array", "items", item),
            "totalDocs",
            ordered("type", "integer", "format", "int64"),
            "limit",
            ordered("type", "integer"),
            "page",
            ordered("type", "integer"),
            "pagingCounter",
            ordered("type", "integer", "format", "int64"),
            "totalPages",
            ordered("type", "integer"),
            "hasPrevPage",
            ordered("type", "boolean"),
            "hasNextPage",
            ordered("type", "boolean"),
            "prevPage",
            ordered("type", List.of("integer", "null")),
            "nextPage",
            ordered("type", List.of("integer", "null"))));
  }

  private static Map<String, Object> auditPage(Map<String, Object> item) {
    return ApiV2OpenApiAuditDocs.auditPage(item);
  }

  private static Map<String, Object> bulkResult(Map<String, Object> item) {
    return ordered(
        "type",
        "object",
        "required",
        List.of("docs"),
        "properties",
        ordered("docs", ordered("type", "array", "items", item)));
  }

  static Map<String, Object> standardResponses() {
    return createStandardResponses();
  }

  private static Map<String, Object> createStandardResponses() {
    return ordered(
        STANDARD_RESPONSE_NAMES.get("400"),
        problemResponse("The request is invalid.", 400, "BAD_REQUEST"),
        STANDARD_RESPONSE_NAMES.get("401"),
        problemResponse("Authentication is required.", 401, "AUTHENTICATION_REQUIRED"),
        STANDARD_RESPONSE_NAMES.get("403"),
        problemResponse("The caller is not authorized.", 403, "FORBIDDEN"),
        STANDARD_RESPONSE_NAMES.get("404"),
        problemResponse("The resource was not found or is not visible.", 404, "NOT_FOUND"),
        STANDARD_RESPONSE_NAMES.get("406"),
        problemResponse(
            "No acceptable response representation is available.", 406, "NOT_ACCEPTABLE"),
        STANDARD_RESPONSE_NAMES.get("415"),
        problemResponse("The request media type is unsupported.", 415, "UNSUPPORTED_MEDIA_TYPE"),
        STANDARD_RESPONSE_NAMES.get("422"),
        problemResponse("The bulk operation exceeds the configured limit.", 422, "BULK_LIMIT"),
        STANDARD_RESPONSE_NAMES.get("429"),
        ordered(
            "description",
            "The request was throttled.",
            "content",
            problemContent("The request was throttled.", 429, "TOO_MANY_REQUESTS"),
            "headers",
            rateLimitHeaders()),
        STANDARD_RESPONSE_NAMES.get("500"),
        problemResponse("An unexpected server error occurred.", 500, "INTERNAL_SERVER_ERROR"));
  }

  static Map<String, Object> standardResponseReference(String status) {
    String name = STANDARD_RESPONSE_NAMES.get(status);
    return name == null ? null : Map.of("$ref", "#/components/responses/" + name);
  }

  static Map<String, Object> problemResponse(String description, int status, String code) {
    return ordered(
        "description", description, "content", problemContent(description, status, code));
  }

  static Map<String, Object> problemResponse(
      String description, int status, Map<String, String> errors) {
    if (errors.size() == 1) {
      Map.Entry<String, String> error = errors.entrySet().iterator().next();
      return problemResponse(description, status, error.getKey());
    }
    Map<String, Object> examples = new LinkedHashMap<>();
    errors.forEach(
        (code, errorDescription) ->
            examples.put(
                code,
                ordered(
                    "summary",
                    errorDescription,
                    "value",
                    ordered(
                        "title",
                        errorDescription,
                        "status",
                        status,
                        "code",
                        code,
                        "detail",
                        errorDescription))));
    return ordered(
        "description",
        description,
        "content",
        Map.of(PROBLEM_JSON, ordered("schema", ref("ApiV2Problem"), "examples", examples)));
  }

  private static Map<String, Object> problemContent(String description, int status, String code) {
    Map<String, Object> media = new LinkedHashMap<>();
    media.put("schema", ref("ApiV2Problem"));
    media.put(
        "example",
        ordered("title", description, "status", status, "code", code, "detail", description));
    return Map.of(PROBLEM_JSON, media);
  }

  private static Map<String, Object> standardHeaders() {
    return ordered(
        "ETag",
        ordered(
            "description",
            "Strong version identifier for conditional resource access replacement.",
            "schema",
            ordered("type", "string", "pattern", "^\\\"[0-9]+\\\"$")),
        "RateLimitLimit",
        integerHeader("Configured request limit for the shortest throttle interval."),
        "RateLimitRemaining",
        integerHeader("Requests remaining in the shortest throttle interval."),
        "RateLimitWait",
        integerHeader("Minimum wait before another request, in milliseconds."),
        "RateLimitMinWait",
        integerHeader("Configured minimum interval between requests, in milliseconds."));
  }

  private static Map<String, Object> integerHeader(String description) {
    return ordered(
        "description", description, "schema", ordered("type", "integer", "format", "int64"));
  }

  private static Map<String, Object> rateLimitHeaders() {
    Map<String, Object> headers =
        ordered(
            "X-Rate-Limit-Limit",
            headerRef("RateLimitLimit"),
            "X-Rate-Limit-Remaining",
            headerRef("RateLimitRemaining"),
            "X-Rate-Limit-WaitTimeMillis",
            headerRef("RateLimitWait"),
            "X-Rate-Limit-MinWaitIntervalMillis",
            headerRef("RateLimitMinWait"));
    for (String interval : List.of("quarter_min", "hour", "day")) {
      headers.put("X-Rate-Limit-Limit-" + interval, headerRef("RateLimitLimit"));
      headers.put("X-Rate-Limit-Remaining-" + interval, headerRef("RateLimitRemaining"));
      headers.put(
          "X-Rate-Limit-WaitTimeTillNextRequestMillis-" + interval, headerRef("RateLimitWait"));
    }
    return headers;
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

  static Map<String, Object> responseRef(String name) {
    return Map.of("$ref", "#/components/responses/" + name);
  }

  static Map<String, Object> headerRef(String name) {
    return Map.of("$ref", "#/components/headers/" + name);
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

  private static String operationId(ResourceOperation operation, String component) {
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
    return prefix + component;
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
      case DELETE -> (registration.deleteIsSoft() ? "Archive one " : "Delete one ") + resource;
      case BULK_DELETE ->
          (registration.deleteIsSoft() ? "Archive matching " : "Delete matching ") + resource;
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
          registration.deleteIsSoft()
              ? "Archives one "
                  + resource
                  + " and returns the archived document. Repeating ordinary DELETE is a no-op."
                  + (registration.supportsPermanentDelete()
                      ? " The explicit permanent=true option permanently removes the resource for"
                          + " a direct sysadmin and returns no document."
                      : "")
              : "Deletes one " + resource + " and returns the deleted document.";
      case BULK_DELETE ->
          (registration.deleteIsSoft() ? "Archives every " : "Deletes every ")
              + resource
              + " matching the required filter. The operation is handled as one service-layer"
              + " mutation; resource-specific limits are reported with status 422.";
    };
  }

  public static String operatorToken(Operator operator) {
    return ApiV2OpenApiQueryParameters.operatorToken(operator);
  }

  static String escapeEcmaRegex(String value) {
    return ApiV2OpenApiResourceDocumentBuilder.escapeEcmaRegex(value);
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }

  private static Object withNullableReferenceProperties(Object schema) {
    return ApiV2OpenApiResourceDocumentBuilder.withNullableReferenceProperties(schema);
  }

  static boolean isNullableReference(Object property) {
    return ApiV2OpenApiResourceDocumentBuilder.isNullableReference(property);
  }
}
