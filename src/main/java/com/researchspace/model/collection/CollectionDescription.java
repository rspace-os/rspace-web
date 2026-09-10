package com.researchspace.model.collection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  private final String resourceName;
  private final Class<T> entityType;
  private final Map<String, Field<T, ?>> fields;
  private final Map<String, Relationship<T>> relationships;
  private final Map<String, FilterSelector<T>> filterSelectors;
  private final Map<String, FilterSelector<T>> internalFilterSelectors;
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
    this(
        resourceName,
        entityType,
        fields,
        relationships,
        idField,
        defaultSort,
        accessPolicy,
        List.of());
  }

  /**
   * Describes a collection that also carries filters only the server may use.
   *
   * <p>An {@link InternalFilter} is resolvable by the query compiler but is invisible to callers:
   * {@link #requirePublicFilterSelector} refuses it and {@link #schema()} omits it. This is what
   * lets an {@link AccessResult#allowedWhere} constraint test a property that must never become a
   * public filter, such as an access control list, without publishing it as an API field.
   */
  public CollectionDescription(
      String resourceName,
      Class<T> entityType,
      List<? extends Field<T, ?>> fields,
      List<Relationship<T>> relationships,
      String idField,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy,
      List<InternalFilter> internalFilters) {
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

    Map<String, FilterSelector<T>> selectors = new LinkedHashMap<>();
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

    Map<String, FilterSelector<T>> internal = new LinkedHashMap<>();
    Objects.requireNonNull(internalFilters, "Internal filters")
        .forEach(
            filter -> {
              Objects.requireNonNull(filter, "Internal filter");
              if (selectors.containsKey(filter.name())
                  || internal.put(
                          filter.name(),
                          new FilterSelector.Property<>(
                              filter.name(), filter.property(), filter.type()))
                      != null) {
                throw new IllegalArgumentException("Duplicate filter selector " + filter.name());
              }
            });
    internalFilterSelectors = Collections.unmodifiableMap(internal);

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
      Class<?> resourceType,
      Class<T> entityType,
      List<Relationship<T>> relationships,
      List<Sort> defaultSort) {
    return fromApiV2Resource(
        resourceType, entityType, relationships, defaultSort, AccessPolicy.authenticated());
  }

  public static <T> CollectionDescription<T> fromApiV2Resource(
      Class<?> resourceType,
      Class<T> entityType,
      List<Relationship<T>> relationships,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy) {
    return AnnotatedCollectionDescriptionFactory.create(
        resourceType, entityType, relationships, defaultSort, accessPolicy);
  }

  /** Describes an annotated resource with filters reserved for server-side constraints. */
  public static <T> CollectionDescription<T> fromApiV2Resource(
      Class<?> resourceType,
      Class<T> entityType,
      List<Relationship<T>> relationships,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy,
      List<InternalFilter> internalFilters) {
    return AnnotatedCollectionDescriptionFactory.create(
        resourceType, entityType, relationships, defaultSort, accessPolicy, internalFilters);
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

  public Optional<Field<T, ?>> findField(String name) {
    return Optional.ofNullable(fields.get(name));
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

  public Optional<Relationship<T>> findRelationship(String name) {
    return Optional.ofNullable(relationships.get(name));
  }

  /**
   * Resolves a selector for compilation, including the internal ones.
   *
   * <p>Safe to be permissive because a caller cannot reach an internal selector: the only path from
   * request text to a filter is {@link RsqlFilterParser}, which resolves through {@link
   * #requirePublicFilterSelector}. An internal selector therefore only ever arrives in a
   * server-built access constraint.
   */
  public FilterSelector<T> requireFilterSelector(String name) {
    FilterSelector<T> selector = filterSelectors.get(name);
    if (selector == null) {
      selector = internalFilterSelectors.get(name);
    }
    if (selector == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    return selector;
  }

  /** Resolves a selector for compilation, or empty when this collection has no such selector. */
  public Optional<FilterSelector<T>> findFilterSelector(String name) {
    FilterSelector<T> selector = filterSelectors.get(name);
    return Optional.ofNullable(selector != null ? selector : internalFilterSelectors.get(name));
  }

  /** Resolves a selector a caller may name, or empty when there is none. */
  public Optional<FilterSelector<T>> findPublicFilterSelector(String name) {
    return Optional.ofNullable(filterSelectors.get(name));
  }

  /** Public selectors in declaration order, for registry-level relationship path discovery. */
  public List<FilterSelector<T>> publicFilterSelectors() {
    return List.copyOf(filterSelectors.values());
  }

  /** Resolves a selector a caller may name. Refuses an internal filter as an unknown field. */
  public FilterSelector<T> requirePublicFilterSelector(String name) {
    FilterSelector<T> selector = filterSelectors.get(name);
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
    return toDocument(entity, selection, Map.of());
  }

  /** Reads selected fields, substituting caller-specific values before their readers run. */
  public Map<String, Object> toDocument(
      T entity, Predicate<String> selection, Map<String, Object> readOverrides) {
    Objects.requireNonNull(entity, "Entity");
    Objects.requireNonNull(selection, "Selection");
    Objects.requireNonNull(readOverrides, "Read overrides");
    Map<String, Object> document = new LinkedHashMap<>();
    fields.values().stream()
        .filter(field -> selection.test(field.name()))
        .forEach(
            field ->
                document.put(
                    field.name(),
                    readOverrides.containsKey(field.name())
                        ? readOverrides.get(field.name())
                        : field.documentValue(entity)));
    return document;
  }

  /** Returns the serialized identifier value used for row-specific access decisions. */
  public Object idValue(T entity) {
    Objects.requireNonNull(entity, "Entity");
    return requireField(idField).documentValue(entity);
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
    FilterSelector<T> selector = filterSelectors.get(field);
    if (selector instanceof FilterSelector.RelationshipPart<?> relationshipPart) {
      return relationshipPart.relationship().readAccess().allowsField(context);
    }
    return false;
  }

  /** Field names this request may not read, for narrowing a {@link FieldSelection}. */
  public Set<String> unreadableFields(AccessContext context) {
    return Stream.concat(fields.keySet().stream(), relationships.keySet().stream())
        .filter(name -> !fieldReadable(name, context))
        .collect(Collectors.toCollection(LinkedHashSet::new));
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

  static AccessDocumentation documented(AccessFunction function) {
    return function
        .documentation()
        .orElseThrow(() -> new IllegalStateException("Access function is not documented"));
  }

  static AccessDocumentation documented(AccessFunction function, AccessFunction inheritedFunction) {
    return function == AccessFunction.INHERITED
        ? documented(inheritedFunction)
        : documented(function);
  }

  Object readRelationship(T entity, Relationship<T> relationship) {
    return relationship.read(entity);
  }

  /**
   * The ID of a to-one relationship's target, or empty when this entity holds no reference.
   *
   * <p>Public because a caller outside this package may need to reach the target of a row without
   * rendering it: projecting a runtime field through a relationship has to ask the target
   * resource's provider for values by ID, and reading the ID back out of a rendered document would
   * depend on how deep the response happened to expand.
   *
   * <p>The ID only, never the target entity. Loading the target is the resolver's job and is
   * subject to that resource's own access rules; this says nothing about whether the caller may
   * read it.
   */
  public Optional<Object> relationshipTargetId(T entity, Relationship<T> relationship) {
    Object value = readRelationship(entity, relationship);
    return value instanceof ResourceReference<?, ?> reference
        ? Optional.ofNullable(reference.id())
        : Optional.empty();
  }

  Object readFilterValue(T entity, String selectorName) {
    FilterSelector<T> selector = requireFilterSelector(selectorName);
    // Covers every permitted FilterSelector. Java 17 has no pattern switch, so a new selector kind
    // fails here explicitly rather than through a cast that breaks only when a caller filters on
    // it.
    if (selector instanceof FilterSelector.Property<T> property) {
      Field<T, ?> field = fields.get(property.name());
      if (field == null) {
        // An internal filter has no reader, so only a database query can evaluate it.
        throw new IllegalStateException(
            "Internal filter " + property.name() + " cannot be evaluated in memory");
      }
      return field.reader.apply(entity);
    }
    if (selector instanceof FilterSelector.RelationshipPart<T> part) {
      return readRelationshipValue(entity, part);
    }
    if (selector instanceof FilterSelector.RuntimeField<T> runtime) {
      throw new IllegalStateException(
          "Runtime field " + runtime.name() + " cannot be evaluated in memory");
    }
    throw new IllegalStateException("Unsupported filter selector " + selector.getClass());
  }

  private Object readRelationshipValue(T entity, FilterSelector.RelationshipPart<T> selector) {
    Object value = selector.readRelationship(entity);
    if (value == null) {
      return null;
    }
    if (!(value instanceof ResourceReference<?, ?> reference)) {
      throw new IllegalStateException("Relationship filter requires a resource reference");
    }
    return switch (selector.part()) {
      case ROOT -> reference;
      case KIND -> reference.kind();
      case ID -> reference.id();
    };
  }

  Object readSortValue(T entity, String fieldName) {
    return requireField(fieldName).reader.apply(entity);
  }

  static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
