package com.researchspace.model.collection;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable boot-time registry of resource definitions and their relationship graph. */
public final class ResourceRegistry {

  private final Map<String, CollectionDescription<?>> byName;
  private final Map<Class<?>, CollectionDescription<?>> byEntityType;

  public ResourceRegistry(Collection<? extends CollectionDescription<?>> descriptions) {
    Objects.requireNonNull(descriptions, "Resource descriptions");
    Map<String, CollectionDescription<?>> names = new LinkedHashMap<>();
    Map<Class<?>, CollectionDescription<?>> entityTypes = new LinkedHashMap<>();
    descriptions.forEach(
        description -> {
          Objects.requireNonNull(description, "Resource description");
          if (names.putIfAbsent(description.resourceName(), description) != null) {
            throw new IllegalArgumentException(
                "Duplicate resource name " + description.resourceName());
          }
          if (entityTypes.putIfAbsent(description.entityType(), description) != null) {
            throw new IllegalArgumentException(
                "Duplicate resource entity type " + description.entityType().getName());
          }
        });
    names
        .values()
        .forEach(
            description ->
                description
                    .relationships()
                    .forEach(
                        relationship ->
                            relationship
                                .targets()
                                .forEach(
                                    target -> {
                                      CollectionDescription<?> targetDescription =
                                          names.get(target.resourceName());
                                      if (targetDescription == null) {
                                        throw new IllegalArgumentException(
                                            "Unknown relationship target " + target.resourceName());
                                      }
                                      if (target.entityType() != Object.class
                                          && !target
                                              .entityType()
                                              .equals(targetDescription.entityType())) {
                                        throw new IllegalArgumentException(
                                            "Relationship target entity type does not match"
                                                + " resource "
                                                + target.resourceName());
                                      }
                                      if (relationship.hasBinding()
                                          && !relationship
                                              .idType()
                                              .javaType()
                                              .equals(
                                                  targetDescription
                                                      .requireField(targetDescription.idField())
                                                      .type()
                                                      .javaType())) {
                                        throw new IllegalArgumentException(
                                            "Relationship target ID type does not match resource "
                                                + target.resourceName());
                                      }
                                    })));
    byName = Collections.unmodifiableMap(names);
    byEntityType = Collections.unmodifiableMap(entityTypes);
  }

  public CollectionDescription<?> requireResource(String name) {
    CollectionDescription<?> description = byName.get(name);
    if (description == null) {
      throw new IllegalArgumentException("Unknown resource " + name);
    }
    return description;
  }

  public CollectionDescription<?> requireEntityType(Class<?> type) {
    CollectionDescription<?> description = byEntityType.get(type);
    if (description == null) {
      throw new IllegalArgumentException("Unknown resource entity type " + type.getName());
    }
    return description;
  }

  public Collection<CollectionDescription<?>> resources() {
    return byName.values();
  }
}
