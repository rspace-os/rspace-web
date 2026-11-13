package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.field;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.scalarSchema;

import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.FieldSchema;
import com.researchspace.model.collection.FilterSchema;
import com.researchspace.model.collection.FilterSelector;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RelationshipSchema;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceSchema;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Builds the {@code x-rspace-filter} extension describing the selectors a collection accepts. */
final class ApiV2OpenApiFilterExtension {

  private ApiV2OpenApiFilterExtension() {}

  private record PublishedFilter(
      String selector,
      String sourceSelector,
      ResourceSchema source,
      List<String> operators,
      boolean supportsWildcards) {}

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
                operators.stream().map(Operator::token).toList(),
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
                                  .map(Operator::token)
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
        filter.operators().stream().map(Operator::token).toList(),
        filter.supportsWildcards());
  }

  static Map<String, Object> filterExtension(
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
}
