package com.researchspace.model.collection;

import com.researchspace.model.collection.CollectionDescription.Relationship;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Renders selected attributes and included relationships from registered resource metadata. */
public final class ResourceRenderer {

  @FunctionalInterface
  public interface TargetResolver {
    Optional<ResolvedTarget> resolve(String resourceName, Object id);

    default Map<TargetKey, Optional<ResolvedTarget>> resolveAll(Collection<TargetKey> targets) {
      Map<TargetKey, Optional<ResolvedTarget>> resolved = new LinkedHashMap<>();
      targets.forEach(target -> resolved.put(target, resolve(target.resourceName(), target.id())));
      return resolved;
    }
  }

  public record TargetKey(String resourceName, Object id) {}

  public record ResolvedTarget(Object entity, FieldSelection fields) {

    public ResolvedTarget {
      Objects.requireNonNull(entity, "Resolved target entity");
      Objects.requireNonNull(fields, "Resolved target fields");
    }
  }

  private static final TargetResolver NO_TARGETS = (resourceName, id) -> Optional.empty();

  private final ResourceRegistry registry;

  public ResourceRenderer(ResourceRegistry registry) {
    this.registry = registry;
  }

  public <T> Map<String, Object> render(
      T entity, CollectionDescription<T> description, FieldSelection fields, IncludeTree includes) {
    return render(entity, description, fields, includes, NO_TARGETS);
  }

  public <T> Map<String, Object> render(
      T entity,
      CollectionDescription<T> description,
      FieldSelection fields,
      IncludeTree includes,
      TargetResolver targetResolver) {
    return render(
        entity,
        description,
        fields,
        ResourceFieldSelections.root(fields),
        includes,
        targetResolver);
  }

  public <T> Map<String, Object> render(
      T entity,
      CollectionDescription<T> description,
      ResourceFieldSelections selections,
      IncludeTree includes,
      TargetResolver targetResolver) {
    return renderAll(
            List.of(entity),
            description,
            ignored -> selections.root(),
            selections,
            includes,
            targetResolver)
        .get(0);
  }

  /** Resolves relationships for the complete result set before rendering any one document. */
  public <T> List<Map<String, Object>> renderAll(
      List<T> entities,
      CollectionDescription<T> description,
      Function<T, FieldSelection> fieldsForEntity,
      ResourceFieldSelections selections,
      IncludeTree includes,
      TargetResolver targetResolver) {
    TargetResolver requestResolver =
        targetResolver == NO_TARGETS ? NO_TARGETS : new CachingTargetResolver(targetResolver);
    if (targetResolver != NO_TARGETS) {
      List<RenderNode> roots =
          entities.stream()
              .map(
                  entity ->
                      new RenderNode(entity, description, fieldsForEntity.apply(entity), includes))
              .toList();
      prefetch(roots, selections, requestResolver);
    }
    return entities.stream()
        .map(
            entity ->
                render(
                    entity,
                    description,
                    fieldsForEntity.apply(entity),
                    selections,
                    includes,
                    requestResolver))
        .toList();
  }

  /** Owns page-local resolution state so batching works with every resolver adapter. */
  private static final class CachingTargetResolver implements TargetResolver {

    private final TargetResolver delegate;
    private final Map<TargetKey, Optional<ResolvedTarget>> cache = new LinkedHashMap<>();

    private CachingTargetResolver(TargetResolver delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<ResolvedTarget> resolve(String resourceName, Object id) {
      TargetKey key = new TargetKey(resourceName, id);
      resolveAll(List.of(key));
      return cache.getOrDefault(key, Optional.empty());
    }

    @Override
    public Map<TargetKey, Optional<ResolvedTarget>> resolveAll(Collection<TargetKey> targets) {
      Set<TargetKey> missing = new LinkedHashSet<>(targets);
      missing.removeAll(cache.keySet());
      if (!missing.isEmpty()) {
        Map<TargetKey, Optional<ResolvedTarget>> resolved = delegate.resolveAll(missing);
        missing.forEach(
            target -> cache.put(target, resolved.getOrDefault(target, Optional.empty())));
      }
      Map<TargetKey, Optional<ResolvedTarget>> result = new LinkedHashMap<>();
      targets.forEach(target -> result.put(target, cache.getOrDefault(target, Optional.empty())));
      return result;
    }
  }

  private void prefetch(
      List<RenderNode> roots, ResourceFieldSelections selections, TargetResolver targetResolver) {
    List<RenderNode> level = roots;
    while (!level.isEmpty()) {
      Set<TargetKey> targets = new LinkedHashSet<>();
      List<PendingExpansion> expansions = new ArrayList<>();
      level.forEach(node -> collectTargets(node, targets, expansions));
      Map<TargetKey, Optional<ResolvedTarget>> resolved = targetResolver.resolveAll(targets);
      List<RenderNode> next = new ArrayList<>();
      for (PendingExpansion expansion : expansions) {
        resolved
            .getOrDefault(expansion.target(), Optional.empty())
            .ifPresent(
                target -> {
                  CollectionDescription<?> targetDescription =
                      registry.requireResource(expansion.target().resourceName());
                  next.add(
                      new RenderNode(
                          target.entity(),
                          targetDescription,
                          target
                              .fields()
                              .intersect(
                                  selections.forResource(expansion.target().resourceName()),
                                  targetDescription.idField()),
                          expansion.includes()));
                });
      }
      level = next;
    }
  }

  private void collectTargets(
      RenderNode node, Set<TargetKey> targets, List<PendingExpansion> expansions) {
    collectTargetsCaptured(node, node.description(), targets, expansions);
  }

  private <T> void collectTargetsCaptured(
      RenderNode node,
      CollectionDescription<T> description,
      Set<TargetKey> targets,
      List<PendingExpansion> expansions) {
    T entity = description.entityType().cast(node.entity());
    for (Relationship<T> relationship : description.relationships()) {
      if (!node.fields().includes(relationship.name(), description.idField())) {
        continue;
      }
      Object value = description.readRelationship(entity, relationship);
      if (!(value instanceof ResourceReference<?, ?> reference)) {
        continue;
      }
      RelationshipTarget<?> target = relationship.targetForKind(reference.kind());
      TargetKey key = new TargetKey(target.resourceName(), reference.id());
      targets.add(key);
      node.includes()
          .relationship(relationship.name())
          .ifPresent(child -> expansions.add(new PendingExpansion(key, child)));
    }
  }

  private record RenderNode(
      Object entity,
      CollectionDescription<?> description,
      FieldSelection fields,
      IncludeTree includes) {}

  private record PendingExpansion(TargetKey target, IncludeTree includes) {}

  private <T> Map<String, Object> render(
      T entity,
      CollectionDescription<T> description,
      FieldSelection fields,
      ResourceFieldSelections selections,
      IncludeTree includes,
      TargetResolver targetResolver) {
    Map<String, Object> document =
        new LinkedHashMap<>(
            description.toDocument(entity, field -> fields.includes(field, description.idField())));
    description
        .relationships()
        .forEach(
            relationship ->
                renderSelectedRelationship(
                    entity,
                    description,
                    relationship,
                    fields,
                    selections,
                    includes,
                    targetResolver,
                    document));
    return document;
  }

  private <T> void renderSelectedRelationship(
      T entity,
      CollectionDescription<T> description,
      Relationship<T> relationship,
      FieldSelection fields,
      ResourceFieldSelections selections,
      IncludeTree includes,
      TargetResolver targetResolver,
      Map<String, Object> document) {
    if (!fields.includes(relationship.name(), description.idField())) {
      return;
    }
    Optional<IncludeTree> child = includes.relationship(relationship.name());
    Object value = description.readRelationship(entity, relationship);
    if (value == null) {
      document.put(relationship.name(), null);
      return;
    }
    if (!(value instanceof ResourceReference<?, ?> reference)) {
      throw new IllegalStateException("Relationship binding must return a resource reference");
    }
    document.put(
        relationship.name(),
        renderReference(relationship, reference, child, selections, targetResolver).orElse(null));
  }

  private <T> Optional<Object> renderReference(
      Relationship<T> relationship,
      ResourceReference<?, ?> reference,
      Optional<IncludeTree> includes,
      ResourceFieldSelections selections,
      TargetResolver targetResolver) {
    RelationshipTarget<?> target = relationship.targetForKind(reference.kind());
    CollectionDescription<?> targetDescription = registry.requireResource(target.resourceName());
    FieldSelection requestedFields = selections.forResource(target.resourceName());
    Optional<ResolvedTarget> resolved =
        targetResolver == NO_TARGETS
            ? Optional.empty()
            : targetResolver.resolve(target.resourceName(), reference.id());
    // API rendering supplies an authorization-aware resolver. An empty result means the target is
    // missing or unreadable and the reference itself must disappear; falling back to its raw ID
    // would turn expansion authorization into an identifier-enumeration oracle.
    if (targetResolver != NO_TARGETS && resolved.isEmpty()) {
      return Optional.empty();
    }
    Object renderedValue = serialize(relationship.idType(), reference.id());
    if (includes.isPresent()) {
      renderedValue =
          resolved
              .<Object>map(
                  targetEntity ->
                      renderUnknown(
                          targetEntity.entity(),
                          targetDescription,
                          targetEntity
                              .fields()
                              .intersect(requestedFields, targetDescription.idField()),
                          selections,
                          includes.orElseThrow(),
                          targetResolver))
              .orElse(renderedValue);
    }
    Map<String, Object> referenceDocument = new LinkedHashMap<>();
    referenceDocument.put("relationTo", target.resourceName());
    referenceDocument.put("value", renderedValue);
    if (target.globalIdPrefix() != null) {
      referenceDocument.put("globalId", target.globalIdPrefix() + reference.id());
    }
    return Optional.of(referenceDocument);
  }

  private static <V> Object serialize(CollectionFieldType<V> type, Object value) {
    return type.serialize(type.javaType().cast(value));
  }

  private Map<String, Object> renderUnknown(
      Object entity,
      CollectionDescription<?> description,
      FieldSelection fields,
      ResourceFieldSelections selections,
      IncludeTree includes,
      TargetResolver targetResolver) {
    return renderCaptured(entity, description, fields, selections, includes, targetResolver);
  }

  private <T> Map<String, Object> renderCaptured(
      Object entity,
      CollectionDescription<T> description,
      FieldSelection fields,
      ResourceFieldSelections selections,
      IncludeTree includes,
      TargetResolver targetResolver) {
    T typedEntity = description.entityType().cast(entity);
    return render(typedEntity, description, fields, selections, includes, targetResolver);
  }
}
