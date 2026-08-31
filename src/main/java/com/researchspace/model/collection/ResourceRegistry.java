package com.researchspace.model.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable boot-time registry of resource definitions and their relationship graph. */
public final class ResourceRegistry {

  /** One destination participating in a queryable relationship-field path. */
  public record TargetQueryField(
      RelationshipTarget<?> target,
      CollectionDescription<?> description,
      FilterSelector.Property<?> property) {}

  /** Registry-owned interpretation of {@code relationship.scalarField}. */
  public record RelationshipQueryPath(
      String selector,
      CollectionDescription.Relationship<?> relationship,
      String targetField,
      List<TargetQueryField> targets,
      FilterSelector.RelationshipProperty<?> filterSelector) {

    public RelationshipQueryPath {
      targets = List.copyOf(targets);
    }
  }

  private final Map<String, CollectionDescription<?>> byName;
  private final Map<Class<?>, List<CollectionDescription<?>>> byEntityType;
  private final Map<String, Map<String, RelationshipQueryPath>> relationshipQueryPaths;

  public ResourceRegistry(Collection<? extends CollectionDescription<?>> descriptions) {
    Objects.requireNonNull(descriptions, "Resource descriptions");
    Map<String, CollectionDescription<?>> names = new LinkedHashMap<>();
    Map<Class<?>, List<CollectionDescription<?>>> entityTypes = new LinkedHashMap<>();
    descriptions.forEach(
        description -> {
          Objects.requireNonNull(description, "Resource description");
          if (names.putIfAbsent(description.resourceName(), description) != null) {
            throw new IllegalArgumentException(
                "Duplicate resource name " + description.resourceName());
          }
          entityTypes
              .computeIfAbsent(description.entityType(), ignored -> new ArrayList<>())
              .add(description);
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
                                      if (!relationship
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
    entityTypes.replaceAll((ignored, matchingDescriptions) -> List.copyOf(matchingDescriptions));
    byEntityType = Collections.unmodifiableMap(entityTypes);
    relationshipQueryPaths = buildRelationshipQueryPaths(names);
  }

  /** The described resource, or null when this registry has no such name. */
  public CollectionDescription<?> findResource(String name) {
    return byName.get(name);
  }

  public CollectionDescription<?> requireResource(String name) {
    CollectionDescription<?> description = byName.get(name);
    if (description == null) {
      throw new IllegalArgumentException("Unknown resource " + name);
    }
    return description;
  }

  public CollectionDescription<?> requireEntityType(Class<?> type) {
    List<CollectionDescription<?>> descriptions = byEntityType.get(type);
    if (descriptions == null) {
      throw new IllegalArgumentException("Unknown resource entity type " + type.getName());
    }
    if (descriptions.size() != 1) {
      throw new IllegalArgumentException(
          "Ambiguous resource entity type "
              + type.getName()
              + "; use a resource name instead: "
              + descriptions.stream().map(CollectionDescription::resourceName).toList());
    }
    return descriptions.get(0);
  }

  /**
   * Whether a caller may query a field on the context's resource.
   *
   * <p>In addition to fields described directly by the resource, this resolves a field reached
   * through one relationship. Every target collection that exposes the field must permit the caller
   * to read it. This keeps relationship-graph traversal and target field authorization in the
   * registry that owns that graph.
   */
  public boolean isQueryFieldReadable(String field, AccessContext context) {
    CollectionDescription<?> description = requireResource(context.resourceName());
    if (description.fieldReadable(field, context)) {
      return true;
    }
    RelationshipQueryPath path =
        findRelationshipQueryPath(context.resourceName(), field).orElse(null);
    if (path == null || !description.fieldReadable(path.relationship().name(), context)) {
      return false;
    }
    for (TargetQueryField target : path.targets()) {
      CollectionDescription<?> targetDescription = target.description();
      AccessContext targetContext =
          new AccessContext(context.user(), context.operation(), targetDescription.resourceName());
      if (!targetDescription.fieldReadable(path.targetField(), targetContext)) {
        return false;
      }
    }
    return true;
  }

  public Optional<RelationshipQueryPath> findRelationshipQueryPath(
      String sourceResource, String selector) {
    return Optional.ofNullable(
        relationshipQueryPaths.getOrDefault(sourceResource, Map.of()).get(selector));
  }

  /** All immediate scalar relationship paths published by one source collection. */
  public Collection<RelationshipQueryPath> relationshipQueryPaths(String sourceResource) {
    return relationshipQueryPaths.getOrDefault(sourceResource, Map.of()).values();
  }

  public Collection<CollectionDescription<?>> resources() {
    return byName.values();
  }

  private static Map<String, Map<String, RelationshipQueryPath>> buildRelationshipQueryPaths(
      Map<String, CollectionDescription<?>> descriptions) {
    Map<String, Map<String, RelationshipQueryPath>> all = new LinkedHashMap<>();
    descriptions
        .values()
        .forEach(
            source -> {
              Map<String, RelationshipQueryPath> paths = new LinkedHashMap<>();
              source
                  .relationships()
                  .forEach(
                      relationship -> {
                        Set<String> targetFields = new LinkedHashSet<>();
                        relationship
                            .targets()
                            .forEach(
                                target ->
                                    descriptions
                                        .get(target.resourceName())
                                        .publicFilterSelectors()
                                        .stream()
                                        .filter(FilterSelector.Property.class::isInstance)
                                        .map(FilterSelector::name)
                                        .forEach(targetFields::add));
                        targetFields.forEach(
                            targetField -> {
                              List<TargetQueryField> targets = new ArrayList<>();
                              relationship
                                  .targets()
                                  .forEach(
                                      target -> {
                                        CollectionDescription<?> targetDescription =
                                            descriptions.get(target.resourceName());
                                        FilterSelector<?> selector =
                                            targetDescription
                                                .findPublicFilterSelector(targetField)
                                                .orElse(null);
                                        if (selector
                                            instanceof FilterSelector.Property<?> property) {
                                          targets.add(
                                              new TargetQueryField(
                                                  target, targetDescription, property));
                                        }
                                      });
                              validateCompatibleTargetFields(
                                  source, relationship, targetField, targets);
                              FilterSelector.Property<?> representative = targets.get(0).property();
                              Set<CollectionDescription.Operator> operators =
                                  new LinkedHashSet<>(
                                      FilterSelector.relationshipTargetFieldOperators());
                              targets.forEach(
                                  target -> operators.retainAll(target.property().operators()));
                              String selector = relationship.name() + "." + targetField;
                              paths.put(
                                  selector,
                                  new RelationshipQueryPath(
                                      selector,
                                      relationship,
                                      targetField,
                                      targets,
                                      new FilterSelector.RelationshipProperty<>(
                                          selector, representative.type(), operators)));
                            });
                      });
              all.put(source.resourceName(), Collections.unmodifiableMap(paths));
            });
    return Collections.unmodifiableMap(all);
  }

  private static void validateCompatibleTargetFields(
      CollectionDescription<?> source,
      CollectionDescription.Relationship<?> relationship,
      String targetField,
      List<TargetQueryField> targets) {
    CollectionFieldType<?> first = targets.get(0).property().type();
    boolean compatible =
        targets.stream()
            .map(target -> target.property().type())
            .allMatch(
                type ->
                    type.javaType().equals(first.javaType())
                        && type.inputKind() == first.inputKind()
                        && type.schema().equals(first.schema()));
    if (!compatible) {
      throw new IllegalArgumentException(
          "Incompatible relationship filter "
              + source.resourceName()
              + "."
              + relationship.name()
              + "."
              + targetField);
    }
  }
}
