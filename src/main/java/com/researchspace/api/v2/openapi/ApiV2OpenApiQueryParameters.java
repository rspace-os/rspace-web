package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.field;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.scalarSchema;

import com.researchspace.api.v2.openapi.ApiV2OpenApiText.Key;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.FieldSchema;
import com.researchspace.model.collection.RelationshipSchema;
import com.researchspace.model.collection.ResourceFieldSchema;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the query, path and header parameters of standard resource operations. */
final class ApiV2OpenApiQueryParameters {

  static final String PARAMETERS = "parameters";

  /** The custom components section holding each resource's {@code x-rspace-filter} catalogue. */
  static final String FILTERS = "x-rspace-filters";

  private ApiV2OpenApiQueryParameters() {}

  /** The parameters of a standard operation, as references to components. */
  static List<Map<String, Object>> parameters(
      ResourceOperation operation,
      String component,
      ApiV2OpenApiComponents components,
      ResourceSchema schema,
      Map<String, ResourceSchema> resourceSchemas,
      ResourceRegistry registry,
      List<Map<String, Object>> runtimeFields,
      ApiV2OpenApiText text) {
    List<Map<String, Object>> parameters = new ArrayList<>();
    if (operation == ResourceOperation.READ
        || operation == ResourceOperation.UPDATE
        || operation == ResourceOperation.DELETE) {
      parameters.add(components.reference(PARAMETERS, component + "Id", idParameter(schema, text)));
    }
    if (operation == ResourceOperation.LIST) {
      parameters.add(
          components.reference(
              PARAMETERS,
              component + "Where",
              where(
                  schema,
                  false,
                  component,
                  components,
                  resourceSchemas,
                  registry,
                  runtimeFields,
                  text)));
      Map<String, Object> sort =
          parameter(
              "sort",
              "query",
              false,
              ordered("type", "string"),
              text.get(Key.PARAMETER_SORT, CollectionQueryLimits.MAX_SORT_FIELDS));
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
      parameters.add(components.reference(PARAMETERS, "Page", pageParameter(text)));
      parameters.add(
          components.reference(PARAMETERS, "Limit", limitParameter(text.get(Key.PARAMETER_LIMIT))));
      parameters.add(
          components.reference(
              PARAMETERS,
              "Totals",
              parameter(
                  "totals",
                  "query",
                  false,
                  ordered("type", "boolean", "default", false),
                  text.get(Key.PARAMETER_TOTALS))));
    }
    if (operation == ResourceOperation.LIST || operation == ResourceOperation.COUNT) {
      parameters.add(
          components.reference(
              PARAMETERS,
              "Max",
              parameter(
                  "max",
                  "query",
                  false,
                  ordered("type", "integer", "minimum", 1, "maximum", 10000),
                  text.get(Key.PARAMETER_MAX))));
    }
    if (operation == ResourceOperation.COUNT
        || operation == ResourceOperation.BULK_UPDATE
        || operation == ResourceOperation.BULK_DELETE) {
      boolean required = operation != ResourceOperation.COUNT;
      parameters.add(
          components.reference(
              PARAMETERS,
              component + (required ? "WhereRequired" : "Where"),
              where(
                  schema,
                  required,
                  component,
                  components,
                  resourceSchemas,
                  registry,
                  runtimeFields,
                  text)));
    }
    if (operation == ResourceOperation.LIST || operation == ResourceOperation.READ) {
      parameters.add(
          components.reference(
              PARAMETERS,
              "Depth",
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
                  text.get(Key.PARAMETER_DEPTH))));
      parameters.add(
          components.reference(
              PARAMETERS,
              component + "Fields",
              fieldset(
                  "fields",
                  schema,
                  resourceSchemas,
                  text.get(Key.PARAMETER_FIELDS, schema.name()))));
      parameters.add(
          components.reference(
              PARAMETERS,
              component + "Exclude",
              fieldset(
                  "exclude",
                  schema,
                  resourceSchemas,
                  text.get(Key.PARAMETER_EXCLUDE, schema.name()))));
    }
    return parameters;
  }

  private static Map<String, Object> where(
      ResourceSchema schema,
      boolean required,
      String component,
      ApiV2OpenApiComponents components,
      Map<String, ResourceSchema> resourceSchemas,
      ResourceRegistry registry,
      List<Map<String, Object>> runtimeFields,
      ApiV2OpenApiText text) {
    Map<String, Object> parameter =
        parameter(
            "where",
            "query",
            required,
            ordered("type", "string", "maxLength", CollectionQueryLimits.MAX_WHERE_LENGTH),
            text.get(
                runtimeFields.isEmpty()
                    ? Key.PARAMETER_WHERE
                    : Key.PARAMETER_WHERE_WITH_RUNTIME_FIELDS));
    parameter.put(
        "x-rspace-filter",
        components.reference(
            FILTERS,
            component,
            ApiV2OpenApiFilterExtension.filterExtension(schema, resourceSchemas, registry, text)));
    if (!runtimeFields.isEmpty()) {
      parameter.put("x-rspace-runtime-fields", runtimeFields);
    }
    return parameter;
  }

  private static Map<String, Object> fieldset(
      String name,
      ResourceSchema resource,
      Map<String, ResourceSchema> resourceSchemas,
      String description) {
    Set<String> reachable = new LinkedHashSet<>();
    reachable.add(resource.name());
    List<ResourceSchema> pending = new ArrayList<>();
    pending.add(resource);
    for (int index = 0; index < pending.size(); index++) {
      for (RelationshipSchema relationship : pending.get(index).relationships()) {
        for (String targetName : relationship.targetResources()) {
          if (reachable.add(targetName)) {
            ResourceSchema target = resourceSchemas.get(targetName);
            if (target != null) {
              pending.add(target);
            }
          }
        }
      }
    }
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
        fieldsetParameter(
            name,
            ordered("type", "object", "additionalProperties", false, "properties", properties),
            description);
    parameter.put("x-rspace-allowed-fields", allowedFields);
    return parameter;
  }

  static Map<String, Object> fieldset(String name, ApiV2OpenApiText text) {
    return fieldsetParameter(
        name,
        ordered("type", "object", "additionalProperties", ordered("type", "string")),
        text.get(Key.PARAMETER_GENERIC_FIELDSET, name));
  }

  private static Map<String, Object> fieldsetParameter(
      String name, Map<String, Object> schema, String description) {
    Map<String, Object> parameter = parameter(name, "query", false, schema, description);
    parameter.put("style", "deepObject");
    parameter.put("explode", true);
    parameter.put("x-rspace-comma-separated-values", true);
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

  static Map<String, Object> idParameter(ResourceSchema schema, ApiV2OpenApiText text) {
    return parameter(
        "id",
        "path",
        true,
        scalarSchema(field(schema, schema.idField()).type(), false),
        text.get(Key.PARAMETER_ID));
  }

  static Map<String, Object> ifMatchParameter(String description) {
    return ifMatchParameter(true, description);
  }

  static Map<String, Object> ifMatchParameter(boolean required, String description) {
    return parameter(
        "If-Match",
        "header",
        required,
        ordered("type", "string", "pattern", "^\\\"[0-9]+\\\"$"),
        description);
  }

  static Map<String, Object> pageParameter(ApiV2OpenApiText text) {
    return parameter(
        "page",
        "query",
        false,
        ordered("type", "integer", "minimum", 1, "default", 1),
        text.get(Key.PARAMETER_PAGE));
  }

  static Map<String, Object> limitParameter(String description) {
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
        description);
  }
}
