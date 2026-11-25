package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Explicit authorization and related-event policy for a resource's audit route.
 *
 * @param lookup finds one resource for its audit endpoint. Implementations may include
 *     lifecycle-hidden rows, but must retain the resource's ordinary row-level readability
 *     boundary.
 * @param accessCheck audit-only authorization hook, deliberately independent of generic
 *     access-document routes
 * @param requiredFields trusted payload fields required to belong to the currently readable
 *     resource instance
 * @param fallbackFrom earliest timestamp for related rows missing the required identity fields
 * @param relatedAuditFields extra safe payload fields admitted for related events in a resource
 *     lifecycle audit
 * @param relatedAuditIdentifierFields trusted top-level payload fields that link related audit
 *     events to this resource's audit ID. Values must be server-derived relationships, never
 *     user-controlled text.
 * @param auditBypassesActorDirectory whether this already-authorised resource audit may include
 *     actors outside the user directory
 */
public record ResourceAuditPolicy<T, ID>(
    BiFunction<ID, User, Optional<T>> lookup,
    BiConsumer<T, User> accessCheck,
    Function<T, Map<String, String>> requiredFields,
    Function<T, Instant> fallbackFrom,
    Set<String> relatedAuditFields,
    Set<String> relatedAuditIdentifierFields,
    boolean auditBypassesActorDirectory) {
  public ResourceAuditPolicy {
    Objects.requireNonNull(lookup, "Audit lookup");
    Objects.requireNonNull(accessCheck, "Audit access check");
    Objects.requireNonNull(requiredFields, "Required audit fields");
    Objects.requireNonNull(fallbackFrom, "Related audit fallback");
    relatedAuditFields = Set.copyOf(relatedAuditFields);
    relatedAuditIdentifierFields = Set.copyOf(relatedAuditIdentifierFields);
  }

  static Method requireAuditIdentifier(Class<?> entityType) {
    if (entityType.getAnnotation(AuditTrailData.class) == null) {
      throw new IllegalArgumentException(
          "Audit policy requires @AuditTrailData on " + entityType.getName());
    }
    var identifiers =
        Arrays.stream(entityType.getMethods())
            .filter(method -> method.isAnnotationPresent(AuditTrailIdentifier.class))
            .toList();
    if (identifiers.size() != 1) {
      throw new IllegalArgumentException(
          "Audit policy requires exactly one public @AuditTrailIdentifier method on "
              + entityType.getName());
    }
    return identifiers.get(0);
  }

  public static <T, ID> ResourceAuditPolicy<T, ID> standard(ResourceOperations<T, ID> operations) {
    return new ResourceAuditPolicy<>(
        operations::findById,
        (entity, subject) -> {},
        entity -> Map.of(),
        entity -> null,
        Set.of(),
        Set.of(),
        false);
  }

  public Optional<T> findByIdForAudit(ID id, User subject) {
    return lookup.apply(id, subject);
  }

  public void requireAuditAccess(T entity, User subject) {
    accessCheck.accept(entity, subject);
  }

  public Map<String, String> requiredAuditFields(T entity) {
    return requiredFields.apply(entity);
  }

  public Instant relatedAuditFallbackFrom(T entity) {
    return fallbackFrom.apply(entity);
  }
}
