package com.researchspace.api.v2.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Relationship;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.CollectionFieldType.InputKind;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import com.researchspace.model.collection.DocumentValidationException.Violation;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipInputForm;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict create and update body parser driven by a collection description.
 *
 * <p>Only fields writable for the requested operation are accepted. The returned document preserves
 * the difference between an omitted value and an explicit null. Create parsing also supplies
 * configured defaults and enforces required fields.
 */
public final class ApiV2DocumentParser {

  private ApiV2DocumentParser() {}

  public static <T> ParsedDocument parse(
      JsonNode body,
      CollectionDescription<T> description,
      WriteOperation operation,
      String errorKey,
      AccessContext context) {
    if (body == null
        || !body.isObject()
        || (operation == WriteOperation.UPDATE && body.isEmpty())) {
      throw new DocumentValidationException(
          errorKey, List.of(new Violation(null, Reason.INVALID_DOCUMENT)));
    }

    Map<String, Object> values = new LinkedHashMap<>();
    List<Violation> violations = new ArrayList<>();
    body.fieldNames()
        .forEachRemaining(
            name -> {
              Field<T, ?> field;
              try {
                field = description.requireField(name);
              } catch (CollectionQueryException ex) {
                relationshipValue(
                    body.get(name), description, name, operation, context, values, violations);
                return;
              }
              // Same violation whether the field is structurally not writable or this caller may
              // not write it, so a caller cannot tell "read-only" from "not yours" and probe.
              if (!field.writableOn(operation, context)) {
                violations.add(new Violation(name, Reason.READ_ONLY));
                return;
              }
              Object value = value(body.get(name), field, violations);
              if (violations.stream().noneMatch(violation -> name.equals(violation.field()))) {
                values.put(name, value);
              }
            });
    // Structural writability only, deliberately ignoring this caller's write access: defaults are
    // server-supplied rather than caller input, and a required field the caller may not write is a
    // misconfiguration that should surface loudly as REQUIRED instead of silently vanishing.
    if (operation == WriteOperation.CREATE) {
      description.fields().stream()
          .filter(field -> field.writableOn(WriteOperation.CREATE))
          .forEach(
              field -> {
                if (body.has(field.name())) {
                  return;
                }
                if (field.hasDefaultValue()) {
                  values.put(field.name(), field.defaultValue());
                } else if (field.isRequiredOnCreate()) {
                  violations.add(new Violation(field.name(), Reason.REQUIRED));
                }
              });
      description.relationships().stream()
          .filter(relationship -> relationship.writableOn(WriteOperation.CREATE))
          .filter(Relationship::isRequiredOnCreate)
          .filter(relationship -> !body.has(relationship.name()))
          .forEach(
              relationship -> violations.add(new Violation(relationship.name(), Reason.REQUIRED)));
    }
    if (!violations.isEmpty()) {
      throw new DocumentValidationException(errorKey, violations);
    }
    return new ParsedDocument(operation, values);
  }

  private static <T> void relationshipValue(
      JsonNode node,
      CollectionDescription<T> description,
      String name,
      WriteOperation operation,
      AccessContext context,
      Map<String, Object> values,
      List<Violation> violations) {
    Relationship<T> relationship;
    try {
      relationship = description.requireRelationship(name);
    } catch (CollectionQueryException ex) {
      violations.add(new Violation(name, Reason.UNKNOWN_FIELD));
      return;
    }
    if (!relationship.writableOn(operation, context)) {
      violations.add(new Violation(name, Reason.READ_ONLY));
      return;
    }
    Object value = relationshipValue(node, relationship, operation, violations);
    if (violations.stream().noneMatch(violation -> name.equals(violation.field()))) {
      values.put(name, value);
    }
  }

  private static Object relationshipValue(
      JsonNode node,
      Relationship<?> relationship,
      WriteOperation operation,
      List<Violation> violations) {
    if (node.isNull()) {
      if (!relationship.nullable()) {
        violations.add(new Violation(relationship.name(), Reason.NULL_NOT_ALLOWED));
      }
      return null;
    }
    try {
      if (node.isObject() && relationship.acceptsInput(operation, RelationshipInputForm.OBJECT)) {
        return objectReference(node, relationship);
      }
      if (node.isTextual()
          && relationship.acceptsInput(operation, RelationshipInputForm.GLOBAL_ID)) {
        return relationship.parseGlobalReference(node.textValue());
      }
      violations.add(new Violation(relationship.name(), Reason.WRONG_TYPE));
    } catch (RuntimeException ex) {
      violations.add(new Violation(relationship.name(), Reason.INVALID_VALUE));
    }
    return null;
  }

  private static ResourceReference<?, ?> objectReference(
      JsonNode node, Relationship<?> relationship) {
    if (node.size() != 2
        || !node.has("relationTo")
        || !node.get("relationTo").isTextual()
        || !node.has("value")
        || node.get("value").isNull()
        || !hasExpectedKind(node.get("value"), relationship.idType().inputKind())) {
      throw new IllegalArgumentException("Invalid relationship reference");
    }
    RelationshipTarget<?> target = relationship.requireTarget(node.get("relationTo").textValue());
    Object id = relationship.idType().parse(node.get("value").asText());
    return new ResourceReference<>(target.storedKind(), id);
  }

  private static Object value(JsonNode node, Field<?, ?> field, List<Violation> violations) {
    if (node.isNull()) {
      if (!field.nullable()) {
        violations.add(new Violation(field.name(), Reason.NULL_NOT_ALLOWED));
      }
      return null;
    }
    if (!hasExpectedKind(node, field.type().inputKind())) {
      violations.add(new Violation(field.name(), Reason.WRONG_TYPE));
      return null;
    }
    try {
      return field.parse(node.asText());
    } catch (RuntimeException ex) {
      violations.add(new Violation(field.name(), Reason.INVALID_VALUE));
      return null;
    }
  }

  private static boolean hasExpectedKind(JsonNode node, InputKind kind) {
    return switch (kind) {
      case STRING -> node.isTextual();
      case NUMBER -> node.isNumber();
      case BOOLEAN -> node.isBoolean();
    };
  }
}
