package com.researchspace.model.collection;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.Getter;

/** One typed scalar attribute exposed by a collection. */
public final class Field<T, V> {

  private final String name;
  private final String property;
  private final CollectionFieldType<V> type;
  private final boolean filterable;
  private final boolean sortable;
  final Function<T, V> reader;
  private final BiConsumer<T, V> writer;
  private final Set<WriteOperation> writeOperations;
  @Getter private final boolean requiredOnCreate;
  private final boolean nullable;
  private final V defaultValue;
  private final AccessFunction readAccess;
  private final AccessFunction createAccess;
  private final AccessFunction updateAccess;
  private final OpenApiSchemaDocumentation openApi;

  private Field(
      String name,
      String property,
      CollectionFieldType<V> type,
      boolean filterable,
      boolean sortable,
      Function<T, V> reader,
      BiConsumer<T, V> writer,
      Set<WriteOperation> writeOperations,
      boolean requiredOnCreate,
      boolean nullable,
      V defaultValue,
      AccessFunction readAccess,
      AccessFunction createAccess,
      AccessFunction updateAccess,
      OpenApiSchemaDocumentation openApi) {
    this.name = requireName(name, "Field name");
    this.property = requireName(property, "Field property");
    this.type = Objects.requireNonNull(type, "Field type");
    this.filterable = filterable;
    this.sortable = sortable;
    this.reader = Objects.requireNonNull(reader, "Field reader");
    this.writer = writer;
    this.writeOperations = immutableOperations(writeOperations);
    this.requiredOnCreate = requiredOnCreate;
    this.nullable = nullable;
    this.defaultValue = defaultValue;
    this.readAccess = AccessFunction.requireDocumentedOrInherited(readAccess);
    this.createAccess = AccessFunction.requireDocumentedOrInherited(createAccess);
    this.updateAccess = AccessFunction.requireDocumentedOrInherited(updateAccess);
    this.openApi = Objects.requireNonNull(openApi, "OpenAPI field documentation");
    validateConfiguration();
  }

  public static <T, V> Field<T, V> readOnly(
      String name, String property, CollectionFieldType<V> type, Function<T, V> reader) {
    return new Field<>(
        name,
        property,
        type,
        true,
        true,
        reader,
        null,
        Set.of(),
        false,
        false,
        null,
        AccessFunction.inherited(),
        AccessFunction.inherited(),
        AccessFunction.inherited(),
        OpenApiSchemaDocumentation.EMPTY);
  }

  public static <T, V> Field<T, V> writable(
      String name,
      String property,
      CollectionFieldType<V> type,
      Function<T, V> reader,
      BiConsumer<T, V> writer) {
    return new Field<>(
        name,
        property,
        type,
        true,
        true,
        reader,
        Objects.requireNonNull(writer, "Field writer"),
        EnumSet.allOf(WriteOperation.class),
        false,
        false,
        null,
        AccessFunction.inherited(),
        AccessFunction.inherited(),
        AccessFunction.inherited(),
        OpenApiSchemaDocumentation.EMPTY);
  }

  /**
   * Restricts who may read this field. Denial omits the value from output and makes the field
   * invalid as a {@code where}/{@code sort} target; it never narrows which rows are returned.
   */
  public Field<T, V> readableBy(AccessFunction access) {
    return copyAccess(
        Objects.requireNonNull(access, "Field read access"), createAccess, updateAccess, openApi);
  }

  public AccessFunction readAccess() {
    return readAccess;
  }

  /**
   * Applies the same caller restriction to create and update, mirroring {@link #readableBy}.
   *
   * <p>Orthogonal to {@link #writeOnlyOn}, which says on which operations the field is writable at
   * all. This says who may perform such a write. Denial produces exactly the error a field that is
   * not writable at all produces, so a caller cannot tell "no such writable field" from "not yours"
   * and probe for names.
   *
   * <p>Prefer {@link #creatableBy} or {@link #updatableBy} when the operations differ.
   */
  public Field<T, V> writableBy(AccessFunction access) {
    return copyAccess(
        readAccess, Objects.requireNonNull(access, "Field write access"), access, openApi);
  }

  public Field<T, V> creatableBy(AccessFunction access) {
    return copyAccess(
        readAccess, Objects.requireNonNull(access, "Field create access"), updateAccess, openApi);
  }

  public Field<T, V> updatableBy(AccessFunction access) {
    return copyAccess(
        readAccess, createAccess, Objects.requireNonNull(access, "Field update access"), openApi);
  }

  public Field<T, V> documented(OpenApiSchemaDocumentation documentation) {
    return copyAccess(
        readAccess,
        createAccess,
        updateAccess,
        Objects.requireNonNull(documentation, "OpenAPI field documentation"));
  }

  public AccessFunction writeAccess(WriteOperation operation) {
    return operation == WriteOperation.CREATE ? createAccess : updateAccess;
  }

  /** Whether this caller may write this field on this operation. */
  public boolean writableOn(WriteOperation operation, AccessContext context) {
    return writableOn(operation) && writeAccess(operation).allowsField(context);
  }

  public Field<T, V> required() {
    return copy(writeOperations, true, nullable, filterable, sortable);
  }

  public Field<T, V> allowNull() {
    return copy(writeOperations, requiredOnCreate, true, filterable, sortable);
  }

  /**
   * Supplies this fixed value when the field is omitted from a create document. Dynamic defaults
   * are intentionally not supported yet.
   */
  public Field<T, V> defaultValue(V value) {
    V checked = type.javaType().cast(Objects.requireNonNull(value, "Field default value"));
    return copy(writeOperations, requiredOnCreate, nullable, filterable, sortable, checked);
  }

  public Field<T, V> writeOnlyOn(WriteOperation... operations) {
    Objects.requireNonNull(operations, "Write operations");
    Set<WriteOperation> selected = EnumSet.noneOf(WriteOperation.class);
    Collections.addAll(selected, operations);
    return copy(selected, requiredOnCreate, nullable, filterable, sortable);
  }

  /**
   * Declares whether clients may use this field in {@code where} and {@code sort}.
   *
   * <p>Public because a programmatically described field can be derived rather than persistent: a
   * value read from a {@code @Transient} accessor has no column to query, so it must be readable
   * without being queryable.
   */
  public Field<T, V> withQueryCapabilities(boolean filterable, boolean sortable) {
    return copy(writeOperations, requiredOnCreate, nullable, filterable, sortable);
  }

  public String name() {
    return name;
  }

  public String property() {
    return property;
  }

  public CollectionFieldType<V> type() {
    return type;
  }

  public FieldSchema schema(AccessPolicy inherited) {
    return new FieldSchema(
        name,
        property,
        type.schema(),
        requiredOnCreate,
        nullable,
        writeOperations.isEmpty(),
        writeOperations,
        defaultValue == null ? null : type.serialize(defaultValue),
        operators(),
        supportsWildcards(),
        sortable(),
        openApi,
        CollectionDescription.documented(readAccess, inherited.readAccess()),
        CollectionDescription.documented(createAccess, inherited.createAccess()),
        CollectionDescription.documented(updateAccess, inherited.updateAccess()));
  }

  public Set<Operator> operators() {
    return filterable ? type.operators() : Set.of();
  }

  public boolean supportsWildcards() {
    return filterable && type.supportsWildcards();
  }

  public boolean sortable() {
    return sortable && type.sortable();
  }

  public FilterSelector<T> filterSelector() {
    return new FilterSelector.Property<>(name, property, type);
  }

  public boolean writableOn(WriteOperation operation) {
    return writeOperations.contains(operation);
  }

  public boolean nullable() {
    return nullable;
  }

  public boolean hasDefaultValue() {
    return defaultValue != null;
  }

  public V defaultValue() {
    return defaultValue;
  }

  public Object parse(String value) {
    return type.parse(value);
  }

  Object documentValue(T entity) {
    V value = reader.apply(entity);
    return value == null ? null : type.serialize(value);
  }

  void write(T entity, Object value) {
    if (writer == null) {
      throw new IllegalStateException("Cannot write read-only field " + name);
    }
    writer.accept(entity, validateValue(value));
  }

  V validateValue(Object value) {
    if (value == null) {
      if (!nullable) {
        throw new IllegalArgumentException("Field " + name + " does not accept null");
      }
      return null;
    }
    return type.javaType().cast(value);
  }

  private Field<T, V> copy(
      Set<WriteOperation> operations,
      boolean required,
      boolean acceptsNull,
      boolean filterable,
      boolean sortable) {
    return copy(operations, required, acceptsNull, filterable, sortable, defaultValue);
  }

  private Field<T, V> copy(
      Set<WriteOperation> operations,
      boolean required,
      boolean acceptsNull,
      boolean filterable,
      boolean sortable,
      V suppliedDefault) {
    return new Field<>(
        name,
        property,
        type,
        filterable,
        sortable,
        reader,
        writer,
        operations,
        required,
        acceptsNull,
        suppliedDefault,
        readAccess,
        createAccess,
        updateAccess,
        openApi);
  }

  private Field<T, V> copyAccess(
      AccessFunction reads,
      AccessFunction creates,
      AccessFunction updates,
      OpenApiSchemaDocumentation documentation) {
    return new Field<>(
        name,
        property,
        type,
        filterable,
        sortable,
        reader,
        writer,
        writeOperations,
        requiredOnCreate,
        nullable,
        defaultValue,
        reads,
        creates,
        updates,
        documentation);
  }

  private void validateConfiguration() {
    Objects.requireNonNull(type.javaType(), "Field Java type");
    Objects.requireNonNull(type.inputKind(), "Field input kind");
    Set.copyOf(Objects.requireNonNull(type.operators(), "Field operators"));
    if (writer == null && !writeOperations.isEmpty()) {
      throw new IllegalArgumentException("Writable field requires a writer");
    }
    if (writer != null && writeOperations.isEmpty()) {
      throw new IllegalArgumentException("Field writer requires a write operation");
    }
    if (requiredOnCreate && !writeOperations.contains(WriteOperation.CREATE)) {
      throw new IllegalArgumentException("Required field must be writable on create");
    }
    if (requiredOnCreate && nullable) {
      throw new IllegalArgumentException("Required field must not accept null");
    }
    if (nullable && type.javaType().isPrimitive()) {
      throw new IllegalArgumentException("Nullable field must use a reference type");
    }
    if (defaultValue != null && !writeOperations.contains(WriteOperation.CREATE)) {
      throw new IllegalArgumentException("Default value requires a create-writable field");
    }
  }

  static Set<WriteOperation> immutableOperations(Set<WriteOperation> operations) {
    Objects.requireNonNull(operations, "Write operations");
    return operations.isEmpty()
        ? Set.of()
        : Collections.unmodifiableSet(EnumSet.copyOf(operations));
  }

  private static String requireName(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
