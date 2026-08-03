package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import com.researchspace.model.permissions.SecurityLogger;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A readable relationship target that does not expose generic CRUD routes. */
public record ApiV2RelationshipTargetSpec<T, ID>(
    CollectionDescription<T> description, BiFunction<ID, User, Optional<T>> findReadableById)
    implements ApiV2ReadableResourceTarget {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  public ApiV2RelationshipTargetSpec {
    Objects.requireNonNull(description, "Resource description");
    Objects.requireNonNull(findReadableById, "Readable resource lookup");
  }

  @Override
  public Optional<ResolvedTarget> resolveReadable(Object rawId, User actor) {
    ID id = castId(rawId);
    AccessContext context =
        new AccessContext(actor, Operation.READ, description.resourceName(), id);
    AccessResult access = description.accessPolicy().read().check(context);
    if (access.isDenied()) {
      return Optional.empty();
    }
    if (access.constraintOrEmpty().isPresent()) {
      throw new IllegalStateException(
          "A target-only REST API v2 resource cannot enforce a read row constraint: "
              + description.resourceName());
    }
    try {
      FieldSelection fields = readableFields(context);
      return findReadableById.apply(id, actor).map(entity -> new ResolvedTarget(entity, fields));
    } catch (AuthorizationException ex) {
      SECURITY_LOG.warn(
          "REST API v2 relationship target authorization failure for user [{}], resource [{}]",
          actor == null ? "(anonymous)" : actor.getUsername(),
          description.resourceName(),
          ex);
      return Optional.empty();
    }
  }

  private FieldSelection readableFields(AccessContext context) {
    java.util.Set<String> unreadable = description.unreadableFields(context);
    return unreadable.isEmpty() ? FieldSelection.all() : FieldSelection.exclude(unreadable);
  }

  @SuppressWarnings("unchecked")
  private ID castId(Object id) {
    try {
      return (ID) description.requireField(description.idField()).type().javaType().cast(id);
    } catch (ClassCastException ex) {
      throw new IllegalArgumentException("Relationship target ID has the wrong type", ex);
    }
  }
}
