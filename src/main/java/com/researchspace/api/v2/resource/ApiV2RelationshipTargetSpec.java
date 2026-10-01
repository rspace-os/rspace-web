package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/** A readable relationship target that does not expose generic CRUD routes. */
public record ApiV2RelationshipTargetSpec<T, ID>(
    CollectionDescription<T> description,
    Class<ID> idType,
    BiFunction<Set<ID>, User, Map<ID, T>> findReadableByIds)
    implements ApiV2ReadableResourceTarget {

  public ApiV2RelationshipTargetSpec {
    Objects.requireNonNull(description, "Resource description");
    Objects.requireNonNull(idType, "ID type");
    Objects.requireNonNull(findReadableByIds, "Readable resource batch lookup");
  }

  @Override
  public Map<Object, ResolvedTarget> resolveReadable(Set<Object> rawIds, User actor) {
    Set<ID> ids = rawIds.stream().map(this::castId).collect(java.util.stream.Collectors.toSet());
    AccessContext context = new AccessContext(actor, Operation.READ, description.resourceName());
    AccessResult access = description.accessPolicy().readAccess().check(context);
    if (access.isDenied()) {
      return Map.of();
    }
    if (access.constraintOrEmpty().isPresent()) {
      throw new IllegalStateException(
          "A target-only REST API v2 resource cannot enforce a read row constraint: "
              + description.resourceName());
    }
    return ApiV2ReadableTargetSupport.hideAuthorizationFailure(
            actor,
            description.resourceName(),
            () ->
                Optional.of(
                    findReadableByIds.apply(ids, actor).entrySet().stream()
                        .filter(entry -> ids.contains(entry.getKey()))
                        .collect(
                            java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                entry ->
                                    new ResolvedTarget(
                                        entry.getValue(),
                                        readableFields(
                                            new AccessContext(
                                                actor,
                                                Operation.READ,
                                                description.resourceName(),
                                                entry.getKey()))),
                                (left, right) -> left,
                                java.util.LinkedHashMap::new))))
        .<Map<Object, ResolvedTarget>>map(map -> new java.util.LinkedHashMap<>(map))
        .orElseGet(Map::of);
  }

  private FieldSelection readableFields(AccessContext context) {
    Set<String> unreadable = description.unreadableFields(context);
    return unreadable.isEmpty() ? FieldSelection.all() : FieldSelection.exclude(unreadable);
  }

  private ID castId(Object id) {
    try {
      return idType.cast(id);
    } catch (ClassCastException ex) {
      throw new IllegalArgumentException("Relationship target ID has the wrong type", ex);
    }
  }
}
