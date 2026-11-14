package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.putIfNotNull;
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
      boolean routable,
      ApiV2OpenApiText text) {
    String component = names.get(resource.name());
    components.put(component + "Reference", referenceSchema(resource));
    components.put(component + "Read", documentSchema(resource, names, null, text, components));
    if (routable) {
      components.put(
          component + "Create",
          documentSchema(resource, names, WriteOperation.CREATE, text, components));
      components.put(
          component + "Update",
          documentSchema(resource, names, WriteOperation.UPDATE, text, components));
    }
  }

  static void addStandardSchemas(Map<String, Object> schemas) {
    ApiV2OpenApiSchemas.responseSchemaFor(ApiV2Problem.class, schemas);
    ApiV2OpenApiSchemas.responseSchemaFor(ApiV2CountResult.class, schemas);
    ApiV2OpenApiSchemas.responseSchemaFor(ApiV2AuditEvent.class, schemas);
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
      ResourceSchema resource,
      Map<String, String> names,
      WriteOperation writeOperation,
      ApiV2OpenApiText text,
      Map<String, Object> components) {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (ResourceFieldSchema field : resource.documentFields()) {
      if (writeOperation != null && !field.writeOperations().contains(writeOperation)) {
        continue;
      }
      Map<String, Object> property =
          documentFieldProperty(field, names, writeOperation, false, false, text, components);
      properties.put(field.name(), property);
      if ((writeOperation == WriteOperation.CREATE && field.requiredOnCreate())
          || (writeOperation == null && field.name().equals(resource.idField()))) {
        required.add(field.name());
      }
    }
    Map<String, Object> schema = closedObjectSchema(properties, required);
    schema.put(
        "x-rspace-access",
        ApiV2OpenApiAccessPolicies.extension(access(resource.access(), writeOperation)));
    return schema;
  }

  private static Map<String, Object> documentFieldSchema(
      ResourceFieldSchema field,
      Map<String, String> names,
      WriteOperation writeOperation,
      ApiV2OpenApiText text,
      Map<String, Object> components) {
    Map<String, Object> schema;
    if (field instanceof FieldSchema scalar
        && scalar.fields().isEmpty()
        && scalar.type().objectType() != null) {
      schema =
          new LinkedHashMap<>(
              ApiV2OpenApiSchemas.responseSchemaFor(scalar.type().objectType(), components));
    } else if (field instanceof FieldSchema scalar) {
      schema = scalarSchema(scalar.type(), false);
      // Only body properties carry write constraints: where literals are never checked against
      // them, so the shared scalar schema must not publish them.
      schema.putAll(scalar.writeConstraints());
      if (!scalar.fields().isEmpty()) {
        Map<String, Object> object =
            embeddedObjectSchema(scalar.fields(), names, writeOperation, text, components);
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
                  ? ApiV2OpenApiRelationshipSchemas.relationshipOutput(relationship, names)
                  : ApiV2OpenApiRelationshipSchemas.relationshipInput(
                      relationship, writeOperation, names, text));
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
      boolean readOnlyBeforeAccess,
      ApiV2OpenApiText text,
      Map<String, Object> components) {
    Map<String, Object> property =
        documentFieldSchema(field, names, writeOperation, text, components);
    applyDocumentation(property, field.openApi());
    if (writeOperation == null && field instanceof RelationshipSchema relationship) {
      Map<String, Object> targets = new LinkedHashMap<>();
      relationship
          .targetResources()
          .forEach(target -> targets.put(target, ref(names.get(target) + "Read")));
      property.put("x-rspace-relationship", ordered("targets", targets));
    }
    if ((writeOperation == WriteOperation.CREATE
            || (updateDefaults && writeOperation == WriteOperation.UPDATE))
        && field.defaultValue() != null) {
      property.put("default", field.defaultValue());
    }
    if (readOnlyBeforeAccess && writeOperation == null && field.readOnly()) {
      property.put("readOnly", true);
    }
    property.put(
        "x-rspace-access", ApiV2OpenApiAccessPolicies.extension(access(field, writeOperation)));
    if (!readOnlyBeforeAccess && writeOperation == null && field.readOnly()) {
      property.put("readOnly", true);
    }
    return property;
  }

  private static Map<String, Object> embeddedObjectSchema(
      List<FieldSchema> fields,
      Map<String, String> names,
      WriteOperation operation,
      ApiV2OpenApiText text,
      Map<String, Object> components) {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (FieldSchema child : fields) {
      if (operation != null && !child.writeOperations().contains(operation)) {
        continue;
      }
      Map<String, Object> property =
          documentFieldProperty(child, names, operation, true, true, text, components);
      properties.put(child.name(), property);
      if (operation != null && child.requiredOnCreate()) {
        required.add(child.name());
      }
    }
    return closedObjectSchema(properties, required);
  }

  private static Map<String, Object> closedObjectSchema(
      Map<String, Object> properties, List<String> required) {
    Map<String, Object> schema =
        ordered("type", "object", "additionalProperties", false, "properties", properties);
    if (!required.isEmpty()) {
      schema.put("required", required);
    }
    return schema;
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

  static FieldSchema field(ResourceSchema resource, String name) {
    return resource.fields().stream()
        .filter(field -> field.name().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
