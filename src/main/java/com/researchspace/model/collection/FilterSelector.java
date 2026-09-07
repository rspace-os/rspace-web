package com.researchspace.model.collection;

import com.researchspace.model.collection.CollectionDescription.Operator;
import java.util.Objects;
import java.util.Set;

/** A server-owned mapping from one public filter selector to typed persistence metadata. */
public sealed interface FilterSelector<T>
    permits FilterSelector.Property,
        FilterSelector.RelationshipPart,
        FilterSelector.RelationshipProperty,
        FilterSelector.RuntimeField {

  /** Positive operators allowed for a public field reached through a relationship. */
  static Set<Operator> relationshipTargetFieldOperators() {
    return Set.of(Operator.EQUAL, Operator.IN, Operator.CONTAINS, Operator.LIKE);
  }

  enum RelationshipComponent {
    ROOT,
    KIND,
    ID
  }

  String name();

  Set<Operator> operators();

  Object parse(String value);

  default Object readRelationship(T entity) {
    throw new IllegalStateException("Selector does not describe a relationship");
  }

  default boolean supportsWildcards() {
    return false;
  }

  record Property<T>(String name, String property, CollectionFieldType<?> type)
      implements FilterSelector<T> {

    public Property {
      if (name == null || name.isBlank() || property == null || property.isBlank()) {
        throw new IllegalArgumentException("Filter selector names must not be blank");
      }
      Objects.requireNonNull(type, "Filter field type");
    }

    @Override
    public Set<Operator> operators() {
      return type.operators();
    }

    @Override
    public Object parse(String value) {
      return type.parse(value);
    }

    @Override
    public boolean supportsWildcards() {
      return type.supportsWildcards();
    }
  }

  /** Typed selector synthesized for a scalar field shared by relationship destinations. */
  record RelationshipProperty<T>(String name, CollectionFieldType<?> type, Set<Operator> operators)
      implements FilterSelector<T> {

    public RelationshipProperty {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Filter selector name must not be blank");
      }
      Objects.requireNonNull(type, "Filter field type");
      operators = Set.copyOf(operators);
    }

    @Override
    public Object parse(String value) {
      return type.parse(value);
    }

    @Override
    public boolean supportsWildcards() {
      return type.supportsWildcards();
    }
  }

  /**
   * A selector for a field that exists only for this actor, resolved by a runtime provider.
   *
   * <p>Unlike the other kinds this carries no model property. Its {@link RuntimeFieldBinding} names
   * a separate value entity, so the query compiler builds a correlated subquery rather than a path
   * on the row's own alias, and nothing in a request can influence those names.
   */
  record RuntimeField<T>(ResolvedRuntimeField field, String name) implements FilterSelector<T> {

    /**
     * @param name the selector as the request wrote it, which is the definition's own selector for
     *     a field of this collection and {@code <relationship>.<selector>} for one reached through
     *     a relationship. It is the key every later stage looks the resolved field up by, so it
     *     must be the written name rather than the definition's.
     */
    public RuntimeField {
      Objects.requireNonNull(field, "Resolved runtime field");
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Runtime field selector name must not be blank");
      }
    }

    @Override
    public Set<Operator> operators() {
      return field.definition().operators();
    }

    @Override
    public Object parse(String value) {
      return field.type().parse(value);
    }

    @Override
    public boolean supportsWildcards() {
      return field.definition().supportsWildcards();
    }
  }

  record RelationshipPart<T>(
      String name, CollectionDescription.Relationship<T> relationship, RelationshipComponent part)
      implements FilterSelector<T> {

    private static final Set<Operator> OPERATORS =
        Set.of(Operator.EQUAL, Operator.NOT_EQUAL, Operator.IN, Operator.NOT_IN, Operator.EXISTS);

    public RelationshipPart {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Filter selector name must not be blank");
      }
      Objects.requireNonNull(relationship, "Relationship");
      Objects.requireNonNull(part, "Relationship component");
    }

    @Override
    public Set<Operator> operators() {
      return OPERATORS;
    }

    @Override
    public Object parse(String value) {
      return switch (part) {
        case ROOT -> relationship.parseGlobalReference(value);
        case KIND -> relationship.requireTarget(value).storedKind();
        case ID -> relationship.idType().parse(value);
      };
    }

    @Override
    public Object readRelationship(T entity) {
      return relationship.read(entity);
    }
  }
}
