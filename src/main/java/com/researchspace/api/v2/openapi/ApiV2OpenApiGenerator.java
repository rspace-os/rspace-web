package com.researchspace.api.v2.openapi;

import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.api.v2.model.ApiV2CountResult;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceAccessSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.booking.service.BookingConfigurationTarget;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessDocumentation.AuthenticationRequirement;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.AccessPolicySchema;
import com.researchspace.model.collection.CollectionDescription.FieldSchema;
import com.researchspace.model.collection.CollectionDescription.FilterSchema;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.RelationshipSchema;
import com.researchspace.model.collection.CollectionDescription.ResourceFieldSchema;
import com.researchspace.model.collection.CollectionDescription.ResourceSchema;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.FilterSelector;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import com.researchspace.model.collection.RelationshipInputForm;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.service.resourceaccess.ResourceAccessDocument;
import com.researchspace.service.resourceaccess.ResourceAccessGrant;
import com.researchspace.service.resourceaccess.ResourceGranteeDirectoryEntry;
import com.researchspace.service.resourceaccess.ResourceRoleSource;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    addCalendarSubscriptionSchemas(schemas);
    addResourceAccessSchemas(schemas);
    ApiV2OpenApiSchemas.schemaFor(BookingConfigurationTarget.class, schemas);

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
        .forEach(
            resource ->
                applyRegisteredAccessFieldSchemas(
                    schemas, resource, componentNames.get(resource.resourceName())));
    catalog
        .routableResources()
        .forEach(resource -> addPaths(paths, resource, componentNames, resourceSchemas));
    addCalendarSubscriptionPath(paths);
    addBookingDirectoryPaths(paths);

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
    tags.add(
        new Tag()
            .name("booking-calendar-subscriptions")
            .description("Manage one caller's bookable-item calendar subscription."));
    tags.add(
        new Tag()
            .name("booking-directories")
            .description("Search bounded Booking access principals and creation targets."));
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

  private static void addCalendarSubscriptionSchemas(Map<String, Object> schemas) {
    Map<String, Object> statusProperties =
        ordered(
            "active",
            ordered("type", "boolean"),
            "updatedAt",
            ordered("type", List.of("string", "null"), "format", "date-time"),
            "subscriptionUrl",
            ordered(
                "type",
                List.of("string", "null"),
                "format",
                "uri",
                "description",
                "The current subscription URL, or null when inactive or awaiting migration."));
    schemas.put(
        "BookingCalendarSubscriptionStatus",
        ordered(
            "type",
            "object",
            "additionalProperties",
            false,
            "required",
            List.of("active", "updatedAt", "subscriptionUrl"),
            "properties",
            statusProperties));
    Map<String, Object> createdProperties = new LinkedHashMap<>(statusProperties);
    createdProperties.put(
        "subscriptionUrl",
        ordered(
            "type",
            "string",
            "format",
            "uri",
            "description",
            "The newly issued subscription URL."));
    schemas.put(
        "BookingCalendarSubscriptionCreated",
        ordered(
            "type",
            "object",
            "additionalProperties",
            false,
            "required",
            List.of("active", "updatedAt", "subscriptionUrl"),
            "properties",
            createdProperties));
  }

  private static void addBookingDirectoryPaths(Map<String, Object> paths) {
    paths.put(
        "/api/v2/booking-settings/access-grantees",
        ordered("get", bookingDirectoryOperation(true)));
    paths.put(
        "/api/v2/booking-configuration-targets", ordered("get", bookingDirectoryOperation(false)));
  }

  private static Map<String, Object> bookingDirectoryOperation(boolean grantees) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put(
        "operationId",
        grantees ? "searchBookingSettingsAccessGrantees" : "searchBookingConfigurationTargets");
    operation.put(
        "summary",
        grantees
            ? "Search eligible default-access grantees"
            : "Search eligible Booking configuration targets");
    operation.put("tags", List.of("booking-directories"));
    operation.put(
        "security", List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of())));
    operation.put(
        "x-rspace-access",
        ordered(
            "description",
            grantees
                ? "Authenticated sysadmins editing Booking creation defaults."
                : "Authenticated callers; ordinary users see owned eligible Instruments and "
                    + "sysadmins see every eligible Instrument.",
            "denialReasonCodes",
            List.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN)));
    operation.put(
        "parameters",
        List.of(
            parameter(
                "query",
                "query",
                true,
                ordered("type", "string", "minLength", 2),
                "Case-insensitive search text."),
            parameter(
                "limit",
                "query",
                false,
                ordered("type", "integer", "minimum", 1, "maximum", 50, "default", 20),
                "Maximum results to return.")));
    Map<String, Object> resultSchema =
        ordered(
            "type",
            "array",
            "items",
            ref(grantees ? "ResourceGranteeDirectoryEntry" : "BookingConfigurationTarget"));
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put(
        "200",
        ordered(
            "description",
            grantees ? "Eligible users and groups." : "Eligible Instrument summaries.",
            "content",
            Map.of(JSON, ordered("schema", resultSchema))));
    responses.put("400", responseRef("BadRequest"));
    responses.put("401", responseRef("Unauthenticated"));
    responses.put("403", responseRef("Forbidden"));
    responses.put("429", responseRef("TooManyRequests"));
    responses.put("500", responseRef("UnexpectedError"));
    operation.put("responses", responses);
    operation.put(
        "x-rspace-operation",
        grantees ? "BOOKING_SETTINGS_GRANTEE_SEARCH" : "BOOKING_TARGET_SEARCH");
    return operation;
  }

  private static void addCalendarSubscriptionPath(Map<String, Object> paths) {
    String path = "/api/v2/booking-configurations/{configurationId}/calendar-subscription";
    paths.put(
        path,
        ordered(
            "get",
            calendarSubscriptionOperation("get"),
            "post",
            calendarSubscriptionOperation("post"),
            "delete",
            calendarSubscriptionOperation("delete")));
  }

  private static Map<String, Object> calendarSubscriptionOperation(String method) {
    boolean get = "get".equals(method);
    boolean post = "post".equals(method);
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put(
        "operationId",
        get
            ? "getBookingCalendarSubscription"
            : post
                ? "createOrReplaceBookingCalendarSubscription"
                : "revokeBookingCalendarSubscription");
    operation.put(
        "summary",
        get
            ? "Get calendar subscription status"
            : post
                ? "Create or replace a calendar subscription"
                : "Revoke a calendar subscription");
    operation.put(
        "description",
        get
            ? "Returns the caller's current subscription URL when one exists."
            : post
                ? "Replaces any active credential and returns the new subscription URL."
                : "Revokes only the caller's subscription for this bookable item.");
    operation.put("tags", List.of("booking-calendar-subscriptions"));
    operation.put(
        "security", List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of())));
    operation.put(
        "x-rspace-access",
        ordered(
            "description",
            "Authenticated active callers with Booking enabled; status and creation also require"
                + " read access to the bookable item.",
            "denialReasonCodes",
            List.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN)));
    operation.put(
        "parameters",
        List.of(
            parameter(
                "configurationId",
                "path",
                true,
                ordered("type", "integer", "format", "int64"),
                "Booking configuration identifier.")));
    operation.put("responses", calendarSubscriptionResponses(get, post));
    operation.put(
        "x-rspace-operation",
        get ? "CALENDAR_STATUS" : post ? "CALENDAR_CREATE" : "CALENDAR_REVOKE");
    return operation;
  }

  private static Map<String, Object> calendarSubscriptionResponses(boolean get, boolean post) {
    Map<String, Object> responses = new LinkedHashMap<>();
    if (get || post) {
      responses.put(
          "200",
          ordered(
              "description",
              get ? "Current subscription status and URL." : "New subscription URL.",
              "headers",
              privateNoStoreHeaders(),
              "content",
              Map.of(
                  JSON,
                  ordered(
                      "schema",
                      ref(
                          get
                              ? "BookingCalendarSubscriptionStatus"
                              : "BookingCalendarSubscriptionCreated")))));
    } else {
      responses.put(
          "204",
          ordered(
              "description", "The subscription is inactive.", "headers", privateNoStoreHeaders()));
    }
    responses.put("401", responseRef("Unauthenticated"));
    responses.put("403", responseRef("Forbidden"));
    responses.put("404", responseRef("NotFound"));
    responses.put("406", responseRef("NotAcceptable"));
    responses.put("429", responseRef("TooManyRequests"));
    responses.put("500", responseRef("UnexpectedError"));
    return responses;
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
      String method = METHODS.get(operation);
      if (pathItem.containsKey(method)) {
        throw new IllegalArgumentException("Duplicate OpenAPI operation " + method + " " + path);
      }
      pathItem.put(method, operation(resource, operation, component, resourceSchemas));
    }
    addAuditPaths(paths, resource, component);
    addResourceAccessPaths(paths, resource, component);
  }

  private static void addResourceAccessSchemas(Map<String, Object> schemas) {
    ApiV2OpenApiSchemas.schemaFor(ResourceAccessDocument.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ResourceAccessGrant.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ResourceGranteeDirectoryEntry.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ResourceRoleSource.class, schemas);
    schemas.put(
        "ResourceAccessReplacement",
        ordered(
            "type",
            "object",
            "additionalProperties",
            false,
            "required",
            List.of("assignments"),
            "properties",
            ordered("assignments", ordered("type", "array", "items", ref("ResourceAccessGrant")))));
  }

  private static void applyRegisteredAccessFieldSchemas(
      Map<String, Object> schemas, ApiV2ResourceRegistration<?, ?> resource, String component) {
    ResourceAccessSpec<?, ?> access = resource.resourceAccess().orElse(null);
    if (access == null || component == null) {
      return;
    }
    Map<String, Object> read = mutableObjectMap(schemas.get(component + "Read"));
    Map<String, Object> properties = mutableObjectMap(read.get("properties"));
    replaceFieldShape(
        properties, "roleSources", ordered("type", "array", "items", ref("ResourceRoleSource")));
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

  private void addResourceAccessPaths(
      Map<String, Object> paths, ApiV2ResourceRegistration<?, ?> resource, String component) {
    if (resource.resourceAccess().isEmpty()) {
      return;
    }
    String root = "/api/v2/" + resource.resourceName() + "/{id}/access";
    paths.put(
        root,
        ordered(
            "get",
            resourceAccessOperation(resource, component, "get"),
            "put",
            resourceAccessOperation(resource, component, "put")));
    paths.put(
        root + "/me", ordered("delete", resourceAccessOperation(resource, component, "delete")));
    paths.put(
        root + "/grantees", ordered("get", resourceAccessOperation(resource, component, "search")));
  }

  private Map<String, Object> resourceAccessOperation(
      ApiV2ResourceRegistration<?, ?> resource, String component, String operationKind) {
    boolean replace = "put".equals(operationKind);
    boolean leave = "delete".equals(operationKind);
    boolean search = "search".equals(operationKind);
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put(
        "operationId",
        search
            ? "search" + component + "AccessGrantees"
            : leave
                ? "leave" + component
                : replace ? "replace" + component + "Access" : "get" + component + "Access");
    operation.put(
        "summary",
        search
            ? "Search eligible access grantees"
            : leave
                ? "Remove the caller's direct assignment"
                : replace ? "Replace direct access assignments" : "Get direct access assignments");
    operation.put("tags", List.of(resource.resourceName()));
    operation.put(
        "security", List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of())));
    operation.put(
        "x-rspace-access",
        ordered(
            "description",
            search || replace
                ? "Authenticated callers with the registered assignment-management capability."
                : "Authenticated callers authorized by the registered protected resource.",
            "denialReasonCodes",
            List.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN)));
    List<Map<String, Object>> parameters = new ArrayList<>();
    FieldSchema id =
        field(resource.description().schema(), resource.description().schema().idField());
    parameters.add(
        parameter("id", "path", true, scalarSchema(id.type(), false), "Resource identifier."));
    if (replace) {
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
    if (search) {
      parameters.add(
          parameter(
              "query",
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
    operation.put("responses", resourceAccessResponses(replace, leave, search));
    operation.put(
        "x-rspace-operation",
        search
            ? "ACCESS_GRANTEE_SEARCH"
            : leave ? "ACCESS_LEAVE" : replace ? "ACCESS_REPLACE" : "ACCESS_READ");
    return operation;
  }

  private static Map<String, Object> resourceAccessResponses(
      boolean replace, boolean leave, boolean search) {
    Map<String, Object> responses = new LinkedHashMap<>();
    if (leave) {
      responses.put("204", ordered("description", "The direct assignment is absent."));
    } else {
      Map<String, Object> schema =
          search
              ? ordered("type", "array", "items", ref("ResourceGranteeDirectoryEntry"))
              : ref("ResourceAccessDocument");
      Map<String, Object> success =
          ordered(
              "description",
              search
                  ? "Eligible users and groups."
                  : "Current direct assignments and caller capabilities.",
              "content",
              Map.of(JSON, ordered("schema", schema)));
      if (!search) {
        success.put("headers", ordered("ETag", headerRef("ETag")));
      }
      responses.put("200", success);
    }
    responses.put("400", responseRef("BadRequest"));
    responses.put("401", responseRef("Unauthenticated"));
    responses.put("403", responseRef("Forbidden"));
    responses.put("404", responseRef("NotFound"));
    responses.put("406", responseRef("NotAcceptable"));
    if (replace || leave) {
      responses.put(
          "409",
          problemResponse(
              "The assignment change would violate a resource invariant.",
              409,
              "errors.api.v2.resourceAccess.ownerRequired"));
    }
    if (replace) {
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
            + "an empty result. The search range is limited to 183 elapsed days. List requests "
            + "use one-based pagination and a daily bounded-consistency snapshot over the exact "
            + "half-open interval from dateFrom through midnight after snapshotDate in UTC. The "
            + "first page selects the latest completed UTC day. Send snapshotDate and "
            + "snapshotFingerprint together on every later page. A 409 means the result set "
            + "changed and pagination must restart; a 503 means the server refused to return "
            + "partial audit data. eventId is deterministic response identity, not a source-log "
            + "signature.");
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
                    Arrays.stream(AuditAction.values()).map(Enum::name).toList())),
            "Audit actions to include.");
    actions.put("style", "form");
    actions.put("explode", true);
    parameters.add(actions);
    if (!count) {
      parameters.add(
          parameter(
              "snapshotDate",
              "query",
              false,
              ordered("type", "string", "format", "date"),
              "Completed UTC snapshot day returned by page one. Supply it together with"
                  + " snapshotFingerprint on later pages."));
      parameters.add(
          parameter(
              "snapshotFingerprint",
              "query",
              false,
              ordered(
                  "type", "string", "pattern", "^[0-9a-f]{64}$", "minLength", 64, "maxLength", 64),
              "Fingerprint returned by page one. Supply it unchanged together with snapshotDate"
                  + " on later pages."));
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
                    count ? ref("ApiV2CountResult") : auditPage(ref("ApiV2AuditEvent"))))));
    responses.put(
        "400",
        count
            ? responseRef("BadRequest")
            : problemResponse(
                "The range or snapshot parameters are invalid, or the result exceeds the"
                    + " configured ceiling and requires a narrower date range.",
                400,
                "errors.api.v2.audit.results.tooMany"));
    responses.put("401", responseRef("Unauthenticated"));
    responses.put("403", responseRef("Forbidden"));
    responses.put("404", responseRef("NotFound"));
    responses.put("406", responseRef("NotAcceptable"));
    if (!count) {
      responses.put(
          "409",
          problemResponse(
              "The bounded audit result changed; restart pagination.",
              409,
              "errors.api.v2.audit.snapshot.changed"));
    }
    responses.put("429", responseRef("TooManyRequests"));
    responses.put("500", responseRef("UnexpectedError"));
    if (!count) {
      responses.put(
          "503",
          problemResponse(
              "The server could not read a consistent audit snapshot and returned no partial"
                  + " data.",
              503,
              "errors.api.v2.audit.unavailable"));
    }
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
    List<Map<String, Object>> parameters =
        parameters(
            operation,
            schema,
            resourceSchemas,
            resource.registry(),
            runtimeFieldsExtension(resource));
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

  private List<Map<String, Object>> runtimeFieldsExtension(
      ApiV2ResourceRegistration<?, ?> resource) {
    List<Map<String, Object>> namespaces = new ArrayList<>(own(resource));
    for (CollectionDescription.Relationship<?> relationship :
        resource.description().relationships()) {
      if (relationship.targets().size() != 1) {
        continue;
      }
      String targetName = relationship.targets().get(0).resourceName();
      ApiV2ResourceRegistration<?, ?> target = catalog.find(targetName).orElse(null);
      if (target == null) {
        continue;
      }
      for (String targetNamespace : target.runtimeNamespaces()) {
        String namespace = relationship.name() + "." + targetNamespace;
        boolean projectable =
            target
                .runtimeFields(targetNamespace)
                .map(RuntimeCollectionFields::projectsThroughRelationship)
                .orElse(false);
        namespaces.add(
            ordered(
                "namespace",
                namespace,
                "catalog",
                catalogUrl(targetName, targetNamespace),
                "responseField",
                projectable ? namespace : "",
                "via",
                relationship.name(),
                "viaResource",
                targetName,
                "filterable",
                true,
                "columnSelectable",
                projectable,
                "sortable",
                false,
                "maximumProjections",
                projectable ? CollectionQueryLimits.MAX_RUNTIME_PROJECTIONS : 0,
                "catalogDefaultLimit",
                RuntimeFieldCatalogQuery.DEFAULT_LIMIT,
                "catalogMaximumLimit",
                RuntimeFieldCatalogQuery.MAX_LIMIT,
                "catalogMaximumIds",
                RuntimeFieldCatalogQuery.MAX_IDS,
                "catalogSearchParameter",
                "search",
                "catalogIdsParameter",
                "ids"));
      }
    }
    return List.copyOf(namespaces);
  }

  private static String catalogUrl(String resourceName, String namespace) {
    return "/api/v2/" + resourceName + "/fields/" + namespace;
  }

  private static List<Map<String, Object>> own(ApiV2ResourceRegistration<?, ?> resource) {
    List<Map<String, Object>> published = new ArrayList<>();
    for (String namespace : resource.runtimeNamespaces()) {
      published.add(
          ordered(
              "namespace",
              namespace,
              "catalog",
              catalogUrl(resource.resourceName(), namespace),
              "responseField",
              namespace,
              "via",
              "",
              "viaResource",
              "",
              "filterable",
              true,
              "columnSelectable",
              true,
              "sortable",
              false,
              "maximumProjections",
              CollectionQueryLimits.MAX_RUNTIME_PROJECTIONS,
              "catalogDefaultLimit",
              RuntimeFieldCatalogQuery.DEFAULT_LIMIT,
              "catalogMaximumLimit",
              RuntimeFieldCatalogQuery.MAX_LIMIT,
              "catalogMaximumIds",
              RuntimeFieldCatalogQuery.MAX_IDS,
              "catalogSearchParameter",
              "search",
              "catalogIdsParameter",
              "ids"));
    }
    return List.copyOf(published);
  }

  private static List<Map<String, Object>> parameters(
      ResourceOperation operation,
      ResourceSchema schema,
      Map<String, ResourceSchema> resourceSchemas,
      ResourceRegistry registry,
      List<Map<String, Object>> runtimeFields) {
    List<Map<String, Object>> parameters = new ArrayList<>();
    if (operation == ResourceOperation.READ
        || operation == ResourceOperation.UPDATE
        || operation == ResourceOperation.DELETE) {
      FieldSchema id = field(schema, schema.idField());
      parameters.add(
          parameter("id", "path", true, scalarSchema(id.type(), false), "Resource identifier."));
    }
    if (operation == ResourceOperation.LIST) {
      parameters.add(where(schema, false, resourceSchemas, registry, runtimeFields));
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
      parameters.add(
          where(
              schema,
              operation != ResourceOperation.COUNT,
              resourceSchemas,
              registry,
              runtimeFields));
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
                  + " reference object and value is the readable target ID. At greater depth,"
                  + " value is the expanded read document; relationTo and globalId remain stable."
                  + " A missing or unreadable target is null at every depth."));
      parameters.add(
          fieldset("fields", schema, resourceSchemas, "Fields to include by resource type."));
      parameters.add(
          fieldset("exclude", schema, resourceSchemas, "Fields to exclude by resource type."));
    }
    return parameters;
  }

  private static Map<String, Object> where(
      ResourceSchema schema,
      boolean required,
      Map<String, ResourceSchema> resourceSchemas,
      ResourceRegistry registry,
      List<Map<String, Object>> runtimeFields) {
    Map<String, Object> parameter =
        parameter(
            "where",
            "query",
            required,
            ordered("type", "string", "maxLength", CollectionQueryLimits.MAX_WHERE_LENGTH),
            "Bounded RSQL filter expression. Available selectors and operators are provided in"
                + " x-rspace-filter."
                + " For ID selectors on relationships whose targets are all users, exact me"
                + " resolves to the authenticated effective subject's numeric ID; for example,"
                + " createdBy.value==me."
                + (runtimeFields.isEmpty()
                    ? ""
                    : " Further per-caller selectors are listed by the catalog named in"
                        + " x-rspace-runtime-fields."));
    parameter.put("x-rspace-filter", filterExtension(schema, resourceSchemas, registry));
    Map<String, Object> relationshipFields = relationshipFieldsExtension(schema, resourceSchemas);
    if (!relationshipFields.isEmpty()) {
      parameter.put("x-rspace-relationship-fields", relationshipFields);
    }
    if (!runtimeFields.isEmpty()) {
      parameter.put("x-rspace-runtime-fields", runtimeFields);
    }
    return parameter;
  }

  private static Map<String, Object> relationshipFieldsExtension(
      ResourceSchema resource, Map<String, ResourceSchema> resourceSchemas) {
    Map<String, Object> published = new LinkedHashMap<>();
    for (RelationshipSchema relationship : resource.relationships()) {
      List<ResourceSchema> targets =
          relationship.targetResources().stream()
              .map(resourceSchemas::get)
              .filter(Objects::nonNull)
              .toList();
      if (targets.size() != relationship.targetResources().size() || targets.isEmpty()) {
        continue;
      }
      for (FieldSchema candidate : targets.get(0).fields()) {
        List<FieldSchema> onEveryTarget =
            targets.stream()
                .map(
                    target ->
                        target.fields().stream()
                            .filter(field -> field.name().equals(candidate.name()))
                            .findFirst()
                            .orElse(null))
                .toList();
        if (onEveryTarget.contains(null)) {
          continue;
        }
        Map<String, Object> fieldSchema = scalarSchema(candidate.type(), candidate.nullable());
        boolean sameShape =
            onEveryTarget.stream()
                .allMatch(
                    field -> scalarSchema(field.type(), field.nullable()).equals(fieldSchema));
        if (!sameShape) {
          continue;
        }
        Set<Operator> operators =
            new LinkedHashSet<>(FilterSelector.relationshipTargetFieldOperators());
        onEveryTarget.forEach(field -> operators.retainAll(field.filterOperators()));
        Map<String, Object> definition =
            ordered(
                "schema",
                fieldSchema,
                "operators",
                operators.stream().map(ApiV2OpenApiGenerator::operatorToken).toList(),
                "wildcards",
                !operators.isEmpty()
                    && onEveryTarget.stream().allMatch(FieldSchema::supportsWildcards));
        String title = candidate.openApi().title();
        if (title != null && !title.isBlank()) {
          definition.put("title", title);
        }
        published.put(relationship.name() + "." + candidate.name(), definition);
      }
    }
    return published;
  }

  private record PublishedFilter(
      String selector,
      String sourceSelector,
      ResourceSchema source,
      List<String> operators,
      boolean supportsWildcards) {}

  /**
   * Discovers every public filter visible from a collection through its declared relationships.
   *
   * <p>The same traversal handles any number of relationships and target collections. Only scalar
   * fields of an immediate target are reachable because the query language permits one relationship
   * hop.
   */
  private static List<PublishedFilter> publishedFilters(
      ResourceSchema resource,
      Map<String, ResourceSchema> resourceSchemas,
      ResourceRegistry registry) {
    Map<String, PublishedFilter> published = new LinkedHashMap<>();
    resource
        .filters()
        .forEach(
            filter ->
                published.put(
                    filter.selector(),
                    publishedFilter(filter.selector(), filter, resource, false)));
    registry
        .relationshipQueryPaths(resource.name())
        .forEach(
            path -> {
              ResourceSchema target =
                  resourceSchemas.get(path.targets().get(0).description().resourceName());
              if (target == null) {
                return;
              }
              target.filters().stream()
                  .filter(filter -> filter.selector().equals(path.targetField()))
                  .findFirst()
                  .map(
                      filter ->
                          new PublishedFilter(
                              path.selector(),
                              path.targetField(),
                              target,
                              path.filterSelector().operators().stream()
                                  .map(ApiV2OpenApiGenerator::operatorToken)
                                  .toList(),
                              path.filterSelector().supportsWildcards()))
                  .filter(filter -> !filter.operators().isEmpty())
                  .ifPresent(filter -> published.put(filter.selector(), filter));
            });
    return List.copyOf(published.values());
  }

  private static PublishedFilter publishedFilter(
      String selector, FilterSchema filter, ResourceSchema source, boolean throughRelationship) {
    return new PublishedFilter(
        selector,
        filter.selector(),
        source,
        filter.operators().stream()
            .filter(
                operator ->
                    !throughRelationship
                        || FilterSelector.relationshipTargetFieldOperators().contains(operator))
            .map(ApiV2OpenApiGenerator::operatorToken)
            .toList(),
        filter.supportsWildcards());
  }

  private static Map<String, Object> filterExtension(
      ResourceSchema resource,
      Map<String, ResourceSchema> resourceSchemas,
      ResourceRegistry registry) {
    Map<String, Object> selectors = new LinkedHashMap<>();
    publishedFilters(resource, resourceSchemas, registry)
        .forEach(
            filter -> {
              Map<String, Object> definition =
                  ordered(
                      "schema",
                      filterSchema(filter.sourceSelector(), filter.source(), resourceSchemas),
                      "operators",
                      filter.operators(),
                      "wildcards",
                      filter.supportsWildcards());
              filter.source().fields().stream()
                  .filter(field -> field.name().equals(filter.sourceSelector()))
                  .map(field -> field.openApi().title())
                  .filter(title -> title != null && !title.isBlank())
                  .findFirst()
                  .ifPresent(title -> definition.put("title", title));
              selectors.put(filter.selector(), definition);
            });
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
    Optional<FieldSchema> field =
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
          List<String> fieldNames =
              target.documentFields().stream().map(ResourceFieldSchema::name).toList();
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
    String component = names.get(resource.name());
    components.put(component + "Reference", referenceSchema(resource));
    components.put(component + "Read", documentSchema(resource, names, null));
    if (routable) {
      components.put(component + "Create", documentSchema(resource, names, WriteOperation.CREATE));
      components.put(component + "Update", documentSchema(resource, names, WriteOperation.UPDATE));
    }
  }

  private static void addStandardSchemas(Map<String, Object> schemas) {
    ApiV2OpenApiSchemas.schemaFor(ApiV2Problem.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ApiV2CountResult.class, schemas);
    ApiV2OpenApiSchemas.schemaFor(ApiV2AuditEvent.class, schemas);
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
    for (ResourceFieldSchema field : resource.documentFields()) {
      if (writeOperation != null && !field.writeOperations().contains(writeOperation)) {
        continue;
      }
      Map<String, Object> property = documentFieldSchema(field, names, writeOperation);
      applyDocumentation(property, field.openApi());
      if (writeOperation == WriteOperation.CREATE && field.defaultValue() != null) {
        property.put("default", field.defaultValue());
      }
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
      properties.put(field.name(), property);
      if ((writeOperation == WriteOperation.CREATE && field.requiredOnCreate())
          || (writeOperation == null && field.name().equals(resource.idField()))) {
        required.add(field.name());
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

  private static Map<String, Object> documentFieldSchema(
      ResourceFieldSchema field, Map<String, String> names, WriteOperation writeOperation) {
    Map<String, Object> schema;
    if (field instanceof FieldSchema scalar) {
      schema = scalarSchema(scalar.type(), false);
    } else if (field instanceof RelationshipSchema relationship) {
      schema =
          new LinkedHashMap<>(
              writeOperation == null
                  ? relationshipOutput(relationship, names)
                  : relationshipInput(relationship, writeOperation, names));
    } else {
      throw new IllegalStateException("Unsupported resource field schema " + field.getClass());
    }
    return nullable(schema, writeOperation == null ? field.nullableOnRead() : field.nullable());
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
    return value;
  }

  private static Map<String, Object> relationshipInput(
      RelationshipSchema relationship, WriteOperation operation, Map<String, String> names) {
    List<Map<String, Object>> variants = new ArrayList<>();
    Set<RelationshipInputForm> forms = relationship.inputForms().getOrDefault(operation, Set.of());
    if (forms.contains(RelationshipInputForm.GLOBAL_ID)) {
      String alternatives =
          relationship.globalIdPrefixesByTarget().values().stream()
              .map(ApiV2OpenApiGenerator::escapeEcmaRegex)
              .collect(Collectors.joining("|"));
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
    if (!nullable) {
      return schema;
    }
    if (schema.get("type") instanceof String type) {
      Map<String, Object> result = new LinkedHashMap<>(schema);
      result.put("type", List.of(type, "null"));
      return result;
    }
    return ordered("anyOf", List.of(schema, ordered("type", "null")));
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

  private static Map<String, Object> auditPage(Map<String, Object> item) {
    Map<String, Object> page = listResult(item);
    List<String> required =
        ((List<?>) page.get("required")).stream().map(String::valueOf).collect(Collectors.toList());
    required.add("snapshotDate");
    required.add("snapshotFingerprint");
    page.put("required", required);
    Map<String, Object> properties = mutableObjectMap(page.get("properties"));
    properties.put("snapshotDate", ordered("type", "string", "format", "date"));
    properties.put(
        "snapshotFingerprint",
        ordered("type", "string", "pattern", "^[0-9a-f]{64}$", "minLength", 64, "maxLength", 64));
    return page;
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

  private static Map<String, Object> problemResponse(
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

  private static Map<String, Object> withHeaders(
      Map<String, Object> response, Map<String, Object> headers) {
    response.put("headers", headers);
    return response;
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

  private static Map<String, Object> ordered(Object... entries) {
    if (entries.length % 2 != 0) {
      throw new IllegalArgumentException("Ordered map entries must be key/value pairs");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      result.put(String.class.cast(entries[i]), entries[i + 1]);
    }
    return result;
  }

  private static Map<String, Object> objectMap(Map<?, ?> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach((key, value) -> result.put(String.class.cast(key), value));
    return result;
  }

  @SuppressWarnings("unchecked") // Every key is checked before returning the mutable view.
  private static Map<String, Object> mutableObjectMap(Object value) {
    if (!(value instanceof Map<?, ?> map)
        || map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
      throw new IllegalArgumentException("Expected an object with string keys");
    }
    return (Map<String, Object>) map;
  }
}
