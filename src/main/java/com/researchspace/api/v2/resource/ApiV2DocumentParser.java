package com.researchspace.api.v2.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Relationship;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.CollectionFieldType.InputKind;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import com.researchspace.model.collection.DocumentValidationException.Violation;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipInputForm;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.service.CollectionMutationException;
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

  public static <T> List<ParsedDocument> parseMany(
      JsonNode body, CollectionDescription<T> description, String errorKey, AccessContext context) {
    return parseMany(body, description, errorKey, context, true);
  }

  static <T> List<ParsedDocument> parseManyStructure(
      JsonNode body,
      CollectionDescription<T> description,
      String errorKey,
      AccessContext context,
      int maxDocuments) {
    return parseMany(body, description, errorKey, context, false, maxDocuments);
  }

  private static <T> List<ParsedDocument> parseMany(
      JsonNode body,
      CollectionDescription<T> description,
      String errorKey,
      AccessContext context,
      boolean authorizeFields) {
    return parseMany(
        body,
        description,
        errorKey,
        context,
        authorizeFields,
        CollectionMutationLimits.MAX_BULK_CREATE_ROWS);
  }

  private static <T> List<ParsedDocument> parseMany(
      JsonNode body,
      CollectionDescription<T> description,
      String errorKey,
      AccessContext context,
      boolean authorizeFields,
      int maxDocuments) {
    if (body == null
        || !body.isObject()
        || body.size() != 1
        || !body.has("docs")
        || !body.get("docs").isArray()
        || body.get("docs").isEmpty()) {
      throw new DocumentValidationException(
          errorKey, List.of(new Violation("docs", Reason.INVALID_DOCUMENT)));
    }
    if (body.get("docs").size() > maxDocuments) {
      throw new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT);
    }

    List<ParsedDocument> documents = new ArrayList<>();
    int index = 0;
    for (JsonNode document : body.get("docs")) {
      try {
        documents.add(
            parse(
                document, description, WriteOperation.CREATE, errorKey, context, authorizeFields));
      } catch (DocumentValidationException ex) {
        throw prefixed(ex, "docs[" + index + "]");
      }
      index++;
    }
    return List.copyOf(documents);
  }

  static DocumentValidationException prefixed(
      DocumentValidationException exception, String prefix) {
    return new DocumentValidationException(
        exception.getErrorKey(),
        exception.getViolations().stream()
            .map(
                violation ->
                    new Violation(
                        violation.field() == null ? prefix : prefix + "." + violation.field(),
                        violation.reason()))
            .toList());
  }

  public static <T> ParsedDocument parse(
      JsonNode body,
      CollectionDescription<T> description,
      WriteOperation operation,
      String errorKey,
      AccessContext context) {
    return parse(body, description, operation, errorKey, context, true);
  }

  static <T> ParsedDocument parseStructure(
      JsonNode body,
      CollectionDescription<T> description,
      WriteOperation operation,
      String errorKey,
      AccessContext context) {
    return parse(body, description, operation, errorKey, context, false);
  }

  private static <T> ParsedDocument parse(
      JsonNode body,
      CollectionDescription<T> description,
      WriteOperation operation,
      String errorKey,
      AccessContext context,
      boolean authorizeFields) {
    if (body == null
        || !body.isObject()
        || (operation == WriteOperation.UPDATE && body.isEmpty())) {
      throw new DocumentValidationException(
          errorKey, List.of(new Violation(null, Reason.INVALID_DOCUMENT)));
    }

    Map<String, Object> values = new LinkedHashMap<>();
    List<Violation> violations = new ArrayList<>();
    body.fields()
        .forEachRemaining(
            entry -> {
              String name = entry.getKey();
              var field = description.findField(name);
              if (field.isEmpty()) {
                relationshipValue(
                    entry.getValue(), description, name, operation, values, violations);
                return;
              }
              Field<T, ?> describedField = field.orElseThrow();
              if (!describedField.writableOn(operation)) {
                violations.add(new Violation(name, Reason.READ_ONLY));
                return;
              }
              int violationCount = violations.size();
              Object value = value(entry.getValue(), describedField, violations);
              if (violations.size() == violationCount) {
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
    ParsedDocument parsed = new ParsedDocument(operation, values);
    if (authorizeFields) {
      authorizeFields(body, description, operation, errorKey, context.withInput(parsed));
    }
    return parsed;
  }

  static <T> void authorizeFields(
      JsonNode body,
      CollectionDescription<T> description,
      WriteOperation operation,
      String errorKey,
      AccessContext inputContext) {
    List<Violation> violations = new ArrayList<>();
    body.fields()
        .forEachRemaining(
            entry -> {
              String name = entry.getKey();
              var field = description.findField(name);
              if (field.isPresent()) {
                if (!field.orElseThrow().writableOn(operation, inputContext)) {
                  violations.add(new Violation(name, Reason.READ_ONLY));
                }
                return;
              }
              description
                  .findRelationship(name)
                  .ifPresent(
                      relationship -> {
                        if (!relationship.writableOn(operation, inputContext)) {
                          violations.add(new Violation(name, Reason.READ_ONLY));
                        }
                      });
              // The structural pass already recorded an unknown field.
            });
    if (!violations.isEmpty()) {
      throw new DocumentValidationException(errorKey, violations);
    }
  }

  static <T> void authorizeManyFields(
      JsonNode body,
      List<ParsedDocument> documents,
      CollectionDescription<T> description,
      String errorKey,
      AccessContext context) {
    for (int index = 0; index < documents.size(); index++) {
      try {
        authorizeFields(
            body.get("docs").get(index),
            description,
            WriteOperation.CREATE,
            errorKey,
            context.withInput(documents.get(index)));
      } catch (DocumentValidationException ex) {
        throw prefixed(ex, "docs[" + index + "]");
      }
    }
  }

  private static <T> void relationshipValue(
      JsonNode node,
      CollectionDescription<T> description,
      String name,
      WriteOperation operation,
      Map<String, Object> values,
      List<Violation> violations) {
    var relationship = description.findRelationship(name);
    if (relationship.isEmpty()) {
      violations.add(new Violation(name, Reason.UNKNOWN_FIELD));
      return;
    }
    Relationship<T> describedRelationship = relationship.orElseThrow();
    if (!describedRelationship.writableOn(operation)) {
      violations.add(new Violation(name, Reason.READ_ONLY));
      return;
    }
    int violationCount = violations.size();
    Object value = relationshipValue(node, describedRelationship, operation, violations);
    if (violations.size() == violationCount) {
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
    if ((node.size() != 2 && node.size() != 3)
        || !node.has("relationTo")
        || !node.get("relationTo").isTextual()
        || !node.has("value")
        || node.get("value").isNull()
        || !hasExpectedKind(node.get("value"), relationship.idType().inputKind())
        || (node.size() == 3 && (!node.has("globalId") || !node.get("globalId").isTextual()))) {
      throw new IllegalArgumentException("Invalid relationship reference");
    }
    RelationshipTarget<?> target = relationship.requireTarget(node.get("relationTo").textValue());
    Object id = relationship.idType().parse(node.get("value").asText());
    if (node.has("globalId")) {
      String prefix = target.globalIdPrefix();
      if (prefix == null || !node.get("globalId").textValue().equals(prefix + id)) {
        throw new IllegalArgumentException(
            "Relationship global ID does not match its target and ID");
      }
    }
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
      case OBJECT -> node.isObject();
      case ARRAY -> node.isArray();
    };
  }
}
