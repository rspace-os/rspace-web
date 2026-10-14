package com.researchspace.model.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared declaration, replacement parsing and output projection for embedded object fields. */
public final class EmbeddedObjects {
  private static final ObjectMapper JSON = new ObjectMapper();

  private EmbeddedObjects() {}

  public static void validateDeclaration(CollectionFieldType<?> type) {
    validateDeclaration(type, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  static void validateDeclaration(
      CollectionFieldType.InputKind kind, List<? extends Field<?, ?>> fields) {
    if (kind != CollectionFieldType.InputKind.OBJECT
        && kind != CollectionFieldType.InputKind.ARRAY) {
      throw new IllegalArgumentException("Declared children require an object or array");
    }
    validateChildren(fields, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static void validateDeclaration(
      CollectionFieldType<?> type, Set<CollectionFieldType<?>> active) {
    if (type.inputKind() == CollectionFieldType.InputKind.ARRAY
        || type instanceof DeclaredFieldType<?>) {
      if (!(type instanceof DeclaredFieldType<?> declared)) {
        throw new IllegalArgumentException("Array fields must declare children and a row adapter");
      }
      if (type.inputKind() != CollectionFieldType.InputKind.OBJECT
          && type.inputKind() != CollectionFieldType.InputKind.ARRAY) {
        throw new IllegalArgumentException("Declared fields require an object or array type");
      }
      if (type.inputKind() == CollectionFieldType.InputKind.ARRAY
          && type.javaType() != List.class) {
        throw new IllegalArgumentException("Declared arrays must use List values");
      }
      if (!active.add(type)) {
        throw new IllegalArgumentException("Cyclic embedded field declarations are unsupported");
      }
      if (!declared.operators().isEmpty() || declared.sortable()) {
        throw new IllegalArgumentException("Embedded values cannot be filtered or sorted");
      }
      validateRowType(declared.rowType());
      validateChildren(type.fields(), active);
      active.remove(type);
    } else if (!type.fields().isEmpty()) {
      throw new IllegalArgumentException("Scalar fields cannot declare children");
    }
  }

  static void validateRowType(Class<?> rowType) {
    Objects.requireNonNull(rowType, "Embedded row type");
    if (rowType.isArray()
        || Iterable.class.isAssignableFrom(rowType)
        || rowType.isPrimitive()
        || rowType == Object.class
        || rowType == Boolean.class
        || rowType == Character.class
        || rowType.isEnum()
        || rowType == String.class
        || Number.class.isAssignableFrom(rowType)) {
      throw new IllegalArgumentException("Embedded rows must be objects");
    }
  }

  private static void validateChildren(
      List<? extends Field<?, ?>> fields, Set<CollectionFieldType<?>> active) {
    if (fields == null || fields.isEmpty()) {
      throw new IllegalArgumentException("Embedded objects require declared children");
    }
    Set<String> names = new LinkedHashSet<>();
    for (Object member : fields) {
      if (!(member instanceof Field<?, ?> field)) {
        throw new IllegalArgumentException("Embedded relationship children are unsupported");
      }
      if (!names.add(field.name())) {
        throw new IllegalArgumentException("Embedded child names must be unique");
      }
      if (!field.embedded() || field.declaresQueryCapabilities()) {
        throw new IllegalArgumentException(
            "Embedded children cannot declare queries or entity bindings");
      }
      if (field.type().inputKind() == CollectionFieldType.InputKind.OBJECT
          && !(field.type() instanceof DeclaredFieldType<?>)) {
        throw new IllegalArgumentException("Embedded object children must declare their fields");
      }
      validateDeclaration(field.type(), active);
    }
  }

  /** Parses complete rows before running row adapters and whole-value validators. */
  public static Object parse(DeclaredFieldType<?> type, JsonNode value, WriteOperation operation) {
    return parse(type, value, operation, "");
  }

  private static Object parse(
      DeclaredFieldType<?> type, JsonNode value, WriteOperation operation, String path) {
    if (value == null
        || (type.inputKind() == CollectionFieldType.InputKind.ARRAY
            ? !value.isArray()
            : !value.isObject())) {
      throw failure(path, Reason.WRONG_TYPE);
    }
    Object parsed;
    if (type.inputKind() == CollectionFieldType.InputKind.ARRAY) {
      List<Object> rows = new ArrayList<>();
      for (int index = 0; index < value.size(); index++) {
        rows.add(parseRow(type, value.get(index), operation, path + "[" + index + "]"));
      }
      parsed = List.copyOf(rows);
    } else {
      parsed = parseRow(type, value, operation, path);
    }
    output(type, parsed, null, false, false, path);
    return parsed;
  }

  private static Object parseRow(
      DeclaredFieldType<?> type, JsonNode node, WriteOperation operation, String path) {
    if (!node.isObject()) {
      throw failure(path, Reason.WRONG_TYPE);
    }
    Map<String, Field<?, ?>> children = children(type);
    node.fieldNames()
        .forEachRemaining(
            name -> {
              if (!children.containsKey(name)) {
                throw failure(childPath(path, name), Reason.UNKNOWN_FIELD);
              }
            });
    Map<String, Object> values = new LinkedHashMap<>();
    for (Field<?, ?> child : type.fields()) {
      String childPath = childPath(path, child.name());
      if (node.has(child.name())) {
        if (!child.writableOn(operation)) {
          throw failure(childPath, Reason.READ_ONLY);
        }
        values.put(child.name(), parseChild(child, node.get(child.name()), operation, childPath));
      } else if (child.hasDefaultValue() && child.writableOn(operation)) {
        values.put(child.name(), child.defaultValue());
      } else if (child.isRequiredOnCreate()) {
        throw failure(childPath, Reason.REQUIRED);
      }
    }
    try {
      return type.rowType()
          .cast(
              Objects.requireNonNull(
                  type.assemble(Collections.unmodifiableMap(values)), "Embedded row"));
    } catch (EmbeddedFieldException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw failure(path, Reason.INVALID_VALUE);
    }
  }

  private static Object parseChild(
      Field<?, ?> field, JsonNode node, WriteOperation operation, String path) {
    if (node.isNull()) {
      if (!field.nullable()) {
        throw failure(path, Reason.NULL_NOT_ALLOWED);
      }
      return null;
    }
    if (!field.type().accepts(node)) {
      throw failure(path, Reason.WRONG_TYPE);
    }
    try {
      if (field.type() instanceof DeclaredFieldType<?> declared) {
        return parse(declared, node, operation, path);
      }
      return field.validateValue(field.type().parseScalar(node));
    } catch (EmbeddedFieldException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw failure(path, Reason.INVALID_VALUE);
    }
  }

  /** Checks only supplied child keys, after the parent document has entered the access context. */
  public static void authorize(
      DeclaredFieldType<?> type, JsonNode value, WriteOperation operation, AccessContext context) {
    authorize(type, value, operation, context, "");
  }

  private static void authorize(
      DeclaredFieldType<?> type,
      JsonNode value,
      WriteOperation operation,
      AccessContext context,
      String path) {
    if (value.isNull()) {
      return;
    }
    if (type.inputKind() == CollectionFieldType.InputKind.ARRAY) {
      for (int index = 0; index < value.size(); index++) {
        authorizeRow(type, value.get(index), operation, context, path + "[" + index + "]");
      }
    } else {
      authorizeRow(type, value, operation, context, path);
    }
  }

  private static void authorizeRow(
      DeclaredFieldType<?> type,
      JsonNode value,
      WriteOperation operation,
      AccessContext context,
      String path) {
    for (Field<?, ?> child : type.fields()) {
      if (!value.has(child.name())) {
        continue;
      }
      String childPath = childPath(path, child.name());
      if (!child.writableOn(operation, context)) {
        throw failure(childPath, Reason.READ_ONLY);
      }
      if (child.type() instanceof DeclaredFieldType<?> nested) {
        authorize(nested, value.get(child.name()), operation, context, childPath);
      }
    }
  }

  /** Reads declared DTO properties, validates their wire values, and applies child read gates. */
  public static Object serialize(DeclaredFieldType<?> type, Object value, AccessContext context) {
    return output(type, value, context, true, false, "").document();
  }

  /** Projects an alternate serialized value through the same declared-child bounds. */
  public static Object project(DeclaredFieldType<?> type, Object value, AccessContext context) {
    return output(type, value, context, true, true, "").document();
  }

  /** Validates defaults without allowing caller access to conceal malformed children. */
  public static void validateValue(DeclaredFieldType<?> type, Object value) {
    output(type, value, null, false, false, "");
  }

  /** Produces a complete, allowlisted default without caller-specific read restrictions. */
  public static Object defaultDocument(DeclaredFieldType<?> type, Object value) {
    return output(type, value, null, false, false, "").document();
  }

  private static RowOutput output(
      DeclaredFieldType<?> type,
      Object value,
      AccessContext context,
      boolean access,
      boolean projected,
      String path) {
    if (value instanceof JsonNode node) {
      value = JSON.convertValue(node, Object.class);
    }
    if (type.inputKind() != CollectionFieldType.InputKind.ARRAY) {
      RowOutput row = outputRow(type, value, context, access, projected, path);
      if (row.row() != null) {
        validate(type, row.row(), path);
      }
      return row;
    }
    if (!(value instanceof List<?> rows)) {
      throw failure(path, Reason.WRONG_TYPE);
    }
    List<Object> parsed = new ArrayList<>();
    List<Object> documents = new ArrayList<>();
    boolean complete = true;
    for (int index = 0; index < rows.size(); index++) {
      RowOutput row =
          outputRow(type, rows.get(index), context, access, projected, path + "[" + index + "]");
      complete &= row.row() != null;
      parsed.add(row.row());
      documents.add(row.document());
    }
    if (complete) {
      validate(type, parsed, path);
    } else {
      checkRowBounds(type, rows.size(), path);
    }
    return new RowOutput(List.copyOf(documents), complete ? List.copyOf(parsed) : null);
  }

  private static RowOutput outputRow(
      DeclaredFieldType<?> type,
      Object value,
      AccessContext context,
      boolean access,
      boolean projected,
      String path) {
    boolean wire = value instanceof Map<?, ?>;
    if (value == null || value instanceof List<?> || (!wire && !type.rowType().isInstance(value))) {
      throw failure(path, Reason.WRONG_TYPE);
    }
    if (wire && !access) {
      for (Object name : ((Map<?, ?>) value).keySet()) {
        if (!children(type).containsKey(name)) {
          throw failure(childPath(path, String.valueOf(name)), Reason.UNKNOWN_FIELD);
        }
      }
    }
    Map<String, Object> document = new LinkedHashMap<>();
    Map<String, Object> parsed = new LinkedHashMap<>();
    boolean complete = true;
    for (Field<?, ?> child : type.fields()) {
      String childPath = childPath(path, child.name());
      boolean readable = !access || child.readAccess().allowsField(context);
      Object raw = wire ? ((Map<?, ?>) value).get(child.name()) : read(child, value);
      boolean present =
          wire ? ((Map<?, ?>) value).containsKey(child.name()) : raw != null || child.nullable();
      if (!present) {
        if (child.isRequiredOnCreate()) {
          if (!projected || readable) {
            throw failure(childPath, Reason.REQUIRED);
          }
          complete = false;
        }
        continue;
      }
      if (raw == null) {
        if (!child.nullable()) {
          throw failure(childPath, Reason.NULL_NOT_ALLOWED);
        }
        parsed.put(child.name(), null);
        if (readable) {
          document.put(child.name(), null);
        }
        continue;
      }
      Object serialized;
      Object childValue;
      if (child.type() instanceof DeclaredFieldType<?> nested) {
        RowOutput nestedOutput = output(nested, raw, context, access, projected, childPath);
        serialized = nestedOutput.document();
        childValue = nestedOutput.row();
        complete &= childValue != null;
      } else {
        try {
          serialized = wire ? raw : serializeScalar(child.type(), raw);
          JsonNode node = JSON.valueToTree(serialized);
          if (!child.type().accepts(node)) {
            throw failure(childPath, Reason.WRONG_TYPE);
          }
          childValue = child.validateValue(child.type().parseScalar(node));
        } catch (EmbeddedFieldException ex) {
          throw ex;
        } catch (RuntimeException ex) {
          throw failure(childPath, Reason.INVALID_VALUE);
        }
      }
      parsed.put(child.name(), childValue);
      if (readable) {
        document.put(child.name(), serialized);
      }
    }
    Object row = value;
    if (wire) {
      row = complete ? assembleOutput(type, parsed, path) : null;
    } else {
      try {
        type.validateRow(row);
      } catch (RuntimeException ex) {
        throw failure(path, Reason.INVALID_VALUE);
      }
    }
    return new RowOutput(Collections.unmodifiableMap(document), row);
  }

  private static Object assembleOutput(
      DeclaredFieldType<?> type, Map<String, Object> parsed, String path) {
    try {
      Object row =
          type.rowType()
              .cast(
                  Objects.requireNonNull(
                      type.assemble(Collections.unmodifiableMap(parsed)), "Embedded row"));
      type.validateRow(row);
      return row;
    } catch (RuntimeException ex) {
      throw failure(path, Reason.INVALID_VALUE);
    }
  }

  private static void checkRowBounds(DeclaredFieldType<?> type, int count, String path) {
    Object minimum = type.writeConstraints().get("minItems");
    Object maximum = type.writeConstraints().get("maxItems");
    if ((minimum instanceof Number minimumNumber && count < minimumNumber.intValue())
        || (maximum instanceof Number maximumNumber && count > maximumNumber.intValue())) {
      throw failure(path, Reason.INVALID_VALUE);
    }
  }

  private static <V> Object serializeScalar(CollectionFieldType<V> type, Object value) {
    return type.serialize(type.javaType().cast(value));
  }

  private static <T> Object read(Field<T, ?> field, Object row) {
    @SuppressWarnings("unchecked")
    T typed = (T) row;
    return field.reader.apply(typed);
  }

  private static <V> void validate(CollectionFieldType<V> type, Object value, String path) {
    try {
      checkRowBoundsIfArray(type, value, path);
      type.validate(type.javaType().cast(value));
    } catch (RuntimeException ex) {
      throw failure(path, Reason.INVALID_VALUE);
    }
  }

  private static void checkRowBoundsIfArray(
      CollectionFieldType<?> type, Object value, String path) {
    if (type instanceof DeclaredFieldType<?> declared
        && type.inputKind() == CollectionFieldType.InputKind.ARRAY) {
      checkRowBounds(declared, ((List<?>) value).size(), path);
    }
  }

  private static Map<String, Field<?, ?>> children(CollectionFieldType<?> type) {
    Map<String, Field<?, ?>> children = new LinkedHashMap<>();
    type.fields().forEach(field -> children.put(field.name(), field));
    return children;
  }

  private static String childPath(String path, String name) {
    return path.isEmpty() ? name : path + "." + name;
  }

  private static EmbeddedFieldException failure(String path, Reason reason) {
    return new EmbeddedFieldException(path, reason);
  }

  private record RowOutput(Object document, Object row) {}
}
