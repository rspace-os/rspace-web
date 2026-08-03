package com.researchspace.model.collection;

import com.researchspace.model.collection.CollectionDescription.Operator;
import java.util.Set;

/** A server-owned mapping from one public filter selector to typed persistence metadata. */
public sealed interface FilterSelector
    permits FilterSelector.Property, FilterSelector.RelationshipPart {

  enum RelationshipComponent {
    ROOT,
    KIND,
    ID
  }

  String name();

  Set<Operator> operators();

  Object parse(String value);

  default boolean supportsWildcards() {
    return false;
  }

  record Property(String name, String property, CollectionFieldType<?> type)
      implements FilterSelector {

    public Property {
      if (name == null || name.isBlank() || property == null || property.isBlank()) {
        throw new IllegalArgumentException("Filter selector names must not be blank");
      }
      java.util.Objects.requireNonNull(type, "Filter field type");
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

  record RelationshipPart(
      String name, CollectionDescription.Relationship<?> relationship, RelationshipComponent part)
      implements FilterSelector {

    private static final Set<Operator> OPERATORS =
        Set.of(Operator.EQUAL, Operator.NOT_EQUAL, Operator.IN, Operator.NOT_IN, Operator.EXISTS);

    public RelationshipPart {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Filter selector name must not be blank");
      }
      java.util.Objects.requireNonNull(relationship, "Relationship");
      java.util.Objects.requireNonNull(part, "Relationship component");
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
  }
}
