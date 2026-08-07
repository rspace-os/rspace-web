package com.researchspace.model.collection;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;

/**
 * Immutable description of the fields and default ordering shared by a collection's adapters.
 *
 * <p>This is the single server-owned allowlist for a collection. It maps public field names to
 * fixed model properties and carries, per field, everything the HTTP and persistence adapters need:
 * a typed field definition, write rules, and entity access. Fields are held in declaration order so
 * generated documents and writes have a stable order.
 *
 * <p>Property names are server-owned model properties, never values supplied by a request. The
 * allowlist is deliberately explicit rather than derived from the entity, so a newly persisted
 * property stays invisible to the API until someone describes it here.
 *
 * @param <T> the persistent entity this collection exposes
 */
public final class CollectionDescription<T> {

  public enum WriteOperation {
    CREATE,
    UPDATE
  }

  public enum Operator {
    EQUAL,
    NOT_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    IN,
    NOT_IN,
    CONTAINS,
    LIKE,
    EXISTS
  }

  public enum RelationshipCardinality {
    TO_ONE,
    TO_MANY
  }

  /** One typed scalar attribute exposed by a collection. */
  public static final class Field<T, V> {

    private final String name;
    private final String property;
    private final CollectionFieldType<V> type;
    private final boolean filterable;
    private final boolean sortable;
    private final Function<T, V> reader;
    private final BiConsumer<T, V> writer;
    private final Set<WriteOperation> writeOperations;
    @Getter private final boolean requiredOnCreate;
    private final boolean nullable;
    private final Supplier<? extends V> defaultValue;
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
        Supplier<? extends V> defaultValue,
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
          Objects.requireNonNull(access, "Field read access"),
          createAccess,
          updateAccess,
          openApi);
    }

    public AccessFunction readAccess() {
      return readAccess;
    }

    /**
     * Applies the same caller restriction to create and update, mirroring {@link #readableBy}.
     *
     * <p>Orthogonal to {@link #writeOnlyOn}, which says on which operations the field is writable
     * at all. This says who may perform such a write. Denial produces exactly the error a field
     * that is not writable at all produces, so a caller cannot tell "no such writable field" from
     * "not yours" and probe for names.
     *
     * <p>Prefer {@link #creatableBy} or {@link #updatableBy} when the operations differ.
     */
    public Field<T, V> writableBy(AccessFunction access) {
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
          readAccess,
          Objects.requireNonNull(access, "Field write access"),
          access,
          openApi);
    }

    public Field<T, V> creatableBy(AccessFunction access) {
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
          readAccess,
          Objects.requireNonNull(access, "Field create access"),
          updateAccess,
          openApi);
    }

    public Field<T, V> updatableBy(AccessFunction access) {
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
          readAccess,
          createAccess,
          Objects.requireNonNull(access, "Field update access"),
          openApi);
    }

    public Field<T, V> documented(OpenApiSchemaDocumentation documentation) {
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
      return copy(writeOperations, true, nullable, defaultValue, filterable, sortable);
    }

    public Field<T, V> allowNull() {
      return copy(writeOperations, requiredOnCreate, true, defaultValue, filterable, sortable);
    }

    public Field<T, V> defaultValue(Supplier<? extends V> value) {
      return copy(
          writeOperations,
          requiredOnCreate,
          nullable,
          Objects.requireNonNull(value),
          filterable,
          sortable);
    }

    public Field<T, V> writeOnlyOn(WriteOperation... operations) {
      Objects.requireNonNull(operations, "Write operations");
      Set<WriteOperation> selected = EnumSet.noneOf(WriteOperation.class);
      Collections.addAll(selected, operations);
      return copy(selected, requiredOnCreate, nullable, defaultValue, filterable, sortable);
    }

    private Field<T, V> withQueryCapabilities(boolean filterable, boolean sortable) {
      return copy(writeOperations, requiredOnCreate, nullable, defaultValue, filterable, sortable);
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
          hasDefaultValue(),
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

    public FilterSelector filterSelector() {
      return new FilterSelector.Property(name, property, type);
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

    public Object defaultValue() {
      if (defaultValue == null) {
        return null;
      }
      V value = defaultValue.get();
      if (value == null && !nullable) {
        throw new IllegalStateException("Non-nullable field default returned null");
      }
      return value == null ? null : type.javaType().cast(value);
    }

    public Object parse(String value) {
      return type.parse(value);
    }

    private Object documentValue(T entity) {
      V value = reader.apply(entity);
      return value == null ? null : type.serialize(value);
    }

    private void write(T entity, Object value) {
      if (writer == null) {
        throw new IllegalStateException("Cannot write read-only field " + name);
      }
      writer.accept(entity, validateValue(value));
    }

    private V validateValue(Object value) {
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
        Supplier<? extends V> suppliedDefault,
        boolean filterable,
        boolean sortable) {
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

    private static Set<WriteOperation> immutableOperations(Set<WriteOperation> operations) {
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

  /** One relationship to another registered resource. */
  public static final class Relationship<T> {

    private final String name;
    private final CollectionFieldType<?> idType;
    private final List<RelationshipTarget<?>> targets;
    private final RelationshipCardinality cardinality;
    private final Function<T, ?> reader;
    private final SplitReferenceBinding<T, ?, ?> binding;
    private final Set<WriteOperation> writeOperations;
    private final Map<WriteOperation, Set<RelationshipInputForm>> inputForms;
    @Getter private final boolean requiredOnCreate;
    private final boolean nullable;
    private final AccessFunction readAccess;
    private final AccessFunction writeAccess;
    private final boolean selfReferenceAllowed;
    private final OpenApiSchemaDocumentation openApi;

    private Relationship(
        String name,
        CollectionFieldType<?> idType,
        List<RelationshipTarget<?>> targets,
        RelationshipCardinality cardinality,
        Function<T, ?> reader,
        SplitReferenceBinding<T, ?, ?> binding,
        Set<WriteOperation> writeOperations,
        Map<WriteOperation, Set<RelationshipInputForm>> inputForms,
        boolean requiredOnCreate,
        boolean nullable,
        AccessFunction readAccess,
        AccessFunction writeAccess,
        boolean selfReferenceAllowed,
        OpenApiSchemaDocumentation openApi) {
      this.name = requireText(name, "Relationship name");
      this.idType = idType;
      this.targets = validateTargets(targets);
      this.cardinality = Objects.requireNonNull(cardinality, "Relationship cardinality");
      this.reader = Objects.requireNonNull(reader, "Relationship reader");
      this.binding = binding;
      this.writeOperations = Field.immutableOperations(writeOperations);
      this.inputForms = immutableInputForms(inputForms);
      this.requiredOnCreate = requiredOnCreate;
      this.nullable = nullable;
      this.readAccess = AccessFunction.requireDocumentedOrInherited(readAccess);
      this.writeAccess = AccessFunction.requireDocumentedOrInherited(writeAccess);
      this.selfReferenceAllowed = selfReferenceAllowed;
      this.openApi = Objects.requireNonNull(openApi, "OpenAPI relationship documentation");
      validateConfiguration();
    }

    public static <T, R> Relationship<T> toOne(
        String name, String targetResource, Function<T, R> reader) {
      return legacy(name, targetResource, RelationshipCardinality.TO_ONE, reader);
    }

    public static <T, R> Relationship<T> toMany(
        String name, String targetResource, Function<T, ? extends Collection<R>> reader) {
      return legacy(name, targetResource, RelationshipCardinality.TO_MANY, reader);
    }

    /** Creates a read-only to-one reference whose target collection is fixed. */
    public static <T, R, ID> Relationship<T> referenceToOne(
        String name,
        String targetResource,
        CollectionFieldType<ID> idType,
        Class<R> targetType,
        Function<T, R> reader,
        Function<R, ID> idReader,
        String idProperty) {
      Objects.requireNonNull(reader, "Relationship reader");
      Objects.requireNonNull(idReader, "Relationship ID reader");
      SplitReferenceBinding<T, String, ID> binding =
          SplitReferenceBinding.monomorphic(
              entity -> {
                R target = reader.apply(entity);
                return target == null
                    ? null
                    : new ResourceReference<>(targetResource, idReader.apply(target));
              },
              idProperty);
      return new Relationship<>(
          name,
          Objects.requireNonNull(idType, "Relationship ID type"),
          List.of(new RelationshipTarget<>(targetResource, targetResource, targetType)),
          RelationshipCardinality.TO_ONE,
          binding::read,
          binding,
          Set.of(),
          Map.of(),
          false,
          true,
          AccessFunction.inherited(),
          AccessFunction.inherited(),
          false,
          OpenApiSchemaDocumentation.EMPTY);
    }

    public static <T, K, ID> Relationship<T> polymorphicToOne(
        String name,
        CollectionFieldType<ID> idType,
        List<RelationshipTarget<K>> targets,
        SplitReferenceBinding<T, K, ID> binding) {
      Objects.requireNonNull(binding, "Reference binding");
      return new Relationship<>(
          name,
          Objects.requireNonNull(idType, "Relationship ID type"),
          List.copyOf(targets),
          RelationshipCardinality.TO_ONE,
          binding::read,
          binding,
          EnumSet.allOf(WriteOperation.class),
          Map.of(
              WriteOperation.CREATE,
              Set.of(RelationshipInputForm.OBJECT),
              WriteOperation.UPDATE,
              Set.of(RelationshipInputForm.OBJECT)),
          false,
          false,
          AccessFunction.inherited(),
          AccessFunction.inherited(),
          false,
          OpenApiSchemaDocumentation.EMPTY);
    }

    private static <T> Relationship<T> legacy(
        String name,
        String targetResource,
        RelationshipCardinality cardinality,
        Function<T, ?> reader) {
      RelationshipTarget<String> target =
          new RelationshipTarget<>(targetResource, targetResource, null, Object.class);
      return new Relationship<>(
          name,
          null,
          List.of(target),
          cardinality,
          reader,
          null,
          Set.of(),
          Map.of(),
          false,
          true,
          AccessFunction.inherited(),
          AccessFunction.inherited(),
          false,
          OpenApiSchemaDocumentation.EMPTY);
    }

    public Relationship<T> required() {
      return copy(
          writeOperations, inputForms, true, false, readAccess, writeAccess, selfReferenceAllowed);
    }

    public Relationship<T> allowNull() {
      return copy(
          writeOperations,
          inputForms,
          requiredOnCreate,
          true,
          readAccess,
          writeAccess,
          selfReferenceAllowed);
    }

    public Relationship<T> writeOnlyOn(WriteOperation... operations) {
      Objects.requireNonNull(operations, "Write operations");
      Set<WriteOperation> selected = EnumSet.noneOf(WriteOperation.class);
      Collections.addAll(selected, operations);
      Map<WriteOperation, Set<RelationshipInputForm>> selectedForms = new HashMap<>();
      selected.forEach(operation -> selectedForms.put(operation, inputForms.get(operation)));
      return copy(
          selected,
          selectedForms,
          requiredOnCreate,
          nullable,
          readAccess,
          writeAccess,
          selfReferenceAllowed);
    }

    public Relationship<T> acceptGlobalIdOn(WriteOperation... operations) {
      Objects.requireNonNull(operations, "Write operations");
      Map<WriteOperation, Set<RelationshipInputForm>> forms = new HashMap<>(inputForms);
      for (WriteOperation operation : operations) {
        if (!writeOperations.contains(operation)) {
          throw new IllegalArgumentException("Input form requires a writable operation");
        }
        Set<RelationshipInputForm> selected = EnumSet.copyOf(forms.get(operation));
        selected.add(RelationshipInputForm.GLOBAL_ID);
        forms.put(operation, selected);
      }
      return copy(
          writeOperations,
          forms,
          requiredOnCreate,
          nullable,
          readAccess,
          writeAccess,
          selfReferenceAllowed);
    }

    public Relationship<T> readableBy(AccessFunction access) {
      return copy(
          writeOperations,
          inputForms,
          requiredOnCreate,
          nullable,
          Objects.requireNonNull(access, "Relationship read access"),
          writeAccess,
          selfReferenceAllowed);
    }

    public Relationship<T> writableBy(AccessFunction access) {
      return copy(
          writeOperations,
          inputForms,
          requiredOnCreate,
          nullable,
          readAccess,
          Objects.requireNonNull(access, "Relationship write access"),
          selfReferenceAllowed);
    }

    public Relationship<T> allowSelfReference() {
      return copy(
          writeOperations, inputForms, requiredOnCreate, nullable, readAccess, writeAccess, true);
    }

    public Relationship<T> documented(OpenApiSchemaDocumentation documentation) {
      return new Relationship<>(
          name,
          idType,
          targets,
          cardinality,
          reader,
          binding,
          writeOperations,
          inputForms,
          requiredOnCreate,
          nullable,
          readAccess,
          writeAccess,
          selfReferenceAllowed,
          Objects.requireNonNull(documentation, "OpenAPI relationship documentation"));
    }

    public String name() {
      return name;
    }

    /** Retained for monomorphic relationship callers. */
    public String targetResource() {
      if (targets.size() != 1) {
        throw new IllegalStateException("Polymorphic relationship has more than one target");
      }
      return targets.get(0).resourceName();
    }

    public CollectionFieldType<?> idType() {
      if (idType == null) {
        throw new IllegalStateException("Legacy relationship has no reference ID type");
      }
      return idType;
    }

    public List<RelationshipTarget<?>> targets() {
      return targets;
    }

    public RelationshipTarget<?> requireTarget(String resourceName) {
      return targets.stream()
          .filter(target -> target.resourceName().equals(resourceName))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unsupported relationship target"));
    }

    public RelationshipTarget<?> targetForKind(Object kind) {
      return targets.stream()
          .filter(target -> target.storedKind().equals(kind))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unsupported relationship kind"));
    }

    public ResourceReference<?, ?> parseGlobalReference(String value) {
      if (idType == null || value == null || !GlobalIdentifier.isValid(value)) {
        throw new IllegalArgumentException("Invalid relationship global ID");
      }
      GlobalIdentifier identifier = new GlobalIdentifier(value);
      if (identifier.hasVersionId()) {
        throw new IllegalArgumentException("Versioned relationship global ID is not supported");
      }
      RelationshipTarget<?> target =
          targets.stream()
              .filter(candidate -> identifier.getPrefix().name().equals(candidate.globalIdPrefix()))
              .findFirst()
              .orElseThrow(
                  () -> new IllegalArgumentException("Unsupported relationship global ID"));
      return new ResourceReference<>(
          target.storedKind(), idType.parse(String.valueOf(identifier.getDbId())));
    }

    public RelationshipCardinality cardinality() {
      return cardinality;
    }

    public SplitReferenceBinding<T, ?, ?> binding() {
      if (binding == null) {
        throw new IllegalStateException("Legacy relationship has no split reference binding");
      }
      return binding;
    }

    public boolean hasBinding() {
      return binding != null;
    }

    public boolean writableOn(WriteOperation operation) {
      return writeOperations.contains(operation);
    }

    public boolean writableOn(WriteOperation operation, AccessContext context) {
      return writableOn(operation) && writeAccess.allowsField(context);
    }

    public boolean acceptsInput(WriteOperation operation, RelationshipInputForm inputForm) {
      return inputForms.getOrDefault(operation, Set.of()).contains(inputForm);
    }

    public Set<RelationshipInputForm> inputForms(WriteOperation operation) {
      return inputForms.getOrDefault(operation, Set.of());
    }

    public boolean nullable() {
      return nullable;
    }

    public AccessFunction readAccess() {
      return readAccess;
    }

    public AccessFunction writeAccess() {
      return writeAccess;
    }

    public boolean selfReferenceAllowed() {
      return selfReferenceAllowed;
    }

    public List<FilterSelector> filterSelectors() {
      if (binding == null) {
        return List.of();
      }
      List<FilterSelector> selectors = new ArrayList<>();
      if (targets.stream().allMatch(target -> target.globalIdPrefix() != null)) {
        selectors.add(
            new FilterSelector.RelationshipPart(
                name, this, FilterSelector.RelationshipComponent.ROOT));
      }
      if (binding.hasKindProperty()) {
        selectors.add(
            new FilterSelector.RelationshipPart(
                name + ".relationTo", this, FilterSelector.RelationshipComponent.KIND));
      }
      selectors.add(
          new FilterSelector.RelationshipPart(
              name + ".value", this, FilterSelector.RelationshipComponent.ID));
      return List.copyOf(selectors);
    }

    Object read(T entity) {
      return reader.apply(entity);
    }

    private Relationship<T> copy(
        Set<WriteOperation> operations,
        Map<WriteOperation, Set<RelationshipInputForm>> forms,
        boolean required,
        boolean acceptsNull,
        AccessFunction reads,
        AccessFunction writes,
        boolean permitsSelf) {
      return new Relationship<>(
          name,
          idType,
          targets,
          cardinality,
          reader,
          binding,
          operations,
          forms,
          required,
          acceptsNull,
          reads,
          writes,
          permitsSelf,
          openApi);
    }

    private void validateConfiguration() {
      if (binding == null) {
        if (idType != null || !writeOperations.isEmpty() || !inputForms.isEmpty()) {
          throw new IllegalArgumentException("Legacy relationship cannot accept reference input");
        }
        return;
      }
      if (cardinality != RelationshipCardinality.TO_ONE) {
        throw new IllegalArgumentException("Writable polymorphic relationships must be to-one");
      }
      Objects.requireNonNull(idType.javaType(), "Relationship ID Java type");
      if (requiredOnCreate && !writeOperations.contains(WriteOperation.CREATE)) {
        throw new IllegalArgumentException("Required relationship must be writable on create");
      }
      if (requiredOnCreate && nullable) {
        throw new IllegalArgumentException("Required relationship must not accept null");
      }
      if (!inputForms.keySet().equals(writeOperations)) {
        throw new IllegalArgumentException("Each writable operation needs an input form");
      }
      if (inputForms.values().stream().anyMatch(Set::isEmpty)) {
        throw new IllegalArgumentException("Writable relationship input forms must not be empty");
      }
      if (inputForms.values().stream()
          .anyMatch(forms -> forms.contains(RelationshipInputForm.GLOBAL_ID))) {
        boolean missingPrefix =
            targets.stream().anyMatch(target -> target.globalIdPrefix() == null);
        if (missingPrefix) {
          throw new IllegalArgumentException("Global-ID input requires prefixes for all targets");
        }
      }
    }

    private static List<RelationshipTarget<?>> validateTargets(
        List<RelationshipTarget<?>> targets) {
      List<RelationshipTarget<?>> copy = List.copyOf(targets);
      if (copy.isEmpty()) {
        throw new IllegalArgumentException("Relationship must have a target");
      }
      Set<String> resources = new LinkedHashSet<>();
      Set<Object> kinds = new LinkedHashSet<>();
      Set<String> prefixes = new LinkedHashSet<>();
      for (RelationshipTarget<?> target : copy) {
        Objects.requireNonNull(target, "Relationship target");
        if (!resources.add(target.resourceName()) || !kinds.add(target.storedKind())) {
          throw new IllegalArgumentException("Duplicate relationship target mapping");
        }
        if (target.globalIdPrefix() != null && !prefixes.add(target.globalIdPrefix())) {
          throw new IllegalArgumentException("Duplicate relationship global ID prefix");
        }
      }
      return copy;
    }

    private static Map<WriteOperation, Set<RelationshipInputForm>> immutableInputForms(
        Map<WriteOperation, Set<RelationshipInputForm>> inputForms) {
      Objects.requireNonNull(inputForms, "Relationship input forms");
      Map<WriteOperation, Set<RelationshipInputForm>> copy = new HashMap<>();
      inputForms.forEach(
          (operation, forms) ->
              copy.put(
                  Objects.requireNonNull(operation, "Write operation"),
                  Set.copyOf(Objects.requireNonNull(forms, "Relationship input forms"))));
      return Map.copyOf(copy);
    }
  }

  public record FieldSchema(
      String name,
      String property,
      CollectionFieldType.Schema type,
      boolean requiredOnCreate,
      boolean nullable,
      boolean readOnly,
      Set<WriteOperation> writeOperations,
      boolean hasDefaultValue,
      Set<Operator> filterOperators,
      boolean supportsWildcards,
      boolean sortable,
      OpenApiSchemaDocumentation openApi,
      AccessDocumentation readAccess,
      AccessDocumentation createAccess,
      AccessDocumentation updateAccess) {}

  public record FilterSchema(String selector, Set<Operator> operators, boolean supportsWildcards) {}

  public record RelationshipSchema(
      String name,
      List<String> targetResources,
      Map<String, String> globalIdPrefixesByTarget,
      RelationshipCardinality cardinality,
      boolean requiredOnCreate,
      boolean nullable,
      boolean readOnly,
      Set<WriteOperation> writeOperations,
      Map<WriteOperation, Set<RelationshipInputForm>> inputForms,
      boolean selfReferenceAllowed,
      OpenApiSchemaDocumentation openApi,
      AccessDocumentation readAccess,
      AccessDocumentation createAccess,
      AccessDocumentation updateAccess) {}

  public record AccessPolicySchema(
      AccessDocumentation readAccess,
      AccessDocumentation createAccess,
      AccessDocumentation updateAccess,
      AccessDocumentation deleteAccess,
      AccessDocumentation softDeleteAccess) {}

  public record ResourceSchema(
      String name,
      Class<?> entityType,
      String idField,
      List<FieldSchema> fields,
      List<RelationshipSchema> relationships,
      List<FilterSchema> filters,
      List<Sort> defaultSort,
      AccessPolicySchema access) {}

  public record Sort(String field, boolean ascending) {}

  private final String resourceName;
  private final Class<T> entityType;
  private final Map<String, Field<T, ?>> fields;
  private final Map<String, Relationship<T>> relationships;
  private final Map<String, FilterSelector> filterSelectors;
  private final String idField;
  private final List<Sort> defaultSort;
  private final AccessPolicy accessPolicy;

  /**
   * Describes a collection whose every operation requires an authenticated caller.
   *
   * <p>Fails closed: a collection wanting anonymous reads must say so with the {@link AccessPolicy}
   * overload. Registering a collection used to make its reads anonymous, so the safe default
   * matters.
   */
  public CollectionDescription(
      String resourceName,
      Class<T> entityType,
      List<? extends Field<T, ?>> fields,
      List<Relationship<T>> relationships,
      String idField,
      List<Sort> defaultSort) {
    this(
        resourceName,
        entityType,
        fields,
        relationships,
        idField,
        defaultSort,
        AccessPolicy.authenticated());
  }

  public CollectionDescription(
      String resourceName,
      Class<T> entityType,
      List<? extends Field<T, ?>> fields,
      List<Relationship<T>> relationships,
      String idField,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "Access policy");
    this.resourceName = requireText(resourceName, "Resource name");
    this.entityType = Objects.requireNonNull(entityType, "Entity type");
    Objects.requireNonNull(fields, "Fields");
    Objects.requireNonNull(relationships, "Relationships");
    this.idField = requireText(idField, "ID field");
    this.defaultSort = List.copyOf(Objects.requireNonNull(defaultSort, "Default sort"));

    Map<String, Field<T, ?>> byName = new LinkedHashMap<>();
    fields.forEach(
        field -> {
          Objects.requireNonNull(field, "Field");
          if (byName.putIfAbsent(field.name(), field) != null) {
            throw new IllegalArgumentException("Duplicate collection field " + field.name());
          }
        });
    this.fields = Collections.unmodifiableMap(byName);

    Map<String, Relationship<T>> relationshipsByName = new LinkedHashMap<>();
    relationships.forEach(
        relationship -> {
          Objects.requireNonNull(relationship, "Relationship");
          if (byName.containsKey(relationship.name())
              || relationshipsByName.putIfAbsent(relationship.name(), relationship) != null) {
            throw new IllegalArgumentException("Duplicate resource field " + relationship.name());
          }
        });
    this.relationships = Collections.unmodifiableMap(relationshipsByName);

    Map<String, FilterSelector> selectors = new LinkedHashMap<>();
    this.fields.values().stream()
        .filter(field -> !field.operators().isEmpty())
        .map(Field::filterSelector)
        .forEach(selector -> selectors.put(selector.name(), selector));
    this.relationships.values().stream()
        .flatMap(relationship -> relationship.filterSelectors().stream())
        .forEach(
            selector -> {
              if (selectors.putIfAbsent(selector.name(), selector) != null) {
                throw new IllegalArgumentException("Duplicate filter selector " + selector.name());
              }
            });
    filterSelectors = Collections.unmodifiableMap(selectors);

    Field<T, ?> id = this.fields.get(this.idField);
    if (id == null) {
      throw new IllegalArgumentException("ID field must be described");
    }
    if (id.writableOn(WriteOperation.CREATE) || id.writableOn(WriteOperation.UPDATE)) {
      throw new IllegalArgumentException("ID field must be read-only");
    }

    Set<String> sorted = new LinkedHashSet<>();
    for (Sort sort : this.defaultSort) {
      Objects.requireNonNull(sort, "Default sort");
      Field<T, ?> field = this.fields.get(sort.field());
      if (field == null || !field.sortable()) {
        throw new IllegalArgumentException("Default sort field must be described and sortable");
      }
      if (!sorted.add(sort.field())) {
        throw new IllegalArgumentException("Default sort fields must not be duplicated");
      }
    }
    if (!sorted.contains(this.idField)) {
      throw new IllegalArgumentException("Default sort must include the ID field");
    }
  }

  /** Describes an annotated resource whose every operation requires an authenticated caller. */
  public static <T> CollectionDescription<T> fromApiV2Resource(
      Class<?> resourceType, List<Relationship<T>> relationships, List<Sort> defaultSort) {
    return fromApiV2Resource(
        resourceType, relationships, defaultSort, AccessPolicy.authenticated());
  }

  public static <T> CollectionDescription<T> fromApiV2Resource(
      Class<?> resourceType,
      List<Relationship<T>> relationships,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy) {
    ApiV2ResourceDefinition definition = resourceType.getAnnotation(ApiV2ResourceDefinition.class);
    if (definition == null || !resourceType.isRecord()) {
      throw new IllegalArgumentException(
          "Resource definition must be an annotated record: " + resourceType.getName());
    }
    @SuppressWarnings("unchecked")
    Class<T> entityType = (Class<T>) definition.entity();
    Map<String, PropertyDescriptor> properties = properties(entityType);
    List<Relationship<T>> describedRelationships = new ArrayList<>(relationships);
    if (definition.auditFields()) {
      describedRelationships.addAll(auditRelationships(properties));
    }
    return new CollectionDescription<>(
        definition.name(),
        entityType,
        fieldsFrom(resourceType, entityType, definition, properties),
        describedRelationships,
        definition.id(),
        defaultSort,
        accessPolicy);
  }

  public String resourceName() {
    return resourceName;
  }

  public Class<T> entityType() {
    return entityType;
  }

  public Field<T, ?> requireField(String name) {
    Field<T, ?> field = fields.get(name);
    if (field == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    return field;
  }

  public void requireSelectableField(String name) {
    if (!fields.containsKey(name) && !relationships.containsKey(name)) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
  }

  /**
   * Returns the field accepting writes under this name.
   *
   * @throws CollectionQueryException if the name is unknown or the field is read-only
   */
  public Field<T, ?> requireWritableField(String name, WriteOperation operation) {
    Field<T, ?> field = requireField(name);
    if (!field.writableOn(operation)) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    return field;
  }

  public String idField() {
    return idField;
  }

  public List<Sort> defaultSort() {
    return defaultSort;
  }

  public List<Relationship<T>> relationships() {
    return List.copyOf(relationships.values());
  }

  public Relationship<T> requireRelationship(String name) {
    Relationship<T> relationship = relationships.get(name);
    if (relationship == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    return relationship;
  }

  public java.util.Optional<Relationship<T>> findRelationship(String name) {
    return java.util.Optional.ofNullable(relationships.get(name));
  }

  public FilterSelector requireFilterSelector(String name) {
    FilterSelector selector = filterSelectors.get(name);
    if (selector == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    return selector;
  }

  /** Names of the fields that accept the requested write operation, in declaration order. */
  public Set<String> writableFields(WriteOperation operation) {
    Set<String> writable = new LinkedHashSet<>();
    fields.values().stream()
        .filter(field -> field.writableOn(operation))
        .forEach(field -> writable.add(field.name()));
    relationships.values().stream()
        .filter(relationship -> relationship.writableOn(operation))
        .forEach(relationship -> writable.add(relationship.name()));
    return Collections.unmodifiableSet(writable);
  }

  /** Reads an entity into an ordered API document containing every described field. */
  public Map<String, Object> toDocument(T entity) {
    return toDocument(entity, field -> true);
  }

  /** Reads only selected fields, avoiding work for fields omitted from the response. */
  public Map<String, Object> toDocument(T entity, Predicate<String> selection) {
    Objects.requireNonNull(entity, "Entity");
    Objects.requireNonNull(selection, "Selection");
    Map<String, Object> document = new LinkedHashMap<>();
    fields.values().stream()
        .filter(field -> selection.test(field.name()))
        .forEach(field -> document.put(field.name(), field.documentValue(entity)));
    return document;
  }

  /** Applies parsed values in description order for deterministic setter behavior. */
  public void apply(T entity, ParsedDocument document) {
    Objects.requireNonNull(document, "Document");
    apply(entity, document.values(), document.operation());
  }

  /** Applies typed values in description order for deterministic setter behavior. */
  public void apply(T entity, Map<String, Object> values, WriteOperation operation) {
    Objects.requireNonNull(entity, "Entity");
    Objects.requireNonNull(values, "Values");
    values.forEach((name, value) -> requireWritableField(name, operation).validateValue(value));
    fields.values().stream()
        .filter(field -> field.writableOn(operation) && values.containsKey(field.name()))
        .forEach(field -> field.write(entity, values.get(field.name())));
  }

  public List<Field<T, ?>> fields() {
    return List.copyOf(fields.values());
  }

  public AccessPolicy accessPolicy() {
    return accessPolicy;
  }

  /**
   * Whether {@code field} may be read at all in this request.
   *
   * <p>The id is always readable: the response envelope and every by-id route depend on it, and a
   * document with no identity is not useful. A field the caller may not read is omitted from output
   * and rejected as a {@code where}/{@code sort} target.
   */
  public boolean fieldReadable(String field, AccessContext context) {
    Field<T, ?> described = fields.get(field);
    if (described != null) {
      return idField.equals(field) || described.readAccess().allowsField(context);
    }
    Relationship<T> relationship = relationships.get(field);
    if (relationship != null) {
      return relationship.readAccess().allowsField(context);
    }
    FilterSelector selector = filterSelectors.get(field);
    if (selector instanceof FilterSelector.RelationshipPart relationshipPart) {
      return relationshipPart.relationship().readAccess().allowsField(context);
    }
    return false;
  }

  /** Field names this request may not read, for narrowing a {@link FieldSelection}. */
  public Set<String> unreadableFields(AccessContext context) {
    return java.util.stream.Stream.concat(fields.keySet().stream(), relationships.keySet().stream())
        .filter(name -> !fieldReadable(name, context))
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  public ResourceSchema schema() {
    return new ResourceSchema(
        resourceName,
        entityType,
        idField,
        fields.values().stream().map(field -> field.schema(accessPolicy)).toList(),
        relationships.values().stream()
            .map(
                relationship ->
                    new RelationshipSchema(
                        relationship.name(),
                        relationship.targets().stream()
                            .map(RelationshipTarget::resourceName)
                            .toList(),
                        globalIdPrefixesByTarget(relationship.targets()),
                        relationship.cardinality(),
                        relationship.isRequiredOnCreate(),
                        relationship.nullable(),
                        !relationship.writableOn(WriteOperation.CREATE)
                            && !relationship.writableOn(WriteOperation.UPDATE),
                        relationship.writeOperations,
                        relationship.inputForms,
                        relationship.selfReferenceAllowed(),
                        relationship.openApi,
                        documented(relationship.readAccess, accessPolicy.readAccess()),
                        documented(relationship.writeAccess, accessPolicy.createAccess()),
                        documented(relationship.writeAccess, accessPolicy.updateAccess())))
            .toList(),
        filterSelectors.values().stream()
            .map(
                selector ->
                    new FilterSchema(
                        selector.name(), selector.operators(), selector.supportsWildcards()))
            .toList(),
        defaultSort,
        new AccessPolicySchema(
            documented(accessPolicy.readAccess()),
            documented(accessPolicy.createAccess()),
            documented(accessPolicy.updateAccess()),
            documented(accessPolicy.deleteAccess()),
            documented(accessPolicy.softDeleteAccess())));
  }

  private static Map<String, String> globalIdPrefixesByTarget(List<RelationshipTarget<?>> targets) {
    Map<String, String> prefixes = new LinkedHashMap<>();
    targets.stream()
        .filter(target -> target.globalIdPrefix() != null)
        .forEach(target -> prefixes.put(target.resourceName(), target.globalIdPrefix()));
    return Collections.unmodifiableMap(prefixes);
  }

  private static AccessDocumentation documented(AccessFunction function) {
    return function
        .documentation()
        .orElseThrow(() -> new IllegalStateException("Access function is not documented"));
  }

  private static AccessDocumentation documented(
      AccessFunction function, AccessFunction inheritedFunction) {
    return function == AccessFunction.INHERITED
        ? documented(inheritedFunction)
        : documented(function);
  }

  Object readRelationship(T entity, Relationship<T> relationship) {
    return relationship.read(entity);
  }

  Object readFilterValue(T entity, String selectorName) {
    FilterSelector selector = requireFilterSelector(selectorName);
    if (selector instanceof FilterSelector.Property property) {
      return fields.get(property.name()).reader.apply(entity);
    }
    FilterSelector.RelationshipPart part = (FilterSelector.RelationshipPart) selector;
    @SuppressWarnings("unchecked")
    Relationship<T> relationship = (Relationship<T>) part.relationship();
    Object value = readRelationship(entity, relationship);
    if (value == null) {
      return null;
    }
    if (!(value instanceof ResourceReference<?, ?> reference)) {
      throw new IllegalStateException("Relationship filter requires a resource reference");
    }
    return switch (part.part()) {
      case ROOT -> reference;
      case KIND -> reference.kind();
      case ID -> reference.id();
    };
  }

  Object readSortValue(T entity, String fieldName) {
    return requireField(fieldName).reader.apply(entity);
  }

  private static <T> List<Field<T, ?>> fieldsFrom(
      Class<?> resourceType,
      Class<T> entityType,
      ApiV2ResourceDefinition definition,
      Map<String, PropertyDescriptor> properties) {
    List<Field<T, ?>> fields =
        new ArrayList<>(
            Arrays.stream(resourceType.getRecordComponents())
                .<Field<T, ?>>map(
                    component -> fieldFrom(resourceType, component, properties, definition.id()))
                .toList());
    if (definition.auditFields()) {
      fields.addAll(auditFields(properties));
    }
    return List.copyOf(fields);
  }

  private static <T> List<Field<T, ?>> auditFields(Map<String, PropertyDescriptor> properties) {
    List<Field<T, ?>> fields = new ArrayList<>();
    CollectionDescription.<T, Date>auditProperty(
            properties, Date.class, "createdAt", "creationDate")
        .map(
            source ->
                auditDateField("createdAt", "Time at which the resource was created.", source))
        .ifPresent(fields::add);
    CollectionDescription.<T, Date>auditProperty(
            properties, Date.class, "updatedAt", "modificationDate")
        .map(
            source ->
                auditDateField("updatedAt", "Time at which the resource was last updated.", source))
        .ifPresent(fields::add);
    CollectionDescription.<T>legacyAuditUserField(
            properties, "createdBy", "Username of the user who created the resource.", "createdBy")
        .ifPresent(fields::add);
    CollectionDescription.<T>legacyAuditUserField(
            properties,
            "updatedBy",
            "Username of the user who last updated the resource.",
            "updatedBy",
            "lastUpdatedBy",
            "modifiedBy")
        .ifPresent(fields::add);
    return List.copyOf(fields);
  }

  private static <T> List<Relationship<T>> auditRelationships(
      Map<String, PropertyDescriptor> properties) {
    List<Relationship<T>> relationships = new ArrayList<>();
    CollectionDescription.<T>auditUserRelationship(properties, "createdBy", "createdBy")
        .ifPresent(relationships::add);
    CollectionDescription.<T>auditUserRelationship(
            properties, "updatedBy", "updatedBy", "lastUpdatedBy", "modifiedBy")
        .ifPresent(relationships::add);
    return List.copyOf(relationships);
  }

  private static <T> Field<T, Date> auditDateField(
      String name, String description, AuditProperty<T, Date> source) {
    return Field.readOnly(name, source.property(), CollectionFieldTypes.instant(), source.reader())
        .allowNull()
        .documented(auditDocumentation(description));
  }

  private static <T> Optional<Field<T, ?>> legacyAuditUserField(
      Map<String, PropertyDescriptor> properties,
      String name,
      String description,
      String... candidates) {
    Method reader = auditReadMethod(properties, candidates);
    if (reader == null || User.class.isAssignableFrom(reader.getReturnType())) {
      return Optional.empty();
    }
    if (reader.getReturnType() != String.class) {
      throw new IllegalArgumentException(
          "Audit property " + reader.getName() + " must return a username or User");
    }
    Function<T, String> valueReader = entity -> (String) invoke(reader, entity);
    return Optional.of(
        Field.readOnly(
                name, readerProperty(properties, reader), CollectionFieldTypes.text(), valueReader)
            .allowNull()
            .documented(auditDocumentation(description)));
  }

  private static <T> Optional<Relationship<T>> auditUserRelationship(
      Map<String, PropertyDescriptor> properties, String name, String... candidates) {
    Method reader = auditReadMethod(properties, candidates);
    if (reader == null || !User.class.isAssignableFrom(reader.getReturnType())) {
      return Optional.empty();
    }
    String property = readerProperty(properties, reader);
    Function<T, User> userReader = entity -> (User) invoke(reader, entity);
    return Optional.of(
        Relationship.referenceToOne(
                name,
                "users",
                CollectionFieldTypes.longNumber(),
                User.class,
                userReader,
                User::getId,
                property + ".id")
            .allowNull()
            .documented(
                auditDocumentation(
                    name.equals("createdBy")
                        ? "User who created the resource."
                        : "User who last updated the resource.")));
  }

  private static String readerProperty(Map<String, PropertyDescriptor> properties, Method reader) {
    return properties.entrySet().stream()
        .filter(entry -> reader.equals(entry.getValue().getReadMethod()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Audit reader is not a bean property"));
  }

  private static OpenApiSchemaDocumentation auditDocumentation(String description) {
    return new OpenApiSchemaDocumentation(
        null, description, null, null, null, null, List.of(), false);
  }

  private static <T, V> Optional<AuditProperty<T, V>> auditProperty(
      Map<String, PropertyDescriptor> properties, Class<V> type, String... candidates) {
    Method reader = auditReadMethod(properties, candidates);
    if (reader == null) {
      return Optional.empty();
    }
    if (!type.isAssignableFrom(reader.getReturnType())) {
      throw new IllegalArgumentException(
          "Audit property " + reader.getName() + " must return " + type.getName());
    }
    return Optional.of(
        new AuditProperty<>(
            readerProperty(properties, reader), entity -> type.cast(invoke(reader, entity))));
  }

  private record AuditProperty<T, V>(String property, Function<T, V> reader) {}

  private static Method auditReadMethod(
      Map<String, PropertyDescriptor> properties, String... candidates) {
    for (String candidate : candidates) {
      PropertyDescriptor descriptor = properties.get(candidate);
      if (descriptor != null && descriptor.getReadMethod() != null) {
        return descriptor.getReadMethod();
      }
    }
    return null;
  }

  private static Map<String, PropertyDescriptor> properties(Class<?> entityType) {
    try {
      Map<String, PropertyDescriptor> properties = new LinkedHashMap<>();
      for (PropertyDescriptor property :
          Introspector.getBeanInfo(entityType).getPropertyDescriptors()) {
        properties.put(property.getName(), property);
      }
      return properties;
    } catch (IntrospectionException ex) {
      throw new IllegalArgumentException("Cannot inspect entity " + entityType.getName(), ex);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <T> Field<T, ?> fieldFrom(
      Class<?> resourceType,
      RecordComponent component,
      Map<String, PropertyDescriptor> properties,
      String idField) {
    ApiV2ResourceField definition = component.getAnnotation(ApiV2ResourceField.class);
    if (definition == null) {
      throw new IllegalArgumentException(
          "Missing @ApiV2ResourceField on " + resourceType.getName() + "." + component.getName());
    }
    String property = definition.property().isBlank() ? component.getName() : definition.property();
    PropertyDescriptor descriptor = properties.get(property);
    Method reader = descriptor == null ? null : descriptor.getReadMethod();
    if (reader == null) {
      throw new IllegalArgumentException("Resource field requires a readable property " + property);
    }
    requireCompatibleType(component, reader.getReturnType(), "reader");

    CollectionFieldType type = fieldType(component.getType(), definition.maxLength());
    Function<T, Object> read = entity -> invoke(reader, entity);
    Method writer = descriptor.getWriteMethod();
    boolean createWritable =
        !component.getName().equals(idField)
            && writer != null
            && definition.createAccess() != ApiV2ResourceField.AccessPreset.NEVER;
    boolean updateWritable =
        !component.getName().equals(idField)
            && writer != null
            && definition.updateAccess() != ApiV2ResourceField.AccessPreset.NEVER;
    Field field;
    if (!createWritable && !updateWritable) {
      field = Field.readOnly(component.getName(), property, type, read);
    } else {
      requireCompatibleType(component, writer.getParameterTypes()[0], "writer");
      BiConsumer<T, Object> write = (entity, value) -> invoke(writer, entity, value);
      field = Field.writable(component.getName(), property, type, read, write);
      if (createWritable && !updateWritable) {
        field = field.writeOnlyOn(WriteOperation.CREATE);
      } else if (!createWritable) {
        field = field.writeOnlyOn(WriteOperation.UPDATE);
      }
    }
    if (definition.requiredOnCreate()) {
      field = field.required();
    }
    if (definition.nullable()) {
      field = field.allowNull();
    }
    field =
        field.documented(
            new OpenApiSchemaDocumentation(
                definition.title(),
                definition.description(),
                definition.example(),
                definition.pattern(),
                definition.minimum(),
                definition.maximum(),
                List.of(definition.enumValues()),
                definition.deprecated(),
                definition.format(),
                definition.defaultValue(),
                definition.minLength() < 0 ? null : definition.minLength(),
                List.of(definition.additionalExamples()),
                Map.of()));
    return field
        .readableBy(definition.readAccess().resolve())
        .creatableBy(definition.createAccess().resolve())
        .updatableBy(definition.updateAccess().resolve())
        .withQueryCapabilities(definition.filterable(), definition.sortable());
  }

  private static CollectionFieldType<?> fieldType(Class<?> type, int maxLength) {
    if (type == String.class) {
      if (maxLength < -1) {
        throw new IllegalArgumentException("Text maximum length must not be less than -1");
      }
      return maxLength == -1 ? CollectionFieldTypes.text() : CollectionFieldTypes.text(maxLength);
    }
    if (maxLength != -1) {
      throw new IllegalArgumentException("Maximum length is only supported for text fields");
    }
    if (type == Long.class || type == long.class) {
      return CollectionFieldTypes.longNumber();
    }
    if (type == java.util.Date.class) {
      return CollectionFieldTypes.instant();
    }
    if (type == Boolean.class || type == boolean.class) {
      return CollectionFieldTypes.bool();
    }
    throw new IllegalArgumentException("Unsupported resource field type " + type.getName());
  }

  private static void requireCompatibleType(
      RecordComponent component, Class<?> accessorType, String accessor) {
    if (!boxed(component.getType()).equals(boxed(accessorType))) {
      throw new IllegalArgumentException(
          "Resource field "
              + component.getName()
              + " does not match entity "
              + accessor
              + " type "
              + accessorType.getName());
    }
  }

  private static Class<?> boxed(Class<?> type) {
    if (type == long.class) {
      return Long.class;
    }
    if (type == boolean.class) {
      return Boolean.class;
    }
    return type;
  }

  private static Object invoke(Method method, Object target, Object... arguments) {
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("Resource accessor failed", cause);
    } catch (IllegalAccessException ex) {
      throw new IllegalStateException("Cannot invoke resource accessor", ex);
    }
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
