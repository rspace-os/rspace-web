package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import com.researchspace.model.collection.DocumentValidationException.Violation;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResolvedResourceReference;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import com.researchspace.model.collection.ResourceRenderer.TargetKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Resolves relationship references through the caller-aware resource catalog. */
final class ApiV2RelationshipResolver {

  private final Function<String, Optional<ApiV2ReadableResourceTarget>> targets;

  ApiV2RelationshipResolver(Function<String, Optional<ApiV2ReadableResourceTarget>> targets) {
    this.targets = Objects.requireNonNull(targets, "Relationship target lookup");
  }

  static ApiV2RelationshipResolver unavailable() {
    return new ApiV2RelationshipResolver(ignored -> Optional.empty());
  }

  Optional<ResolvedTarget> resolveReadable(String resourceName, Object id, User actor) {
    return targets.apply(resourceName).flatMap(target -> target.resolveReadable(id, actor));
  }

  Map<TargetKey, Optional<ResolvedTarget>> resolveReadable(Collection<TargetKey> keys, User actor) {
    Map<String, Set<Object>> idsByResource = new LinkedHashMap<>();
    keys.forEach(
        key ->
            idsByResource
                .computeIfAbsent(key.resourceName(), ignored -> new LinkedHashSet<>())
                .add(key.id()));
    Map<TargetKey, Optional<ResolvedTarget>> result = new LinkedHashMap<>();
    keys.forEach(key -> result.put(key, Optional.empty()));
    idsByResource.forEach(
        (resourceName, ids) ->
            targets
                .apply(resourceName)
                .ifPresent(
                    target ->
                        target
                            .resolveReadable(ids, actor)
                            .forEach(
                                (id, resolved) ->
                                    result.put(
                                        new TargetKey(resourceName, id), Optional.of(resolved)))));
    return result;
  }

  ParsedDocument resolve(
      ParsedDocument document,
      CollectionDescription<?> source,
      User actor,
      AccessContext context,
      String errorKey,
      boolean bulkUpdate) {
    Map<String, Object> resolved = new LinkedHashMap<>(document.values());
    for (CollectionDescription.Relationship<?> relationship : source.relationships()) {
      if (!document.changed(relationship.name())) {
        continue;
      }
      Object value = document.values().get(relationship.name());
      if (value == null) {
        continue;
      }
      if (!(value instanceof ResourceReference<?, ?> reference)) {
        throw new IllegalStateException(
            "Parsed relationship is not a resource reference: " + relationship.name());
      }
      RelationshipTarget<?> target = relationship.targetForKind(reference.kind());
      if (invalidSelfReference(source, relationship, target, reference, context, bulkUpdate)) {
        throw invalid(errorKey, relationship.name());
      }
      Object entity =
          resolveReadable(target.resourceName(), reference.id(), actor)
              .map(ResolvedTarget::entity)
              .filter(target.entityType()::isInstance)
              .orElseThrow(() -> invalid(errorKey, relationship.name()));
      resolved.put(relationship.name(), new ResolvedResourceReference<>(reference, entity));
    }
    return new ParsedDocument(document.operation(), resolved);
  }

  private static boolean invalidSelfReference(
      CollectionDescription<?> source,
      CollectionDescription.Relationship<?> relationship,
      RelationshipTarget<?> target,
      ResourceReference<?, ?> reference,
      AccessContext context,
      boolean bulkUpdate) {
    if (!source.resourceName().equals(target.resourceName())) {
      return false;
    }
    if (relationship.selfReferenceAllowed()) {
      return false;
    }
    if (bulkUpdate) {
      return true;
    }
    return context.targetId() != null && context.targetId().equals(reference.id());
  }

  private static DocumentValidationException invalid(String errorKey, String field) {
    return new DocumentValidationException(
        errorKey, List.of(new Violation(field, Reason.INVALID_VALUE)));
  }
}
