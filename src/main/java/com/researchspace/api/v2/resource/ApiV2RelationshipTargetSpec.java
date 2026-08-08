package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/** A readable relationship target that does not expose generic CRUD routes. */
public record ApiV2RelationshipTargetSpec<T, ID>(
    CollectionDescription<T> description,
    Class<ID> idType,
    BiFunction<ID, User, Optional<T>> findReadableById)
    implements ApiV2ReadableResourceTarget {

  public ApiV2RelationshipTargetSpec {
    Objects.requireNonNull(description, "Resource description");
    Objects.requireNonNull(idType, "ID type");
    Objects.requireNonNull(findReadableById, "Readable resource lookup");
  }

  @Override
  public Optional<ResolvedTarget> resolveReadable(Object rawId, User actor) {
    ID id = castId(rawId);
    AccessContext context =
        new AccessContext(actor, Operation.READ, description.resourceName(), id);
    AccessResult access = description.accessPolicy().readAccess().check(context);
    if (access.isDenied()) {
      return Optional.empty();
    }
    if (access.constraintOrEmpty().isPresent()) {
      throw new IllegalStateException(
          "A target-only REST API v2 resource cannot enforce a read row constraint: "
              + description.resourceName());
    }
    FieldSelection fields = readableFields(context);
    return ApiV2ReadableTargetSupport.hideAuthorizationFailure(
        actor,
        description.resourceName(),
        () -> findReadableById.apply(id, actor).map(entity -> new ResolvedTarget(entity, fields)));
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
