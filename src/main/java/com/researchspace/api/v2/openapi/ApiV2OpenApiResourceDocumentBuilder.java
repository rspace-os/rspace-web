package com.researchspace.api.v2.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.api.v2.model.ApiV2CountResult;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessPolicySchema;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.FieldSchema;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import com.researchspace.model.collection.RelationshipInputForm;
import com.researchspace.model.collection.RelationshipSchema;
import com.researchspace.model.collection.ResourceFieldSchema;
import com.researchspace.model.collection.ResourceSchema;
import com.researchspace.model.collection.WriteOperation;
import io.swagger.v3.core.util.Json31;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ApiV2OpenApiResourceDocumentBuilder {

  private static AccessDocumentation access(AccessPolicySchema access, WriteOperation operation) {
    return access(access.readAccess(), access.createAccess(), access.updateAccess(), operation);
  }

  private static AccessDocumentation access(ResourceFieldSchema field, WriteOperation operation) {
    return access(field.readAccess(), field.createAccess(), field.updateAccess(), operation);
  }

  private static AccessDocumentation access(
      AccessDocumentation read,
      AccessDocumentation create,
      AccessDocumentation update,
      WriteOperation operation) {
    return operation == null ? read : operation == WriteOperation.CREATE ? create : update;
  }

  static void addSchemas(
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

  static void addStandardSchemas(Map<String, Object> schemas) {
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
      Map<String, Object> property =
          documentFieldProperty(field, names, writeOperation, false, false);
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
    schema.put("x-rspace-access", accessExtension(access(resource.access(), writeOperation)));
    return schema;
  }

  private static Map<String, Object> documentFieldSchema(
      ResourceFieldSchema field, Map<String, String> names, WriteOperation writeOperation) {
    Map<String, Object> schema;
    if (field instanceof FieldSchema scalar) {
      schema = scalarSchema(scalar.type(), false);
      // Only body properties carry write constraints: where literals are never checked against
      // them, so the shared scalar schema must not publish them.
      schema.putAll(scalar.writeConstraints());
      if (!scalar.fields().isEmpty()) {
        Map<String, Object> object = embeddedObjectSchema(scalar.fields(), names, writeOperation);
        if ("array".equals(scalar.type().jsonType())) {
          schema.put("items", object);
        } else {
          schema.putAll(object);
        }
        schema.put("x-rspace-fields", scalar.fields());
      }
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

  private static Map<String, Object> documentFieldProperty(
      ResourceFieldSchema field,
      Map<String, String> names,
      WriteOperation writeOperation,
      boolean updateDefaults,
      boolean readOnlyBeforeAccess) {
    Map<String, Object> property = documentFieldSchema(field, names, writeOperation);
    applyDocumentation(property, field.openApi());
    if ((writeOperation == WriteOperation.CREATE
            || (updateDefaults && writeOperation == WriteOperation.UPDATE))
        && field.defaultValue() != null) {
      property.put("default", field.defaultValue());
    }
    if (readOnlyBeforeAccess && writeOperation == null && field.readOnly()) {
      property.put("readOnly", true);
    }
    property.put("x-rspace-access", accessExtension(access(field, writeOperation)));
    if (!readOnlyBeforeAccess && writeOperation == null && field.readOnly()) {
      property.put("readOnly", true);
    }
    return property;
  }

  private static Map<String, Object> embeddedObjectSchema(
      List<FieldSchema> fields, Map<String, String> names, WriteOperation operation) {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (FieldSchema child : fields) {
      if (operation != null && !child.writeOperations().contains(operation)) {
        continue;
      }
      Map<String, Object> property = documentFieldProperty(child, names, operation, true, true);
      properties.put(child.name(), property);
      if (operation != null && child.requiredOnCreate()) {
        required.add(child.name());
      }
    }
    Map<String, Object> schema =
        ordered("type", "object", "additionalProperties", false, "properties", properties);
    if (!required.isEmpty()) {
      schema.put("required", required);
    }
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
    return ordered("oneOf", variants);
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

  /**
   * The swagger converter renders a nullable reference property as {@code {"type": "null", "$ref":
   * ...}}, which no value satisfies in OpenAPI 3.1. Rewrites each such property as {@code anyOf}
   * the reference and {@code null}, keeping its other keywords such as the description.
   */
  static Object withNullableReferenceProperties(Object schema) {
    if (!(schema instanceof Map<?, ?> component)
        || !(component.get("properties") instanceof Map<?, ?> properties)
        || properties.values().stream().noneMatch(ApiV2OpenApiGenerator::isNullableReference)) {
      return schema;
    }
    Map<String, Object> rewritten = new LinkedHashMap<>();
    properties.forEach(
        (name, property) ->
            rewritten.put(
                (String) name,
                isNullableReference(property)
                    ? nullableReference((Map<?, ?>) property)
                    : property));
    Map<String, Object> result = new LinkedHashMap<>();
    component.forEach((key, value) -> result.put((String) key, value));
    result.put("properties", rewritten);
    return result;
  }

  static boolean isNullableReference(Object property) {
    return property instanceof Map<?, ?> map
        && map.get("$ref") instanceof String
        && ("null".equals(map.get("type"))
            || map.get("type") instanceof List<?> types && types.contains("null"));
  }

  private static Map<String, Object> nullableReference(Map<?, ?> property) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("anyOf", List.of(ordered("$ref", property.get("$ref")), ordered("type", "null")));
    property.forEach(
        (key, value) -> {
          if (!"$ref".equals(key) && !"type".equals(key)) {
            result.put((String) key, value);
          }
        });
    return result;
  }

  private static Map<String, Object> nullable(Map<String, Object> schema, boolean nullable) {
    if (!nullable) {
      return schema;
    }
    if (schema.get("type") instanceof String type) {
      Map<String, Object> result = new LinkedHashMap<>(schema);
      result.put("type", List.of(type, "null"));
      if (result.get("enum") instanceof List<?> values) {
        // The enum lists every accepted value, so a nullable one must accept null as well.
        List<Object> withNull = new ArrayList<>(values);
        withNull.add(null);
        result.put("enum", Collections.unmodifiableList(withNull));
      }
      return result;
    }
    return ordered("anyOf", List.of(schema, ordered("type", "null")));
  }

  static Map<String, Object> scalarSchema(CollectionFieldType.Schema type, boolean nullable) {
    return nullable(nonNullScalarSchema(type), nullable);
  }

  private static Map<String, Object> nonNullScalarSchema(CollectionFieldType.Schema type) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", type.jsonType());
    if (type.format() != null) {
      schema.put("format", type.format());
    }
    if (type.maxLength() != null) {
      schema.put("maxLength", type.maxLength());
    }
    schema.putAll(type.structure());
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
    putIfNotNull(schema, "format", documentation.format());
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
        case "object", "array" -> Json31.mapper().readValue(value, Object.class);
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
    } catch (JsonProcessingException | NumberFormatException ex) {
      throw new IllegalArgumentException(
          "OpenAPI value '" + value + "' is not a valid " + type, ex);
    }
  }

  static void putIfNotNull(Map<String, Object> target, String name, Object value) {
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

  static FieldSchema field(ResourceSchema resource, String name) {
    return resource.fields().stream()
        .filter(field -> field.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  static String escapeEcmaRegex(String value) {
    StringBuilder escaped = new StringBuilder();
    for (char character : value.toCharArray()) {
      if ("\\^$.*+?()[]{}|/".indexOf(character) >= 0) {
        escaped.append('\\');
      }
      escaped.append(character);
    }
    return escaped.toString();
  }
}
