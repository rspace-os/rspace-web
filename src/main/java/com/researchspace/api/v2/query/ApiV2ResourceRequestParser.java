package com.researchspace.api.v2.query;

import com.researchspace.api.v2.controller.ApiV2BadRequestException;
import com.researchspace.api.v2.model.ApiV2CollectionQuery;
import com.researchspace.api.v2.model.ApiV2FieldsetQuery;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.ResourceRequest.Page;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.model.collection.RuntimeFieldContext;
import com.researchspace.model.collection.RuntimeFieldSelection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts collection HTTP parameters into one typed, layer-neutral resource request. */
public final class ApiV2ResourceRequestParser {

  private ApiV2ResourceRequestParser() {}

  public static ResourceRequest parse(
      ApiV2CollectionQuery query,
      ApiV2FieldsetQuery fieldsets,
      CollectionDescription<?> description,
      ResourceRegistry registry) {
    return parse(query, fieldsets, description, registry, RuntimeFieldContext.empty());
  }

  public static ResourceRequest parse(
      ApiV2CollectionQuery query,
      ApiV2FieldsetQuery fieldsets,
      CollectionDescription<?> description,
      ResourceRegistry registry,
      RuntimeFieldContext runtimeFields) {
    RuntimeFieldResolution runtime = new RuntimeFieldResolution(runtimeFields);
    FilterExpression filter = filter(query.getWhere(), description, registry, runtime);
    ResourceFieldSelections fields = fields(fieldsets, description, registry, runtime);
    return new ResourceRequest(
        filter,
        ApiV2SortParser.parse(query.getSort(), description),
        new Page(query.getPage(), query.getLimit()),
        fields,
        includes(query.getDepth(), description, registry),
        runtime.selection());
  }

  public static ResourceRequest item(
      int depth,
      ApiV2FieldsetQuery fieldsets,
      CollectionDescription<?> description,
      ResourceRegistry registry) {
    return item(depth, fieldsets, description, registry, RuntimeFieldContext.empty());
  }

  public static ResourceRequest item(
      int depth,
      ApiV2FieldsetQuery fieldsets,
      CollectionDescription<?> description,
      ResourceRegistry registry,
      RuntimeFieldContext runtimeFields) {
    RuntimeFieldResolution runtime = new RuntimeFieldResolution(runtimeFields);
    ResourceFieldSelections fields = fields(fieldsets, description, registry, runtime);
    return new ResourceRequest(
        null,
        description.defaultSort(),
        new Page(1, 1),
        fields,
        includes(depth, description, registry),
        runtime.selection());
  }

  public static ResourceRequest filtered(
      String where, CollectionDescription<?> description, ResourceRegistry registry) {
    return filtered(where, description, registry, RuntimeFieldContext.empty());
  }

  public static ResourceRequest filtered(
      String where,
      CollectionDescription<?> description,
      ResourceRegistry registry,
      RuntimeFieldContext runtimeFields) {
    // Length is enforced by filter(); calling validateWhere here too would just repeat it.
    RuntimeFieldResolution runtime = new RuntimeFieldResolution(runtimeFields);
    FilterExpression filter = filter(where, description, registry, runtime);
    return ResourceRequest.unpaged(filter, runtime.selection());
  }

  public static ResourceRequest bulk(
      String where, CollectionDescription<?> description, ResourceRegistry registry) {
    if (where == null || where.isBlank()) {
      throw new ApiV2BadRequestException("errors.api.v2.bulk.filter.required");
    }
    return filtered(where, description, registry, RuntimeFieldContext.empty());
  }

  /**
   * Bounds the {@code where} parameter before it is URL-decoded and bound.
   *
   * <p>The outer of the two layers that enforce {@link ApiV2CollectionQuery#MAX_WHERE_LENGTH}. This
   * one measures the raw, still-encoded value and matches the parameter name after decoding it, so
   * a caller cannot slip an oversized expression past it by encoding either the name ({@code
   * w%68ere}) or the value. Because percent-encoding only lengthens a string, it is necessarily at
   * least as strict as the decoded check — an expression of 1366 characters written as {@code
   * %61}-escapes is 4098 raw characters and is refused. That conservatism is deliberate and pinned
   * by {@code ApiV2QuerySupportTest}.
   *
   * <p>The inner layer is {@link #validateWhere}, which every parse path reaches through {@link
   * #filter} and which is the authoritative rule for callers that do not arrive through the
   * controller advice.
   */
  public static void validateRawWhere(String rawQuery) {
    if (rawQuery == null) {
      return;
    }
    for (String parameter : rawQuery.split("&", -1)) {
      int separator = parameter.indexOf('=');
      String rawName = separator < 0 ? parameter : parameter.substring(0, separator);
      String name;
      try {
        name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException ex) {
        throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
      }
      int rawValueLength = separator < 0 ? 0 : parameter.length() - separator - 1;
      if ("where".equals(name) && rawValueLength > ApiV2CollectionQuery.MAX_WHERE_LENGTH) {
        throw new ApiV2BadRequestException(
            "errors.api.v2.where.length", ApiV2CollectionQuery.MAX_WHERE_LENGTH);
      }
    }
  }

  /** Validates fieldset shape and cardinality before Spring attempts indexed-property binding. */
  public static void validateFieldsetParameters(Map<String, String[]> parameters) {
    for (Map.Entry<String, String[]> parameter : parameters.entrySet()) {
      String name = parameter.getKey();
      if (name.equals("select") || name.startsWith("select[")) {
        throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
      }
      if (name.equals("fields") || name.startsWith("fields[")) {
        validateFieldsetParameter(parameter, "fields[");
      }
      if (name.equals("exclude") || name.startsWith("exclude[")) {
        validateFieldsetParameter(parameter, "exclude[");
      }
    }
  }

  private static void validateFieldsetParameter(
      Map.Entry<String, String[]> parameter, String prefix) {
    if (!isSingleMapEntry(parameter.getKey(), prefix)) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    if (parameter.getValue() == null || parameter.getValue().length != 1) {
      throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
    }
  }

  private static boolean isSingleMapEntry(String name, String prefix) {
    if (!name.startsWith(prefix) || !name.endsWith("]")) {
      return false;
    }
    String key = name.substring(prefix.length(), name.length() - 1);
    return !key.isEmpty() && key.indexOf('[') < 0 && key.indexOf(']') < 0;
  }

  private static FilterExpression filter(
      String where,
      CollectionDescription<?> description,
      ResourceRegistry registry,
      RuntimeFieldResolution runtime) {
    validateWhere(where);
    RsqlFilterParser parser = new RsqlFilterParser(description, registry, runtime.context());
    FilterExpression filter = parser.parse(where);
    parser.resolvedRuntimeFields().forEach(runtime::filtered);
    parser.runtimeRelationships().forEach(runtime::reachedThrough);
    return filter;
  }

  private record HoppedProjection(
      String selector, String namespace, String relationship, ResolvedRuntimeField field) {}

  private static final class RuntimeFieldResolution {

    private final RuntimeFieldContext context;
    private final Map<String, ResolvedRuntimeField> selectors = new LinkedHashMap<>();
    private final Set<String> projected = new LinkedHashSet<>();
    private final Map<String, Integer> projectionsByNamespace = new LinkedHashMap<>();
    private final Map<String, String> relationships = new LinkedHashMap<>();

    private RuntimeFieldResolution(RuntimeFieldContext context) {
      this.context = context;
    }

    private RuntimeFieldContext context() {
      return context;
    }

    private void filtered(String selector, ResolvedRuntimeField field) {
      selectors.putIfAbsent(selector, field);
    }

    private void reachedThrough(String selector, String relationship) {
      relationships.putIfAbsent(selector, relationship);
    }

    private void project(List<String> requested) {
      if (requested.isEmpty()) {
        return;
      }
      requested.forEach(
          selector ->
              reserveProjection(
                  selector,
                  context
                      .namespaceOf(selector)
                      .orElseThrow(
                          () ->
                              new CollectionQueryException(
                                  CollectionQueryException.Reason.FIELD))));
      Set<String> unresolved = new LinkedHashSet<>(requested);
      unresolved.removeAll(selectors.keySet());
      Map<String, ResolvedRuntimeField> resolved = context.resolveAll(unresolved);
      for (String selector : requested) {
        ResolvedRuntimeField field =
            selectors.containsKey(selector) ? selectors.get(selector) : resolved.get(selector);
        if (field == null) {
          throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
        }
        selectors.putIfAbsent(selector, field);
      }
    }

    private void projectThroughRelationship(List<HoppedProjection> requested) {
      if (requested.isEmpty()) {
        return;
      }
      for (HoppedProjection hopped : requested) {
        reserveProjection(hopped.selector(), hopped.namespace());
        selectors.putIfAbsent(hopped.selector(), hopped.field());
        relationships.putIfAbsent(hopped.selector(), hopped.relationship());
      }
    }

    private void reserveProjection(String selector, String namespace) {
      if (!projected.add(selector)) {
        return;
      }
      int count = projectionsByNamespace.merge(namespace, 1, Integer::sum);
      if (count > CollectionQueryLimits.MAX_RUNTIME_PROJECTIONS) {
        throw new ApiV2BadRequestException(
            "errors.api.v2.runtimeFields.projection.limit",
            CollectionQueryLimits.MAX_RUNTIME_PROJECTIONS);
      }
    }

    private RuntimeFieldSelection selection() {
      return selectors.isEmpty()
          ? RuntimeFieldSelection.empty()
          : new RuntimeFieldSelection(selectors, projected, relationships);
    }
  }

  private static IncludeTree includes(
      int depth, CollectionDescription<?> description, ResourceRegistry registry) {
    if (depth < 0 || depth > ApiV2CollectionQuery.MAX_DEPTH) {
      throw new ApiV2BadRequestException(
          "errors.api.v2.depth.range", ApiV2CollectionQuery.MAX_DEPTH);
    }
    return IncludeTree.toDepth(description, registry, depth);
  }

  private static ResourceFieldSelections fields(
      ApiV2FieldsetQuery fieldsets,
      CollectionDescription<?> root,
      ResourceRegistry registry,
      RuntimeFieldResolution runtime) {
    Map<String, FieldSelection> included = new LinkedHashMap<>();
    Map<String, FieldSelection> excluded = new LinkedHashMap<>();
    fieldsets
        .getFields()
        .forEach(
            (resourceName, values) ->
                addFieldSelection(
                    resourceName,
                    values,
                    registry,
                    included,
                    true,
                    root.resourceName().equals(resourceName) ? runtime : null));
    fieldsets
        .getExclude()
        .forEach(
            (resourceName, values) ->
                addFieldSelection(resourceName, values, registry, excluded, false, null));
    included.keySet().stream()
        .filter(excluded::containsKey)
        .findAny()
        .ifPresent(
            ignored -> {
              throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
            });
    Map<String, FieldSelection> selections = new LinkedHashMap<>(included);
    selections.putAll(excluded);
    return ResourceFieldSelections.forRoot(root.resourceName(), selections);
  }

  private static void addFieldSelection(
      String resourceName,
      String value,
      ResourceRegistry registry,
      Map<String, FieldSelection> selections,
      boolean include,
      RuntimeFieldResolution runtime) {
    if (resourceName.isEmpty()
        || resourceName.indexOf('[') >= 0
        || resourceName.indexOf(']') >= 0) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    CollectionDescription<?> description;
    try {
      description = registry.requireResource(resourceName);
    } catch (IllegalArgumentException ex) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    Set<String> names = parseFieldNames(value, description, runtime);
    FieldSelection selection =
        include
            ? FieldSelection.include(
                names.isEmpty() ? Set.of(description.idField()) : Set.copyOf(names))
            : names.isEmpty() ? FieldSelection.all() : FieldSelection.exclude(names);
    selections.put(resourceName, selection);
  }

  private static Set<String> parseFieldNames(
      String value, CollectionDescription<?> description, RuntimeFieldResolution runtime) {
    if (value == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
    }
    if (value.isEmpty()) {
      return Set.of();
    }
    Set<String> fields = new LinkedHashSet<>();
    Set<String> seen = new LinkedHashSet<>();
    List<String> runtimeSelectors = new ArrayList<>();
    List<HoppedProjection> hoppedSelectors = new ArrayList<>();
    for (String token : value.split(",", -1)) {
      String field = token.trim();
      if (field.isEmpty()) {
        throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
      }
      if (!seen.add(field)) {
        throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
      }
      RuntimeFieldContext context = runtime == null ? null : runtime.context();
      if (context != null && context.isBareNamespace(field)) {
        throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
      }
      if (context != null && context.namespaced(field)) {
        runtimeSelectors.add(field);
        continue;
      }
      HoppedProjection hopped =
          context == null ? null : hoppedProjection(field, description, context);
      if (hopped != null) {
        hoppedSelectors.add(hopped);
        continue;
      }
      description.requireSelectableField(field);
      fields.add(field);
    }
    if (runtime != null) {
      runtime.project(runtimeSelectors);
      runtime.projectThroughRelationship(hoppedSelectors);
    }
    if (fields.isEmpty() && !seen.isEmpty()) {
      fields.add(description.idField());
    }
    return Set.copyOf(fields);
  }

  private static HoppedProjection hoppedProjection(
      String selector, CollectionDescription<?> description, RuntimeFieldContext context) {
    int dot = selector.indexOf('.');
    if (dot <= 0 || dot == selector.length() - 1) {
      return null;
    }
    CollectionDescription.Relationship<?> relationship =
        description.findRelationship(selector.substring(0, dot)).orElse(null);
    if (relationship == null || relationship.targets().size() != 1) {
      return null;
    }
    String targetResource = relationship.targets().get(0).resourceName();
    String targetSelector = selector.substring(dot + 1);
    String targetNamespace = context.namespaceOnTarget(targetResource, targetSelector).orElse(null);
    if (targetNamespace == null) {
      return null;
    }
    ResolvedRuntimeField resolved =
        context.resolveProjectionOnTarget(targetResource, targetSelector).orElse(null);
    return resolved == null
        ? null
        : new HoppedProjection(
            selector, relationship.name() + "." + targetNamespace, relationship.name(), resolved);
  }

  /**
   * The single authoritative check on decoded {@code where} length.
   *
   * <p>Reached by every parse path through {@link #filter}, so {@code filtered} and {@code bulk}
   * must not call it again, and no caller needs its own copy of the rule.
   */
  private static void validateWhere(String where) {
    if (where != null && where.length() > ApiV2CollectionQuery.MAX_WHERE_LENGTH) {
      throw new ApiV2BadRequestException(
          "errors.api.v2.where.length", ApiV2CollectionQuery.MAX_WHERE_LENGTH);
    }
  }
}
