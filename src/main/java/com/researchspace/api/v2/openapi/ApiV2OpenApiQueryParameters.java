package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.field;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResourceDocumentBuilder.scalarSchema;

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

  private ApiV2OpenApiQueryParameters() {}

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
      parameters.add(idParameter(schema));
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
      parameters.add(
          parameter(
              "totals",
              "query",
              false,
              ordered("type", "boolean", "default", false),
              "Include totalDocs and totalIsLowerBound. Defaults to false."));
    }
    if (operation == ResourceOperation.LIST || operation == ResourceOperation.COUNT) {
      parameters.add(
          parameter(
              "max",
              "query",
              false,
              ordered("type", "integer", "minimum", 1, "maximum", 10000),
              "Cap requested totals at max. When exceeded, totalDocs equals max and"
                  + " totalIsLowerBound is true. On list routes, requires totals=true."));
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
                + " x-rspace-filter. For ID selectors on relationships whose targets are all users,"
                + " exact me resolves to the authenticated effective subject's numeric ID; for"
                + " example, createdBy.value==me. Scalar user-id selectors may explicitly opt into"
                + " the same alias."
                + (runtimeFields.isEmpty()
                    ? ""
                    : " Further per-caller selectors are listed by the catalog named in"
                        + " x-rspace-runtime-fields."));
    parameter.put(
        "x-rspace-filter",
        ApiV2OpenApiFilterExtension.filterExtension(schema, resourceSchemas, registry));
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

  static Map<String, Object> idParameter(ResourceSchema schema) {
    return parameter(
        "id",
        "path",
        true,
        scalarSchema(field(schema, schema.idField()).type(), false),
        "Resource identifier.");
  }

  static Map<String, Object> ifMatchParameter(String description) {
    return parameter(
        "If-Match",
        "header",
        true,
        ordered("type", "string", "pattern", "^\\\"[0-9]+\\\"$"),
        description);
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
}
