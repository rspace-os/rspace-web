package com.researchspace.model.collection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable graph of relationships selected for expansion. */
public record IncludeTree(Map<String, IncludeTree> relationships) {

  private static final IncludeTree EMPTY = new IncludeTree(Map.of());

  public IncludeTree {
    relationships = Map.copyOf(relationships);
  }

  public static IncludeTree empty() {
    return EMPTY;
  }

  public static IncludeTree toDepth(
      CollectionDescription<?> description, ResourceRegistry registry, int depth) {
    if (depth < 0) {
      throw new IllegalArgumentException("Include depth must not be negative");
    }
    if (depth == 0 || description.relationships().isEmpty()) {
      return empty();
    }
    Map<String, IncludeTree> children = new LinkedHashMap<>();
    description
        .relationships()
        .forEach(
            relationship -> {
              IncludeTree targetTree =
                  relationship.targets().stream()
                      .map(target -> registry.requireResource(target.resourceName()))
                      .map(target -> toDepth(target, registry, depth - 1))
                      .reduce(IncludeTree::merge)
                      .orElse(empty());
              children.put(relationship.name(), targetTree);
            });
    return new IncludeTree(children);
  }

  public Optional<IncludeTree> relationship(String name) {
    return Optional.ofNullable(relationships.get(name));
  }

  public boolean isEmpty() {
    return relationships.isEmpty();
  }

  private IncludeTree merge(IncludeTree other) {
    Map<String, IncludeTree> merged = new LinkedHashMap<>(relationships);
    other.relationships.forEach((name, child) -> merged.merge(name, child, IncludeTree::merge));
    return new IncludeTree(merged);
  }
}
