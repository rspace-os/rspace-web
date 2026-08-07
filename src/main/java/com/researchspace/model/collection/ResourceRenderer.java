package com.researchspace.model.collection;

import com.researchspace.model.collection.CollectionDescription.Relationship;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Renders selected attributes and included relationships from registered resource metadata. */
public final class ResourceRenderer {

  @FunctionalInterface
  public interface TargetResolver {
    Optional<ResolvedTarget> resolve(String resourceName, Object id);
  }

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
    return render(entity, description, selections.root(), selections, includes, targetResolver);
  }

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
    document.put(
        relationship.name(),
        renderReference(entity, description, relationship, child, selections, targetResolver));
  }

  private <T> Object renderReference(
      T entity,
      CollectionDescription<T> description,
      Relationship<T> relationship,
      Optional<IncludeTree> includes,
      ResourceFieldSelections selections,
      TargetResolver targetResolver) {
    Object value = description.readRelationship(entity, relationship);
    if (value == null) {
      return null;
    }
    if (!(value instanceof ResourceReference<?, ?> reference)) {
      throw new IllegalStateException("Relationship binding must return a resource reference");
    }
    RelationshipTarget<?> target = relationship.targetForKind(reference.kind());
    CollectionDescription<?> targetDescription = registry.requireResource(target.resourceName());
    FieldSelection requestedFields = selections.forResource(target.resourceName());
    Object renderedValue = serialize(relationship.idType(), reference.id());
    if (includes.isPresent()) {
      renderedValue =
          targetResolver
              .resolve(target.resourceName(), reference.id())
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
    return referenceDocument;
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
