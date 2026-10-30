package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.FieldSchema;
import com.researchspace.model.collection.FilterSchema;
import com.researchspace.model.collection.FilterSelector;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.RelationshipSchema;
import com.researchspace.model.collection.ResourceFieldSchema;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceSchema;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class ApiV2OpenApiQueryParameters {

  private final ApiV2ResourceCatalog catalog;

  ApiV2OpenApiQueryParameters(ApiV2ResourceCatalog catalog) {
    this.catalog = catalog;
  }

  private record PublishedFilter(
      String selector,
      String sourceSelector,
      ResourceSchema source,
      List<String> operators,
      boolean supportsWildcards) {}

  void applyRuntimeProjectionSchemas(
      Map<String, Object> schemas, ApiV2ResourceRegistration<?, ?> resource, String component) {
    Map<String, Object> read = mutableObjectMap(schemas.get(component + "Read"));
    Map<String, Object> properties = mutableObjectMap(read.get("properties"));
    for (Map<String, Object> runtimeField : runtimeFieldsExtension(resource)) {
      String responseField = String.class.cast(runtimeField.get("responseField"));
      if (!responseField.isEmpty()) {
        properties.put(responseField, ordered("type", "object", "additionalProperties", true));
      }
    }
  }

  List<Map<String, Object>> runtimeFieldsExtension(ApiV2ResourceRegistration<?, ?> resource) {
    List<Map<String, Object>> namespaces = new ArrayList<>(own(resource));
    for (Relationship<?> relationship : resource.description().relationships()) {
      if (relationship.targets().size() != 1) {
        continue;
      }
      String targetName = relationship.targets().get(0).resourceName();
      for (RuntimeCollectionFields<?> provider : catalog.runtimeFieldsOf(targetName)) {
        String targetNamespace = provider.namespace();
        String namespace = relationship.name() + "." + targetNamespace;
        boolean projectable = provider.projectsThroughRelationship();
        namespaces.add(
            runtimeNamespace(
                namespace,
                catalogUrl(targetName, targetNamespace),
                relationship.name(),
                targetName,
                projectable));
      }
    }
    return List.copyOf(namespaces);
  }

  private static String catalogUrl(String resourceName, String namespace) {
    return "/api/v2/" + resourceName + "/fields/" + namespace;
  }

  private static Map<String, Object> runtimeNamespace(
      String namespace, String catalog, String via, String viaResource, boolean projectable) {
    return ordered(
        "namespace",
        namespace,
        "catalog",
        catalog,
        "responseField",
        projectable ? namespace : "",
        "via",
        via,
        "viaResource",
        viaResource,
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
        "q",
        "catalogIdsParameter",
        "ids");
  }

  private static List<Map<String, Object>> own(ApiV2ResourceRegistration<?, ?> resource) {
    List<Map<String, Object>> published = new ArrayList<>();
    for (String namespace : resource.runtimeNamespaces()) {
      published.add(
          runtimeNamespace(
              namespace, catalogUrl(resource.resourceName(), namespace), "", "", true));
    }
    return List.copyOf(published);
  }

  static List<Map<String, Object>> parameters(
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
      parameters.add(pageParameter());
      parameters.add(limitParameter());
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
    if (!runtimeFields.isEmpty()) {
      parameter.put("x-rspace-runtime-fields", runtimeFields);
    }
    return parameter;
  }

  /**
   * Scalar fields shared by every target of each relationship, keyed by {@code
   * <relationship>.<field>}, so clients can offer them as columns whether or not they are
   * filterable.
   */
  private static Map<String, Map<String, Object>> relationshipTargetFields(
      ResourceSchema resource, Map<String, ResourceSchema> resourceSchemas) {
    Map<String, Map<String, Object>> published = new LinkedHashMap<>();
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
        String viaTitle = relationship.openApi().title();
        if (viaTitle != null && !viaTitle.isBlank()) {
          definition.put("viaTitle", viaTitle);
        }
        published.put(relationship.name() + "." + candidate.name(), definition);
      }
    }
    return published;
  }

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
                    filter.selector(), publishedFilter(filter.selector(), filter, resource)));
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
      String selector, FilterSchema filter, ResourceSchema source) {
    return new PublishedFilter(
        selector,
        filter.selector(),
        source,
        filter.operators().stream().map(ApiV2OpenApiGenerator::operatorToken).toList(),
        filter.supportsWildcards());
  }

  private static Map<String, Object> filterExtension(
      ResourceSchema resource,
      Map<String, ResourceSchema> resourceSchemas,
      ResourceRegistry registry) {
    Map<String, Map<String, Object>> selectors = new LinkedHashMap<>();
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
              relationshipPicker(resource, filter.selector())
                  .ifPresent(picker -> definition.put("picker", picker));
              selectors.put(filter.selector(), definition);
            });
    // A filterable target field keeps the definition the query parser accepts.
    relationshipTargetFields(resource, resourceSchemas)
        .forEach(
            (selector, definition) -> {
              Map<String, Object> filter = selectors.putIfAbsent(selector, definition);
              if (filter != null && definition.containsKey("viaTitle")) {
                filter.put("viaTitle", definition.get("viaTitle"));
              }
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

  private static Optional<Map<String, Object>> relationshipPicker(
      ResourceSchema resource, String selector) {
    return resource.relationships().stream()
        .filter(relationship -> relationship.name().equals(selector))
        .filter(relationship -> relationship.targetResources().size() == 1)
        .findFirst()
        .flatMap(
            relationship -> {
              String target = relationship.targetResources().get(0);
              String globalIdPrefix = relationship.globalIdPrefixesByTarget().get(target);
              return globalIdPrefix == null
                  ? Optional.empty()
                  : Optional.of(
                      ordered(
                          "resource",
                          target,
                          "identity",
                          "globalId",
                          "globalIdPrefix",
                          globalIdPrefix));
            });
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

  static Map<String, Object> parameter(
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

  private static Map<String, Object> pageParameter() {
    return parameter(
        "page",
        "query",
        false,
        ordered("type", "integer", "minimum", 1, "default", 1),
        "One-based page number.");
  }

  private static Map<String, Object> limitParameter() {
    return parameter(
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
        "Maximum documents per page.");
  }

  public static String operatorToken(Operator operator) {
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

  private static Map<String, Object> scalarSchema(
      CollectionFieldType.Schema type, boolean nullable) {
    return ApiV2OpenApiResourceDocumentBuilder.scalarSchema(type, nullable);
  }

  private static FieldSchema field(ResourceSchema resource, String name) {
    return ApiV2OpenApiResourceDocumentBuilder.field(resource, name);
  }
}
