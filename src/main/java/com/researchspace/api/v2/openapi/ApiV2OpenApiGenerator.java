package com.researchspace.api.v2.openapi;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessDocumentation.AuthenticationRequirement;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.AccessPolicySchema;
import com.researchspace.model.collection.CollectionDescription.FieldSchema;
import com.researchspace.model.collection.CollectionDescription.RelationshipCardinality;
import com.researchspace.model.collection.CollectionDescription.RelationshipSchema;
import com.researchspace.model.collection.CollectionDescription.ResourceSchema;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import com.researchspace.model.collection.RelationshipInputForm;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ApiV2OpenApiGenerator {

  private static final String JSON = "application/json";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final Map<ResourceOperation, String> METHODS =
      Map.of(
          ResourceOperation.LIST, "get",
          ResourceOperation.COUNT, "get",
          ResourceOperation.READ, "get",
          ResourceOperation.CREATE, "post",
          ResourceOperation.BULK_CREATE, "post",
          ResourceOperation.UPDATE, "patch",
          ResourceOperation.BULK_UPDATE, "patch",
          ResourceOperation.DELETE, "delete",
          ResourceOperation.BULK_DELETE, "delete");

  private final ApiV2ResourceCatalog catalog;
  private final String title;
  private final String version;
  private final String serverUrl;

  public ApiV2OpenApiGenerator(ApiV2ResourceCatalog catalog, String title, String version) {
    this(catalog, title, version, "/");
  }

  public ApiV2OpenApiGenerator(
      ApiV2ResourceCatalog catalog, String title, String version, String serverUrl) {
    this.catalog = Objects.requireNonNull(catalog, "Resource catalog");
    this.title = requireText(title, "OpenAPI title");
    this.version = requireText(version, "OpenAPI version");
    this.serverUrl = requireText(serverUrl, "OpenAPI server URL");
  }

  public Map<String, Object> generate() {
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
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    addStandardSchemas(schemas);

    catalog
        .allSchemas()
        .forEach(
            description ->
                addSchemas(
                    schemas,
                    description.schema(),
                    componentNames,
                    routableNames.contains(description.resourceName())));
    catalog
        .routableResources()
        .forEach(resource -> addPaths(paths, resource, componentNames, resourceSchemas));

    Map<String, Object> components = new LinkedHashMap<>();
    components.put("schemas", schemas);
    components.put("responses", standardResponses());
    components.put("headers", standardHeaders());
    components.put("securitySchemes", securitySchemes());

    Map<String, Object> document = new LinkedHashMap<>();
    document.put("openapi", "3.1.0");
    document.put(
        "info",
        ordered(
            "title",
            title,
            "version",
            version,
            "description",
            "Generated contract for RSpace REST API v2. Resource operations and schemas are "
                + "derived from the same definitions used at runtime."));
    document.put(
        "servers",
        List.of(
            ordered(
                "url",
                serverUrl,
                "description",
                "RSpace deployment root for this generated document.")));
    List<Map<String, Object>> tags = new ArrayList<>();
    catalog
        .routableResources()
        .forEach(
            resource ->
                tags.add(
                    ordered(
                        "name",
                        resource.resourceName(),
                        "description",
                        "Operations on the " + resource.resourceName() + " collection.")));
    document.put("tags", tags);
    document.put("paths", paths);
    document.put("components", components);
    ApiV2OpenApiValidator.validate(document);
    return document;
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
      @SuppressWarnings("unchecked")
      Map<String, Object> pathItem =
          (Map<String, Object>) paths.computeIfAbsent(path, ignored -> new LinkedHashMap<>());
      String method = METHODS.get(operation);
      if (pathItem.containsKey(method)) {
        throw new IllegalArgumentException("Duplicate OpenAPI operation " + method + " " + path);
      }
      pathItem.put(method, operation(resource, operation, component, resourceSchemas));
    }
    addAuditPaths(paths, resource, component);
  }

  private void addAuditPaths(
      Map<String, Object> paths, ApiV2ResourceRegistration<?, ?> resource, String component) {
    String itemAuditPath = "/api/v2/" + resource.resourceName() + "/{id}/audit";
    String countPath = itemAuditPath + "/count";
    paths.put(itemAuditPath, ordered("get", auditOperation(resource, component, false)));
    paths.put(countPath, ordered("get", auditOperation(resource, component, true)));
  }

  private Map<String, Object> auditOperation(
      ApiV2ResourceRegistration<?, ?> resource, String component, boolean count) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("operationId", (count ? "count" : "list") + component + "AuditEvents");
    result.put(
        "summary",
        count
            ? "Count audit events for one " + resource.resourceName() + " resource"
            : "List audit events for one " + resource.resourceName() + " resource");
    result.put(
        "description",
        "Returns events from the existing RSpace audit trail. The caller must be authenticated "
            + "and must be able to read the resource. A resource without audit metadata returns "
            + "an empty result. The search range is limited to 183 days.");
    result.put("tags", List.of(resource.resourceName()));
    result.put("security", List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of())));
    result.put(
        "x-rspace-access",
        ordered(
            "description",
            "Authenticated callers who can read the resource.",
            "denialReasonCodes",
            List.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN)));
    result.put("parameters", auditParameters(resource.description().schema(), count));
    result.put("responses", auditResponses(count));
    result.put("x-rspace-operation", count ? "AUDIT_COUNT" : "AUDIT_LIST");
    return result;
  }

  private static List<Map<String, Object>> auditParameters(ResourceSchema schema, boolean count) {
    List<Map<String, Object>> parameters = new ArrayList<>();
    FieldSchema id = field(schema, schema.idField());
    parameters.add(
        parameter("id", "path", true, scalarSchema(id.type(), false), "Resource identifier."));
    parameters.add(
        parameter(
            "dateFrom",
            "query",
            false,
            ordered("type", "string", "format", "date-time"),
            "Earliest event time. The server limits the range to 183 days."));
    parameters.add(
        parameter(
            "dateTo",
            "query",
            false,
            ordered("type", "string", "format", "date-time"),
            "Latest event time."));
    Map<String, Object> actions =
        parameter(
            "actions",
            "query",
            false,
            ordered(
                "type",
                "array",
                "items",
                ordered(
                    "type",
                    "string",
                    "enum",
                    java.util.Arrays.stream(AuditAction.values()).map(Enum::name).toList())),
            "Audit actions to include.");
    actions.put("style", "form");
    actions.put("explode", true);
    parameters.add(actions);
    if (!count) {
      parameters.add(
          parameter(
              "page",
              "query",
              false,
              ordered("type", "integer", "minimum", 1, "default", 1),
              "One-based page number."));
      parameters.add(
          parameter(
              "limit",
              "query",
              false,
              ordered(
                  "type",
                  "integer",
                  "minimum",
                  1,
                  "maximum",
                  CollectionQueryLimits.MAX_PAGE_SIZE,
                  "default",
                  20),
              "Maximum events per page."));
    }
    return parameters;
  }

  private static Map<String, Object> auditResponses(boolean count) {
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put(
        "200",
        ordered(
            "description",
            count ? "Audit event count." : "Audit event page.",
            "headers",
            rateLimitHeaders(),
            "content",
            Map.of(
                JSON,
                ordered(
                    "schema",
                    count ? ref("ApiV2CountResult") : listResult(ref("ApiV2AuditEvent"))))));
    responses.put("400", responseRef("BadRequest"));
    responses.put("401", responseRef("Unauthenticated"));
    responses.put("403", responseRef("Forbidden"));
    responses.put("404", responseRef("NotFound"));
    responses.put("406", responseRef("NotAcceptable"));
    responses.put("429", responseRef("TooManyRequests"));
    responses.put("500", responseRef("UnexpectedError"));
    return responses;
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
        documentation.summary() == null
            ? summary(operation, resource.resourceName())
            : documentation.summary());
    result.put(
        "description",
        documentation.description() == null
            ? description(operation, resource.resourceName())
            : documentation.description());
    result.put(
        "tags",
        documentation.tags().isEmpty() ? List.of(resource.resourceName()) : documentation.tags());
    if (documentation.deprecated()) {
      result.put("deprecated", true);
    }

    AccessDocumentation access = access(schema.access(), operation);
    applyAccess(result, access);
    List<Map<String, Object>> parameters = parameters(operation, schema, resourceSchemas);
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
    return switch (operation) {
      case LIST, COUNT, READ -> access.readAccess();
      case CREATE, BULK_CREATE -> access.createAccess();
      case UPDATE, BULK_UPDATE -> access.updateAccess();
      case DELETE, BULK_DELETE -> access.deleteAccess();
    };
  }

  private static void applyAccess(Map<String, Object> operation, AccessDocumentation access) {
    operation.put(
        "security",
        access.authenticationRequirement() == AuthenticationRequirement.PUBLIC
            ? List.of()
            : List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of())));
    operation.put(
        "x-rspace-access",
        ordered(
            "description", access.description(), "denialReasonCodes", access.denialReasonCodes()));
  }

  private static List<Map<String, Object>> parameters(
      ResourceOperation operation,
      ResourceSchema schema,
      Map<String, ResourceSchema> resourceSchemas) {
    List<Map<String, Object>> parameters = new ArrayList<>();
    if (operation == ResourceOperation.READ
        || operation == ResourceOperation.UPDATE
        || operation == ResourceOperation.DELETE) {
      FieldSchema id = field(schema, schema.idField());
      parameters.add(
          parameter("id", "path", true, scalarSchema(id.type(), false), "Resource identifier."));
    }
    if (operation == ResourceOperation.LIST) {
      parameters.add(where(schema, false, resourceSchemas));
      Map<String, Object> sort =
          parameter(
              "sort",
              "query",
              false,
              ordered("type", "string"),
              "Comma-separated sortable fields; prefix descending fields with '-'. At most "
                  + CollectionQueryLimits.MAX_SORT_FIELDS
                  + " fields.");
      sort.put(
          "x-rspace-sort",
          ordered(
              "fields",
              schema.fields().stream()
                  .filter(FieldSchema::sortable)
                  .map(FieldSchema::name)
                  .toList(),
              "default",
              schema.defaultSort().stream()
                  .map(value -> (value.ascending() ? "" : "-") + value.field())
                  .toList(),
              "maximumFields",
              CollectionQueryLimits.MAX_SORT_FIELDS));
      parameters.add(sort);
      parameters.add(
          parameter(
              "page",
              "query",
              false,
              ordered("type", "integer", "minimum", 1, "default", 1),
              "One-based page number."));
      parameters.add(
          parameter(
              "limit",
              "query",
              false,
              ordered(
                  "type",
                  "integer",
                  "minimum",
                  1,
                  "maximum",
                  CollectionQueryLimits.MAX_PAGE_SIZE,
                  "default",
                  20),
              "Maximum documents per page."));
    }
    if (operation == ResourceOperation.COUNT
        || operation == ResourceOperation.BULK_UPDATE
        || operation == ResourceOperation.BULK_DELETE) {
      parameters.add(where(schema, operation != ResourceOperation.COUNT, resourceSchemas));
    }
    if (operation == ResourceOperation.LIST || operation == ResourceOperation.READ) {
      parameters.add(
          parameter(
              "depth",
              "query",
              false,
              ordered(
                  "type",
                  "integer",
                  "minimum",
                  0,
                  "maximum",
                  CollectionQueryLimits.MAX_RELATIONSHIP_DEPTH,
                  "default",
                  0),
              "Relationship expansion depth. At depth 0, the response is the canonical update"
                  + " reference object and value is the raw target ID. At greater depth, value is"
                  + " the expanded read document; relationTo and globalId remain stable."));
      parameters.add(
          fieldset("fields", schema, resourceSchemas, "Fields to include by resource type."));
      parameters.add(
          fieldset("exclude", schema, resourceSchemas, "Fields to exclude by resource type."));
    }
    return parameters;
  }

  private static Map<String, Object> where(
      ResourceSchema schema, boolean required, Map<String, ResourceSchema> resourceSchemas) {
    Map<String, Object> parameter =
        parameter(
            "where",
            "query",
            required,
            ordered("type", "string", "maxLength", CollectionQueryLimits.MAX_WHERE_LENGTH),
            "Bounded RSQL filter expression. Available selectors and operators are provided in"
                + " x-rspace-filter.");
    parameter.put("x-rspace-filter", filterExtension(schema, resourceSchemas));
    return parameter;
  }

  private static Map<String, Object> filterExtension(
      ResourceSchema resource, Map<String, ResourceSchema> resourceSchemas) {
    Map<String, Object> selectors = new LinkedHashMap<>();
    resource
        .filters()
        .forEach(
            filter ->
                selectors.put(
                    filter.selector(),
                    ordered(
                        "schema",
                        filterSchema(filter.selector(), resource, resourceSchemas),
                        "operators",
                        filter.operators().stream()
                            .map(ApiV2OpenApiGenerator::operatorToken)
                            .toList(),
                        "wildcards",
                        filter.supportsWildcards())));
    return ordered(
        "maximumComparisons",
        CollectionQueryLimits.MAX_COMPARISONS,
        "maximumLikeComparisons",
        CollectionQueryLimits.MAX_LIKE_COMPARISONS,
        "maximumNesting",
        CollectionQueryLimits.MAX_FILTER_NESTING,
        "maximumArguments",
        CollectionQueryLimits.MAX_ARGUMENTS,
        "selectors",
        selectors);
  }

  private static Map<String, Object> filterSchema(
      String selector, ResourceSchema resource, Map<String, ResourceSchema> resourceSchemas) {
    java.util.Optional<FieldSchema> field =
        resource.fields().stream().filter(value -> value.name().equals(selector)).findFirst();
    if (field.isPresent()) {
      return scalarSchema(field.get().type(), field.get().nullable());
    }
    for (RelationshipSchema relationship : resource.relationships()) {
      if (selector.equals(relationship.name())) {
        return ordered("type", "string", "description", "RSpace global identifier.");
      }
      if (selector.equals(relationship.name() + ".relationTo")) {
        return ordered("type", "string", "enum", relationship.targetResources());
      }
      if (selector.equals(relationship.name() + ".value")) {
        List<Map<String, Object>> targetIds =
            relationship.targetResources().stream()
                .map(resourceSchemas::get)
                .filter(Objects::nonNull)
                .map(target -> scalarSchema(field(target, target.idField()).type(), false))
                .distinct()
                .toList();
        return targetIds.size() == 1 ? targetIds.get(0) : ordered("oneOf", targetIds);
      }
    }
    return ordered("type", "string");
  }

  private static Map<String, Object> fieldset(
      String name,
      ResourceSchema resource,
      Map<String, ResourceSchema> resourceSchemas,
      String description) {
    Set<String> reachable = new LinkedHashSet<>();
    reachable.add(resource.name());
    resource
        .relationships()
        .forEach(relationship -> reachable.addAll(relationship.targetResources()));
    Map<String, Object> properties = new LinkedHashMap<>();
    Map<String, Object> allowedFields = new LinkedHashMap<>();
    reachable.forEach(
        resourceName -> {
          ResourceSchema target = resourceSchemas.get(resourceName);
          if (target == null) {
            return;
          }
          List<String> fieldNames = new ArrayList<>();
          target.fields().stream().map(FieldSchema::name).forEach(fieldNames::add);
          target.relationships().stream().map(RelationshipSchema::name).forEach(fieldNames::add);
          allowedFields.put(resourceName, fieldNames);
          properties.put(resourceName, ordered("type", "string"));
        });
    Map<String, Object> parameter =
        parameter(
            name,
            "query",
            false,
            ordered("type", "object", "additionalProperties", false, "properties", properties),
            description
                + " Uses square-bracket keys and comma-separated nested field paths, for example "
                + name
                + "["
                + resource.name()
                + "]=id,name.");
    parameter.put("style", "deepObject");
    parameter.put("explode", true);
    parameter.put("x-rspace-comma-separated-values", true);
    parameter.put("x-rspace-allowed-fields", allowedFields);
    parameter.put("x-rspace-mutually-exclusive-with", name.equals("fields") ? "exclude" : "fields");
    return parameter;
  }

  private static Map<String, Object> parameter(
      String name,
      String location,
      boolean required,
      Map<String, Object> schema,
      String description) {
    return ordered(
        "name",
        name,
        "in",
        location,
        "required",
        required,
        "description",
        description,
        "schema",
        schema);
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
                .responseDescriptions()
                .getOrDefault(
                    Integer.valueOf(successStatus),
                    "Successful " + operation.name().toLowerCase(Locale.ROOT) + "."),
            "headers",
            rateLimitHeaders(),
            "content",
            Map.of(JSON, successMedia)));
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
    resource
        .errorResponses(operation)
        .forEach(
            (status, code) ->
                responses.put(
                    String.valueOf(status),
                    problemResponse(
                        documentation
                            .responseDescriptions()
                            .getOrDefault(status, "Operation failed: " + code),
                        status,
                        code)));
    documentation
        .responseDescriptions()
        .forEach(
            (status, description) -> {
              Object existing = responses.get(String.valueOf(status));
              if (existing instanceof Map<?, ?> response) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mutable = new LinkedHashMap<>((Map<String, Object>) response);
                mutable.put("description", description);
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
    String component = names.get(resource.name());
    components.put(component + "Reference", referenceSchema(resource));
    components.put(component + "Read", documentSchema(resource, names, null));
    if (routable) {
      components.put(component + "Create", documentSchema(resource, names, WriteOperation.CREATE));
      components.put(component + "Update", documentSchema(resource, names, WriteOperation.UPDATE));
    }
  }

  private static void addStandardSchemas(Map<String, Object> schemas) {
    Map<String, Object> invalidParam =
        ordered(
            "type",
            "object",
            "required",
            List.of("name", "reason"),
            "properties",
            ordered("name", ordered("type", "string"), "reason", ordered("type", "string")));
    schemas.put(
        "ApiV2Problem",
        ordered(
            "type",
            "object",
            "required",
            List.of("title", "status", "code"),
            "properties",
            ordered(
                "title",
                ordered("type", "string"),
                "status",
                ordered("type", "integer"),
                "code",
                ordered("type", "string"),
                "detail",
                ordered("type", List.of("string", "null")),
                "invalidParams",
                ordered("type", List.of("array", "null"), "items", invalidParam))));
    schemas.put(
        "ApiV2CountResult",
        ordered(
            "type",
            "object",
            "required",
            List.of("totalDocs"),
            "properties",
            Map.of("totalDocs", ordered("type", "integer", "format", "int64"))));
    schemas.put(
        "ApiV2AuditEvent",
        ordered(
            "type",
            "object",
            "required",
            List.of("timestamp", "username", "domain", "action", "payload"),
            "properties",
            ordered(
                "timestamp",
                ordered("type", "string", "format", "date-time"),
                "username",
                ordered("type", "string"),
                "fullName",
                ordered("type", List.of("string", "null")),
                "domain",
                ordered(
                    "type",
                    "string",
                    "enum",
                    java.util.Arrays.stream(AuditDomain.values()).map(Enum::name).toList()),
                "action",
                ordered(
                    "type",
                    "string",
                    "enum",
                    java.util.Arrays.stream(AuditAction.values()).map(Enum::name).toList()),
                "description",
                ordered("type", List.of("string", "null")),
                "payload",
                ordered("type", "object", "additionalProperties", true))));
    schemas.put(
        "ApiV2BulkError",
        ordered(
            "type",
            "object",
            "required",
            List.of("code"),
            "properties",
            ordered(
                "id",
                ordered("type", List.of("string", "null")),
                "code",
                ordered("type", "string"),
                "detail",
                ordered("type", List.of("string", "null")))));
  }

  private static Map<String, Object> referenceSchema(ResourceSchema resource) {
    FieldSchema id = field(resource, resource.idField());
    return ordered(
        "type",
        "object",
        "required",
        List.of("relationTo", "value"),
        "properties",
        ordered(
            "relationTo",
            ordered("type", "string", "const", resource.name()),
            "value",
            scalarSchema(id.type(), false)));
  }

  private static Map<String, Object> documentSchema(
      ResourceSchema resource, Map<String, String> names, WriteOperation writeOperation) {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (FieldSchema field : resource.fields()) {
      if (writeOperation != null && !field.writeOperations().contains(writeOperation)) {
        continue;
      }
      Map<String, Object> property = scalarSchema(field.type(), field.nullable());
      applyDocumentation(property, field.openApi());
      property.put(
          "x-rspace-access",
          accessExtension(
              writeOperation == null
                  ? field.readAccess()
                  : writeOperation == WriteOperation.CREATE
                      ? field.createAccess()
                      : field.updateAccess()));
      if (writeOperation == null && field.readOnly()) {
        property.put("readOnly", true);
      }
      if (field.hasDefaultValue()) {
        property.put("x-rspace-has-dynamic-default", true);
      }
      properties.put(field.name(), property);
      if ((writeOperation == WriteOperation.CREATE && field.requiredOnCreate())
          || (writeOperation == null && field.name().equals(resource.idField()))) {
        required.add(field.name());
      }
    }
    for (RelationshipSchema relationship : resource.relationships()) {
      if (writeOperation != null && !relationship.writeOperations().contains(writeOperation)) {
        continue;
      }
      Map<String, Object> property =
          new LinkedHashMap<>(
              writeOperation == null
                  ? relationshipOutput(relationship, names)
                  : relationshipInput(relationship, writeOperation, names));
      applyDocumentation(property, relationship.openApi());
      property.put(
          "x-rspace-access",
          accessExtension(
              writeOperation == null ? relationship.readAccess() : relationship.writeAccess()));
      properties.put(relationship.name(), nullable(property, relationship.nullable()));
      if (writeOperation == WriteOperation.CREATE && relationship.requiredOnCreate()) {
        required.add(relationship.name());
      }
    }
    Map<String, Object> schema =
        ordered("type", "object", "additionalProperties", false, "properties", properties);
    if (!required.isEmpty()) {
      schema.put("required", required);
    }
    schema.put(
        "x-rspace-access",
        accessExtension(
            writeOperation == null
                ? resource.access().readAccess()
                : writeOperation == WriteOperation.CREATE
                    ? resource.access().createAccess()
                    : resource.access().updateAccess()));
    return schema;
  }

  private static Map<String, Object> relationshipOutput(
      RelationshipSchema relationship, Map<String, String> names) {
    List<Map<String, Object>> variants = new ArrayList<>();
    relationship
        .targetResources()
        .forEach(
            target -> {
              String prefix = relationship.globalIdPrefixesByTarget().get(target);
              variants.add(
                  prefix == null
                      ? ref(names.get(target) + "Reference")
                      : globalReferenceSchema(names.get(target) + "Reference", prefix));
              variants.add(
                  expandedReferenceSchema(target, ref(names.get(target) + "Read"), prefix));
            });
    Map<String, Object> value = ordered("oneOf", variants);
    return relationship.cardinality() == RelationshipCardinality.TO_MANY
        ? ordered("type", "array", "items", value)
        : value;
  }

  private static Map<String, Object> relationshipInput(
      RelationshipSchema relationship, WriteOperation operation, Map<String, String> names) {
    List<Map<String, Object>> variants = new ArrayList<>();
    Set<RelationshipInputForm> forms = relationship.inputForms().getOrDefault(operation, Set.of());
    if (forms.contains(RelationshipInputForm.GLOBAL_ID)) {
      String alternatives =
          relationship.globalIdPrefixesByTarget().values().stream()
              .map(ApiV2OpenApiGenerator::escapeEcmaRegex)
              .collect(java.util.stream.Collectors.joining("|"));
      variants.add(
          ordered(
              "type",
              "string",
              "pattern",
              "^(?:" + alternatives + ")\\d+$",
              "example",
              relationship.globalIdPrefixesByTarget().values().iterator().next() + "1",
              "description",
              "RSpace global identifier. Permitted prefixes: "
                  + String.join(", ", relationship.globalIdPrefixesByTarget().values())
                  + "."));
    }
    if (forms.contains(RelationshipInputForm.OBJECT)) {
      relationship
          .targetResources()
          .forEach(
              target -> {
                String referenceComponent = names.get(target) + "Reference";
                String prefix = relationship.globalIdPrefixesByTarget().get(target);
                variants.add(
                    prefix == null
                        ? ref(referenceComponent)
                        : globalInputReferenceSchema(referenceComponent, prefix));
              });
    }
    return variants.size() == 1 ? variants.get(0) : ordered("oneOf", variants);
  }

  private static Map<String, Object> globalReferenceSchema(
      String referenceComponent, String prefix) {
    return ordered(
        "allOf",
        List.of(
            ref(referenceComponent),
            ordered(
                "type",
                "object",
                "required",
                List.of("globalId"),
                "properties",
                ordered("globalId", globalIdSchema(prefix)))));
  }

  private static Map<String, Object> globalInputReferenceSchema(
      String referenceComponent, String prefix) {
    return ordered(
        "allOf",
        List.of(
            ref(referenceComponent),
            ordered("type", "object", "properties", ordered("globalId", globalIdSchema(prefix)))));
  }

  private static Map<String, Object> expandedReferenceSchema(
      String target, Map<String, Object> value, String prefix) {
    List<String> required = new ArrayList<>(List.of("relationTo", "value"));
    Map<String, Object> properties =
        ordered("relationTo", ordered("type", "string", "const", target), "value", value);
    if (prefix != null) {
      required.add("globalId");
      properties.put("globalId", globalIdSchema(prefix));
    }
    return ordered("type", "object", "required", required, "properties", properties);
  }

  private static Map<String, Object> globalIdSchema(String prefix) {
    return ordered(
        "type",
        "string",
        "pattern",
        "^" + escapeEcmaRegex(prefix) + "\\d+$",
        "example",
        prefix + "1");
  }

  private static Map<String, Object> nullable(Map<String, Object> schema, boolean nullable) {
    return nullable ? ordered("anyOf", List.of(schema, ordered("type", "null"))) : schema;
  }

  private static Map<String, Object> scalarSchema(
      CollectionFieldType.Schema type, boolean nullable) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", nullable ? List.of(type.jsonType(), "null") : type.jsonType());
    if (type.format() != null) {
      schema.put("format", type.format());
    }
    if (type.maxLength() != null) {
      schema.put("maxLength", type.maxLength());
    }
    return schema;
  }

  private static void applyDocumentation(
      Map<String, Object> schema, OpenApiSchemaDocumentation documentation) {
    putIfNotNull(schema, "title", documentation.title());
    putIfNotNull(schema, "description", documentation.description());
    if (documentation.example() != null) {
      schema.put("example", schemaLiteral(schema, documentation.example()));
    }
    if (!documentation.additionalExamples().isEmpty()) {
      schema.put(
          "examples",
          documentation.additionalExamples().stream()
              .map(value -> schemaLiteral(schema, value))
              .toList());
    }
    if (documentation.defaultValue() != null) {
      schema.put("default", schemaLiteral(schema, documentation.defaultValue()));
    }
    putIfNotNull(schema, "pattern", documentation.pattern());
    putIfNotNull(schema, "format", documentation.format());
    putIfNotNull(schema, "minLength", documentation.minLength());
    if (documentation.minimum() != null) {
      schema.put("minimum", new BigDecimal(documentation.minimum()));
    }
    if (documentation.maximum() != null) {
      schema.put("maximum", new BigDecimal(documentation.maximum()));
    }
    if (!documentation.enumValues().isEmpty()) {
      schema.put(
          "enum",
          documentation.enumValues().stream().map(value -> schemaLiteral(schema, value)).toList());
    }
    if (documentation.deprecated()) {
      schema.put("deprecated", true);
    }
    schema.putAll(documentation.extensions());
  }

  private static Object schemaLiteral(Map<String, Object> schema, String value) {
    Object declaredType = schema.get("type");
    String type =
        declaredType instanceof String scalar
            ? scalar
            : declaredType instanceof List<?> alternatives
                ? alternatives.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(candidate -> !candidate.equals("null"))
                    .findFirst()
                    .orElse("string")
                : "string";
    try {
      return switch (type) {
        case "integer" -> Long.valueOf(value);
        case "number" -> new BigDecimal(value);
        case "boolean" -> {
          if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Boolean schema value must be true or false");
          }
          yield Boolean.valueOf(value);
        }
        default -> value;
      };
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "OpenAPI value '" + value + "' is not a valid " + type, ex);
    }
  }

  private static void putIfNotNull(Map<String, Object> target, String name, Object value) {
    if (value != null) {
      target.put(name, value);
    }
  }

  private static Map<String, Object> accessExtension(AccessDocumentation access) {
    return ordered(
        "description",
        access.description(),
        "authentication",
        access.authenticationRequirement().name(),
        "denialReasonCodes",
        access.denialReasonCodes());
  }

  private static Map<String, Object> listResult(Map<String, Object> item) {
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

  private static Map<String, Object> bulkResult(Map<String, Object> item) {
    return ordered(
        "type",
        "object",
        "required",
        List.of("docs", "errors"),
        "properties",
        ordered(
            "docs",
            ordered("type", "array", "items", item),
            "errors",
            ordered("type", "array", "items", ref("ApiV2BulkError"))));
  }

  private static Map<String, Object> standardResponses() {
    return ordered(
        "BadRequest",
        problemResponse("The request is invalid.", 400, "BAD_REQUEST"),
        "Unauthenticated",
        problemResponse("Authentication is required.", 401, "AUTHENTICATION_REQUIRED"),
        "Forbidden",
        problemResponse("The caller is not authorized.", 403, "FORBIDDEN"),
        "NotFound",
        problemResponse("The resource was not found or is not visible.", 404, "NOT_FOUND"),
        "NotAcceptable",
        problemResponse(
            "No acceptable response representation is available.", 406, "NOT_ACCEPTABLE"),
        "UnsupportedMediaType",
        problemResponse("The request media type is unsupported.", 415, "UNSUPPORTED_MEDIA_TYPE"),
        "BulkLimit",
        problemResponse("The bulk operation exceeds the configured limit.", 422, "BULK_LIMIT"),
        "TooManyRequests",
        withHeaders(
            problemResponse("The request was throttled.", 429, "TOO_MANY_REQUESTS"),
            rateLimitHeaders()),
        "UnexpectedError",
        problemResponse("An unexpected server error occurred.", 500, "INTERNAL_SERVER_ERROR"));
  }

  private static Map<String, Object> problemResponse(String description, int status, String code) {
    return ordered(
        "description", description, "content", problemContent(description, status, code));
  }

  private static Map<String, Object> problemContent(String description, int status, String code) {
    Map<String, Object> media = new LinkedHashMap<>();
    media.put("schema", ref("ApiV2Problem"));
    media.put(
        "example",
        ordered("title", description, "status", status, "code", code, "detail", description));
    return Map.of(PROBLEM_JSON, media);
  }

  private static Map<String, Object> withHeaders(
      Map<String, Object> response, Map<String, Object> headers) {
    response.put("headers", headers);
    return response;
  }

  private static Map<String, Object> standardHeaders() {
    return ordered(
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
        ordered("type", "http", "scheme", "bearer"));
  }

  private static Map<String, Object> responseRef(String name) {
    return Map.of("$ref", "#/components/responses/" + name);
  }

  private static Map<String, Object> headerRef(String name) {
    return Map.of("$ref", "#/components/headers/" + name);
  }

  private static Map<String, Object> ref(String name) {
    return Map.of("$ref", "#/components/schemas/" + name);
  }

  private static FieldSchema field(ResourceSchema resource, String name) {
    return resource.fields().stream()
        .filter(field -> field.name().equals(name))
        .findFirst()
        .orElseThrow();
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

  private static String summary(ResourceOperation operation, String resource) {
    return switch (operation) {
      case LIST -> "List " + resource;
      case COUNT -> "Count " + resource;
      case READ -> "Get one " + resource;
      case CREATE -> "Create one " + resource;
      case BULK_CREATE -> "Create many " + resource;
      case UPDATE -> "Update one " + resource;
      case BULK_UPDATE -> "Update matching " + resource;
      case DELETE -> "Delete one " + resource;
      case BULK_DELETE -> "Delete matching " + resource;
    };
  }

  private static String description(ResourceOperation operation, String resource) {
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
      case DELETE -> "Deletes one " + resource + " and returns the deleted document.";
      case BULK_DELETE ->
          "Deletes every "
              + resource
              + " matching the required filter. The operation is handled as one service-layer"
              + " mutation; resource-specific limits are reported with status 422.";
    };
  }

  private static String operatorToken(CollectionDescription.Operator operator) {
    return switch (operator) {
      case EQUAL -> "==";
      case NOT_EQUAL -> "!=";
      case GREATER_THAN -> "=gt=";
      case GREATER_THAN_OR_EQUAL -> "=ge=";
      case LESS_THAN -> "=lt=";
      case LESS_THAN_OR_EQUAL -> "=le=";
      case IN -> "=in=";
      case NOT_IN -> "=out=";
      case CONTAINS -> "=contains=";
      case LIKE -> "=like=";
      case EXISTS -> "=exists=";
    };
  }

  private static String escapeEcmaRegex(String value) {
    StringBuilder escaped = new StringBuilder();
    for (char character : value.toCharArray()) {
      if ("\\^$.*+?()[]{}|/".indexOf(character) >= 0) {
        escaped.append('\\');
      }
      escaped.append(character);
    }
    return escaped.toString();
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static <T> Map<String, T> ordered(Object... entries) {
    if (entries.length % 2 != 0) {
      throw new IllegalArgumentException("Ordered map entries must be key/value pairs");
    }
    Map<String, T> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      result.put((String) entries[i], (T) entries[i + 1]);
    }
    return result;
  }
}
