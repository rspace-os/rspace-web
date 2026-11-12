package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceAccessDocs.addResourceAccessSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.addSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.addStandardSchemas;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.rateLimitsExtension;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.standardHeaders;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.standardResponses;

import com.researchspace.api.v2.model.ApiV2RuntimeFieldCatalog;
import com.researchspace.api.v2.openapi.ApiV2OpenApiText.Key;
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
  private final ApiV2OpenApiText text;
  private final String title;
  private final String version;
  private final String serverUrl;
  private final ApiV2OpenApiRuntimeFieldDocs runtimeFields;
  private final ApiV2OpenApiOperations operations;

  public ApiV2OpenApiGenerator(
      ApiV2ResourceCatalog catalog, ApiV2OpenApiText text, String title, String version) {
    this(catalog, text, title, version, "/");
  }

  public ApiV2OpenApiGenerator(
      ApiV2ResourceCatalog catalog,
      ApiV2OpenApiText text,
      String title,
      String version,
      String serverUrl) {
    this.catalog = Objects.requireNonNull(catalog, "Resource catalog");
    this.text = Objects.requireNonNull(text, "OpenAPI text");
    this.title = ApiV2OpenApiNames.requireText(title, "OpenAPI title");
    this.version = ApiV2OpenApiNames.requireText(version, "OpenAPI version");
    this.serverUrl = ApiV2OpenApiNames.requireText(serverUrl, "OpenAPI server URL");
    this.runtimeFields = new ApiV2OpenApiRuntimeFieldDocs(catalog, text);
    this.operations = new ApiV2OpenApiOperations(runtimeFields, text);
  }

  /** The resolver for the document text, shared with the annotated-endpoint merge. */
  ApiV2OpenApiText text() {
    return text;
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
    ApiV2OpenApiComponents referenced = new ApiV2OpenApiComponents();
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
                    routableNames.contains(description.resourceName()),
                    text));
    ApiV2OpenApiDeclaredRows.share(schemas, catalog.allSchemas(), componentNames);
    catalog
        .routableResources()
        .forEach(
            resource ->
                runtimeFields.applyRuntimeProjectionSchemas(
                    schemas, resource, componentNames.get(resource.resourceName())));
    catalog
        .routableResources()
        .forEach(
            resource ->
                addPaths(paths, resource, componentNames, resourceSchemas, schemas, referenced));

    schemas.replaceAll(
        (name, schema) -> ApiV2OpenApiSchemas.withNullableReferenceProperties(schema));
    ApiV2OpenApiAccessPolicies.share(schemas, referenced);
    ApiV2OpenApiAccessPolicies.share(paths, referenced);
    Map<String, Object> components = new LinkedHashMap<>();
    components.put("schemas", schemas);
    components.put("responses", standardResponses(text));
    components.put("headers", standardHeaders(text));
    components.put("securitySchemes", securitySchemes());
    referenced.addTo(components);

    return new OpenAPI()
        .openapi("3.1.0")
        .info(new Info().title(title).version(version).description(text.get(Key.INFO_DESCRIPTION)))
        .servers(List.of(new Server().url(serverUrl).description(text.get(Key.SERVER_DESCRIPTION))))
        .paths(Json31.mapper().convertValue(paths, Paths.class))
        .components(Json31.mapper().convertValue(components, Components.class))
        .extensions(Map.of("x-rspace-rate-limits", rateLimitsExtension(text)));
  }

  private void addPaths(
      Map<String, Object> paths,
      ApiV2ResourceRegistration<?, ?> resource,
      Map<String, String> componentNames,
      Map<String, ResourceSchema> resourceSchemas,
      Map<String, Object> schemas,
      ApiV2OpenApiComponents referenced) {
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
      pathItem.put(
          method,
          operations.operation(
              resource, operation, component, resourceSchemas, schemas, referenced));
    }
    runtimeFields.addRuntimeFieldsPath(paths, resource);
    ApiV2OpenApiAuditDocs.addAuditPaths(paths, resource, component, text, referenced);
    addResourceAccessPaths(paths, resource, component);
  }

  private void addResourceAccessPaths(
      Map<String, Object> paths, ApiV2ResourceRegistration<?, ?> resource, String component) {
    new ApiV2OpenApiResourceAccessDocs(text).addResourceAccessPaths(paths, resource, component);
  }

  private Map<String, Object> securitySchemes() {
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
            text.get(Key.SECURITY_BROWSER_SESSION)));
  }

  static String singularName(ApiV2ResourceRegistration<?, ?> resource) {
    return ApiV2OpenApiNames.singularName(resource);
  }
}
