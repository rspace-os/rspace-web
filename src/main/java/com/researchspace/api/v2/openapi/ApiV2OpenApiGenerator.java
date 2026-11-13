package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.addResourceAccessSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.applyRegisteredAccessFieldSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.addSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.addStandardSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.withNullableReferenceProperties;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.standardHeaders;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.standardResponses;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiRuntimeFieldDocs.addRuntimeFieldsPath;

import com.researchspace.api.v2.model.ApiV2RuntimeFieldCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.ResourceSchema;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ApiV2OpenApiGenerator {
  static final List<Map<String, List<String>>> AUTHENTICATED_SECURITY =
      List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of()));

  private final ApiV2ResourceCatalog catalog;
  private final String title;
  private final String version;
  private final String serverUrl;
  private final ApiV2OpenApiQueryParameters queryParameters;
  private final ApiV2OpenApiOperations operations;

  public ApiV2OpenApiGenerator(ApiV2ResourceCatalog catalog, String title, String version) {
    this(catalog, title, version, "/");
  }

  public ApiV2OpenApiGenerator(
      ApiV2ResourceCatalog catalog, String title, String version, String serverUrl) {
    this.catalog = Objects.requireNonNull(catalog, "Resource catalog");
    this.title = ApiV2OpenApiNames.requireText(title, "OpenAPI title");
    this.version = ApiV2OpenApiNames.requireText(version, "OpenAPI version");
    this.serverUrl = ApiV2OpenApiNames.requireText(serverUrl, "OpenAPI server URL");
    this.queryParameters = new ApiV2OpenApiQueryParameters(catalog);
    this.operations = new ApiV2OpenApiOperations(queryParameters);
  }

  public OpenAPI generate() {
    Map<String, Object> paths = new LinkedHashMap<>();
    Map<String, Object> schemas = new LinkedHashMap<>();
    Map<String, String> componentNames = ApiV2OpenApiNames.componentNames(catalog.allSchemas());
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
      pathItem.put(method, operations.operation(resource, operation, component, resourceSchemas));
    }
    addRuntimeFieldsPath(paths, resource, catalog.runtimeFieldsOf(resource.resourceName()));
  }

  private void addResourceAccessPaths(
      Map<String, Object> paths, ApiV2ResourceRegistration<?, ?> resource, String component) {
    new ApiV2OpenApiResourceAccessDocs().addResourceAccessPaths(paths, resource, component);
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

  static String singularName(ApiV2ResourceRegistration<?, ?> resource) {
    return ApiV2OpenApiNames.singularName(resource);
  }
}
