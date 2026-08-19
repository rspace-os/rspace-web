package com.researchspace.api.v2.query;

import com.researchspace.api.v2.controller.ApiV2BadRequestException;
import com.researchspace.api.v2.model.ApiV2CollectionQuery;
import com.researchspace.api.v2.model.ApiV2FieldsetQuery;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.ResourceRequest.Page;
import com.researchspace.model.collection.RsqlFilterParser;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    return new ResourceRequest(
        filter(query.getWhere(), description, registry),
        ApiV2SortParser.parse(query.getSort(), description),
        new Page(query.getPage(), query.getLimit()),
        fields(fieldsets, description, registry),
        includes(query.getDepth(), description, registry));
  }

  public static ResourceRequest item(
      int depth,
      ApiV2FieldsetQuery fieldsets,
      CollectionDescription<?> description,
      ResourceRegistry registry) {
    return new ResourceRequest(
        null,
        description.defaultSort(),
        new Page(1, 1),
        fields(fieldsets, description, registry),
        includes(depth, description, registry));
  }

  public static ResourceRequest filtered(
      String where, CollectionDescription<?> description, ResourceRegistry registry) {
    // Length is enforced by filter(); calling validateWhere here too would just repeat it.
    return ResourceRequest.unpaged(filter(where, description, registry));
  }

  public static ResourceRequest bulk(
      String where, CollectionDescription<?> description, ResourceRegistry registry) {
    if (where == null || where.isBlank()) {
      throw new ApiV2BadRequestException("errors.api.v2.bulk.filter.required");
    }
    return filtered(where, description, registry);
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
      String where, CollectionDescription<?> description, ResourceRegistry registry) {
    validateWhere(where);
    return new RsqlFilterParser(description, registry).parse(where);
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
      ApiV2FieldsetQuery fieldsets, CollectionDescription<?> root, ResourceRegistry registry) {
    Map<String, FieldSelection> included = new LinkedHashMap<>();
    Map<String, FieldSelection> excluded = new LinkedHashMap<>();
    fieldsets
        .getFields()
        .forEach(
            (resourceName, values) ->
                addFieldSelection(resourceName, values, registry, included, true));
    fieldsets
        .getExclude()
        .forEach(
            (resourceName, values) ->
                addFieldSelection(resourceName, values, registry, excluded, false));
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
      boolean include) {
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
    Set<String> names = parseFieldNames(value, description);
    FieldSelection selection =
        include
            ? FieldSelection.include(
                names.isEmpty() ? Set.of(description.idField()) : Set.copyOf(names))
            : names.isEmpty() ? FieldSelection.all() : FieldSelection.exclude(names);
    selections.put(resourceName, selection);
  }

  private static Set<String> parseFieldNames(String value, CollectionDescription<?> description) {
    if (value == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
    }
    if (value.isEmpty()) {
      return Set.of();
    }
    Set<String> fields = new LinkedHashSet<>();
    for (String token : value.split(",", -1)) {
      String field = token.trim();
      if (field.isEmpty()) {
        throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
      }
      description.requireSelectableField(field);
      if (!fields.add(field)) {
        throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
      }
    }
    return Set.copyOf(fields);
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
