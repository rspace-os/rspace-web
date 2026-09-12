package com.researchspace.model.collection;

import com.researchspace.model.core.GlobalIdentifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.Getter;

/** One relationship to another registered resource. */
public final class Relationship<T> {

  private final String name;
  private final CollectionFieldType<?> idType;
  private final List<RelationshipTarget<?>> targets;
  private final Function<T, ?> reader;
  private final SplitReferenceBinding<T, ?, ?> binding;
  final Set<WriteOperation> writeOperations;
  final Map<WriteOperation, Set<RelationshipInputForm>> inputForms;
  @Getter private final boolean requiredOnCreate;
  private final boolean nullable;
  final AccessFunction readAccess;
  final AccessFunction writeAccess;
  private final boolean selfReferenceAllowed;
  final OpenApiSchemaDocumentation openApi;

  private Relationship(
      String name,
      CollectionFieldType<?> idType,
      List<RelationshipTarget<?>> targets,
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
    this.name = CollectionDescription.requireText(name, "Relationship name");
    this.idType = Objects.requireNonNull(idType, "Relationship ID type");
    this.targets = validateTargets(targets);
    this.reader = Objects.requireNonNull(reader, "Relationship reader");
    this.binding = Objects.requireNonNull(binding, "Reference binding");
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
    return copy(
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

  public CollectionFieldType<?> idType() {
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
    if (value == null || !GlobalIdentifier.isValid(value)) {
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
            .orElseThrow(() -> new IllegalArgumentException("Unsupported relationship global ID"));
    return new ResourceReference<>(
        target.storedKind(), idType.parse(String.valueOf(identifier.getDbId())));
  }

  public SplitReferenceBinding<T, ?, ?> binding() {
    return binding;
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

  public List<FilterSelector<T>> filterSelectors() {
    List<FilterSelector<T>> selectors = new ArrayList<>();
    if (targets.stream().allMatch(target -> target.globalIdPrefix() != null)) {
      selectors.add(
          new FilterSelector.RelationshipPart<>(
              name, this, FilterSelector.RelationshipComponent.ROOT));
    }
    if (binding.hasKindProperty()) {
      selectors.add(
          new FilterSelector.RelationshipPart<>(
              name + ".relationTo", this, FilterSelector.RelationshipComponent.KIND));
    }
    selectors.add(
        new FilterSelector.RelationshipPart<>(
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
    return copy(operations, forms, required, acceptsNull, reads, writes, permitsSelf, openApi);
  }

  private Relationship<T> copy(
      Set<WriteOperation> operations,
      Map<WriteOperation, Set<RelationshipInputForm>> forms,
      boolean required,
      boolean acceptsNull,
      AccessFunction reads,
      AccessFunction writes,
      boolean permitsSelf,
      OpenApiSchemaDocumentation documentation) {
    return new Relationship<>(
        name,
        idType,
        targets,
        reader,
        binding,
        operations,
        forms,
        required,
        acceptsNull,
        reads,
        writes,
        permitsSelf,
        documentation);
  }

  private void validateConfiguration() {
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
      boolean missingPrefix = targets.stream().anyMatch(target -> target.globalIdPrefix() == null);
      if (missingPrefix) {
        throw new IllegalArgumentException("Global-ID input requires prefixes for all targets");
      }
    }
  }

  private static List<RelationshipTarget<?>> validateTargets(List<RelationshipTarget<?>> targets) {
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
