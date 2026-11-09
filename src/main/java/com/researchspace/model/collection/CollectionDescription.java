package com.researchspace.model.collection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable description of a collection's public fields and default ordering shared by its
 * adapters.
 *
 * <p>This is the single server-owned allowlist for a collection. Its ordered member list contains
 * scalar fields and relationships, each mapped to fixed model properties and carrying the
 * capabilities its HTTP and persistence adapters need. Fields and relationships retain their
 * type-specific behavior and order within their kinds.
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
  private final Map<String, ResourceMember<T>> fields;
  private final Map<String, FilterSelector<T>> publicFilterSelectors;
  private final Map<String, FilterSelector<T>> compilableSelectors;
  private final String idField;
  private final List<Sort> defaultSort;
  private final AccessPolicy accessPolicy;
  private final CollectionEntityMapper<T> entityMapper;
  private final CollectionAccessEvaluator<T> accessEvaluator;
  private final CollectionValueReader<T> valueReader;

  /**
   * Describes a collection whose every operation requires an authenticated caller.
   *
   * <p>Fails closed: a collection wanting anonymous reads must say so with the {@link AccessPolicy}
   * overload.
   */
  public CollectionDescription(
      String resourceName,
      Class<T> entityType,
      List<? extends ResourceMember<T>> fields,
      String idField,
      List<Sort> defaultSort) {
    this(resourceName, entityType, fields, idField, defaultSort, AccessPolicy.authenticated());
  }

  public CollectionDescription(
      String resourceName,
      Class<T> entityType,
      List<? extends ResourceMember<T>> fields,
      String idField,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy) {
    this(resourceName, entityType, fields, idField, defaultSort, accessPolicy, List.of());
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
      List<? extends ResourceMember<T>> fields,
      String idField,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy,
      List<InternalFilter> internalFilters) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "Access policy");
    this.resourceName = requireText(resourceName, "Resource name");
    this.entityType = Objects.requireNonNull(entityType, "Entity type");
    Objects.requireNonNull(fields, "Fields");
    this.idField = requireText(idField, "ID field");
    this.defaultSort = List.copyOf(Objects.requireNonNull(defaultSort, "Default sort"));

    Map<String, ResourceMember<T>> fieldsByName = new LinkedHashMap<>();
    fields.forEach(
        member -> {
          Objects.requireNonNull(member, "Member");
          if (fieldsByName.putIfAbsent(member.name(), member) != null) {
            throw new IllegalArgumentException("Duplicate resource member " + member.name());
          }
          if (member instanceof Field<T, ?> field) {
            if (field.embedded()) {
              throw new IllegalArgumentException(
                  "Embedded fields must belong to an object or array");
            }
            field.validateConfiguration();
          }
        });
    this.fields = Collections.unmodifiableMap(fieldsByName);

    Map<String, FilterSelector<T>> selectors = new LinkedHashMap<>();
    fields().stream()
        .filter(field -> !field.operators().isEmpty())
        .map(Field::filterSelector)
        .forEach(selector -> selectors.put(selector.name(), selector));
    relationships().stream()
        .flatMap(relationship -> relationship.filterSelectors().stream())
        .forEach(
            selector -> {
              if (selectors.putIfAbsent(selector.name(), selector) != null) {
                throw new IllegalArgumentException("Duplicate filter selector " + selector.name());
              }
            });
    publicFilterSelectors = Collections.unmodifiableMap(selectors);

    Map<String, FilterSelector<T>> internal = new LinkedHashMap<>();
    Objects.requireNonNull(internalFilters, "Internal filters")
        .forEach(
            filter -> {
              Objects.requireNonNull(filter, "Internal filter");
              if (publicFilterSelectors.containsKey(filter.name())
                  || internal.put(
                          filter.name(),
                          new FilterSelector.Property<>(
                              filter.name(), filter.property(), filter.type()))
                      != null) {
                throw new IllegalArgumentException("Duplicate filter selector " + filter.name());
              }
            });
    Map<String, FilterSelector<T>> compilable = new LinkedHashMap<>(publicFilterSelectors);
    compilable.putAll(internal);
    compilableSelectors = Collections.unmodifiableMap(compilable);

    Field<T, ?> id = findField(this.idField).orElse(null);
    if (id == null) {
      throw new IllegalArgumentException("ID field must be described");
    }
    if (id.writableOn(WriteOperation.CREATE) || id.writableOn(WriteOperation.UPDATE)) {
      throw new IllegalArgumentException("ID field must be read-only");
    }

    Set<String> sorted = new LinkedHashSet<>();
    for (Sort sort : this.defaultSort) {
      Objects.requireNonNull(sort, "Default sort");
      Field<T, ?> field = findField(sort.field()).orElse(null);
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

    this.entityMapper = new CollectionEntityMapper<>(this.fields, this.idField);
    this.accessEvaluator =
        new CollectionAccessEvaluator<>(this.fields, publicFilterSelectors, this.idField);
    this.valueReader = new CollectionValueReader<>(this.fields, compilableSelectors);
  }

  /** Describes an annotated resource whose every operation requires an authenticated caller. */
  public static <T> CollectionDescription<T> fromApiV2Resource(
      Class<?> resourceType, Class<T> entityType, List<Sort> defaultSort) {
    return fromApiV2Resource(resourceType, entityType, defaultSort, AccessPolicy.authenticated());
  }

  public static <T> CollectionDescription<T> fromApiV2Resource(
      Class<?> resourceType,
      Class<T> entityType,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy) {
    return AnnotatedCollectionDescriptionFactory.create(
        resourceType,
        entityType,
        defaultSort,
        accessPolicy,
        List.of(),
        CollectionFieldTypeRegistry.builtIns());
  }

  /** Describes an annotated resource with filters reserved for server-side constraints. */
  public static <T> CollectionDescription<T> fromApiV2Resource(
      Class<?> resourceType,
      Class<T> entityType,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy,
      List<InternalFilter> internalFilters) {
    return AnnotatedCollectionDescriptionFactory.create(
        resourceType,
        entityType,
        defaultSort,
        accessPolicy,
        internalFilters,
        CollectionFieldTypeRegistry.builtIns());
  }

  /** Describes an annotated resource using an explicitly assembled immutable type registry. */
  public static <T> CollectionDescription<T> fromApiV2Resource(
      Class<?> resourceType,
      Class<T> entityType,
      List<Sort> defaultSort,
      CollectionFieldTypeRegistry fieldTypes) {
    return fromApiV2Resource(
        resourceType, entityType, defaultSort, AccessPolicy.authenticated(), List.of(), fieldTypes);
  }

  public static <T> CollectionDescription<T> fromApiV2Resource(
      Class<?> resourceType,
      Class<T> entityType,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy,
      List<InternalFilter> internalFilters,
      CollectionFieldTypeRegistry fieldTypes) {
    return AnnotatedCollectionDescriptionFactory.create(
        resourceType,
        entityType,
        defaultSort,
        accessPolicy,
        internalFilters,
        Objects.requireNonNull(fieldTypes, "Field type registry"));
  }

  public String resourceName() {
    return resourceName;
  }

  public Class<T> entityType() {
    return entityType;
  }

  public Field<T, ?> requireField(String name) {
    return findField(name)
        .orElseThrow(() -> new CollectionQueryException(CollectionQueryException.Reason.FIELD));
  }

  public Optional<Field<T, ?>> findField(String name) {
    ResourceMember<T> member = fields.get(name);
    return member instanceof Field<T, ?> field ? Optional.of(field) : Optional.empty();
  }

  public void requireSelectableField(String name) {
    if (!fields.containsKey(name)) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
  }

  public String idField() {
    return idField;
  }

  public List<Sort> defaultSort() {
    return defaultSort;
  }

  public List<Relationship<T>> relationships() {
    List<Relationship<T>> relationships = new ArrayList<>();
    for (ResourceMember<T> member : fields.values()) {
      if (member instanceof Relationship<T> relationship) {
        relationships.add(relationship);
      }
    }
    return List.copyOf(relationships);
  }

  public Relationship<T> requireRelationship(String name) {
    return findRelationship(name)
        .orElseThrow(() -> new CollectionQueryException(CollectionQueryException.Reason.FIELD));
  }

  public Optional<Relationship<T>> findRelationship(String name) {
    ResourceMember<T> member = fields.get(name);
    return member instanceof Relationship<T> relationship
        ? Optional.of(relationship)
        : Optional.empty();
  }

  /**
   * Resolves a selector for compilation, or empty when this collection has no such selector.
   *
   * <p>Safe to be permissive because a caller cannot reach an internal selector: the only path from
   * request text to a filter is {@link RsqlFilterParser}, which resolves through {@link
   * #requirePublicFilterSelector}. An internal selector therefore only ever arrives in a
   * server-built access constraint.
   */
  public Optional<FilterSelector<T>> findFilterSelector(String name) {
    return Optional.ofNullable(compilableSelectors.get(name));
  }

  /** Resolves a selector a caller may name, or empty when there is none. */
  public Optional<FilterSelector<T>> findPublicFilterSelector(String name) {
    return Optional.ofNullable(publicFilterSelectors.get(name));
  }

  /** Public selectors in declaration order, for registry-level relationship path discovery. */
  public List<FilterSelector<T>> publicFilterSelectors() {
    return List.copyOf(publicFilterSelectors.values());
  }

  /** Resolves a selector a caller may name. Refuses an internal filter as an unknown field. */
  public FilterSelector<T> requirePublicFilterSelector(String name) {
    return findPublicFilterSelector(name)
        .orElseThrow(() -> new CollectionQueryException(CollectionQueryException.Reason.FIELD));
  }

  /** Names of the fields that accept the requested write operation, in declaration order. */
  public Set<String> writableFields(WriteOperation operation) {
    Set<String> writable = new LinkedHashSet<>();
    fields().stream()
        .filter(field -> field.writableOn(operation))
        .forEach(field -> writable.add(field.name()));
    relationships().stream()
        .filter(relationship -> relationship.writableOn(operation))
        .forEach(relationship -> writable.add(relationship.name()));
    return Collections.unmodifiableSet(writable);
  }

  /** Reads an entity into an ordered API document containing every described field. */
  public Map<String, Object> toDocument(T entity) {
    return entityMapper.toDocument(entity);
  }

  /** Reads only selected fields, avoiding work for fields omitted from the response. */
  public Map<String, Object> toDocument(T entity, Predicate<String> selection) {
    return entityMapper.toDocument(entity, selection);
  }

  /** Reads selected fields, substituting caller-specific values before their readers run. */
  public Map<String, Object> toDocument(
      T entity, Predicate<String> selection, Map<String, Object> readOverrides) {
    return entityMapper.toDocument(entity, selection, readOverrides);
  }

  /** Reads selected fields and embedded children with one request's access context. */
  public Map<String, Object> toDocument(
      T entity,
      Predicate<String> selection,
      Map<String, Object> readOverrides,
      AccessContext context) {
    return entityMapper.toDocument(entity, selection, readOverrides, context);
  }

  /** Returns the serialized identifier value used for row-specific access decisions. */
  public Object idValue(T entity) {
    return entityMapper.idValue(entity);
  }

  /** Applies parsed values in description order for deterministic setter behavior. */
  public void apply(T entity, ParsedDocument document) {
    Objects.requireNonNull(document, "Document");
    apply(entity, document.values(), document.operation());
  }

  /** Applies typed values in description order for deterministic setter behavior. */
  public void apply(T entity, Map<String, Object> values, WriteOperation operation) {
    entityMapper.apply(entity, values, operation);
  }

  public List<Field<T, ?>> fields() {
    List<Field<T, ?>> scalarFields = new ArrayList<>();
    for (ResourceMember<T> member : fields.values()) {
      if (member instanceof Field<T, ?> field) {
        scalarFields.add(field);
      }
    }
    return List.copyOf(scalarFields);
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
    return accessEvaluator.fieldReadable(field, context);
  }

  /** Field names this request may not read, for narrowing a {@link FieldSelection}. */
  public Set<String> unreadableFields(AccessContext context) {
    return accessEvaluator.unreadableFields(context);
  }

  public ResourceSchema schema() {
    return CollectionSchemaBuilder.build(
        resourceName,
        entityType,
        fields,
        publicFilterSelectors,
        idField,
        defaultSort,
        accessPolicy);
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
    return entityMapper.relationshipTargetId(entity, relationship);
  }

  Object readFilterValue(T entity, String selectorName) {
    return valueReader.readFilterValue(entity, selectorName);
  }

  Object readSortValue(T entity, String fieldName) {
    return valueReader.readSortValue(entity, fieldName);
  }

  static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
