package com.researchspace.api.v2.resource;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Collection-specific domain adapter used by the generic REST v2 CRUD dispatcher.
 *
 * <p>A read-only collection pairs the three read methods with {@link AccessPolicy#readOnly}, which
 * refuses every mutation before dispatch reaches the adapter, so the throws below are unreachable
 * defense rather than the mechanism that makes a collection read-only.
 */
public interface ResourceOperations<T, ID> {

  /** Finds one page, resolving any caller-specific values for the effective {@code subject}. */
  ResourcePage<T> find(ResourceRequest request, User subject);

  /** Counts matching resources after caller-specific values are resolved. */
  long count(ResourceRequest request, User subject);

  /**
   * Finds one resource with caller-specific values and collection-specific read authorization.
   *
   * <p>The collection access policy remains the normal authorization mechanism. Collections whose
   * existing permission model cannot be represented as a query constraint can implement that check
   * here. Relationship resolution uses this method, so it cannot bypass those permissions.
   */
  Optional<T> findById(ID id, User subject);

  /**
   * Finds one resource for its audit endpoint. Implementations may include lifecycle-hidden rows,
   * but must retain the resource's ordinary row-level readability boundary.
   */
  default Optional<T> findByIdForAudit(ID id, User subject) {
    return findById(id, subject);
  }

  /** Audit-only authorization hook, deliberately independent of generic access-document routes. */
  default void requireAuditAccess(T resource, User subject) {}

  /** Extra safe payload fields admitted for related events in a resource lifecycle audit. */
  default Set<String> relatedAuditFields() {
    return Set.of();
  }

  /**
   * Whether this already-authorised resource audit may include actors outside the user directory.
   */
  default boolean auditBypassesActorDirectory() {
    return false;
  }

  /**
   * Supplies caller-specific field values after an entity is read.
   *
   * <p>The outer map uses resource identifiers. The inner map contains only fields whose rendered
   * values need replacement. Implementations must resolve a whole page in one batch.
   */
  default Map<Object, Map<String, Object>> readOverrides(List<T> resources, User subject) {
    return Map.of();
  }

  /** Names of fields that {@link #readOverrides} can replace. */
  default Set<String> readOverrideFields() {
    return Set.of();
  }

  /**
   * Optionally supplies complete scalar documents for batched relationship expansion.
   *
   * <p>This lets a read-heavy target avoid hydrating a large domain aggregate. The registration
   * still resolves the real entity lazily if a write uses the same relationship target.
   */
  default Optional<Map<Object, Map<String, Object>>> relationshipReadDocuments(
      Set<ID> ids, User subject) {
    return Optional.empty();
  }

  default T create(ParsedDocument document, ApiV2Caller caller) {
    throw readOnly("create");
  }

  default List<T> createMany(List<ParsedDocument> documents, ApiV2Caller caller) {
    throw readOnly("create");
  }

  default Optional<T> update(ID id, ParsedDocument document, ApiV2Caller caller) {
    throw readOnly("update");
  }

  /** Version-aware singular update; unversioned resources retain their existing implementation. */
  default Optional<T> update(
      ID id, ParsedDocument document, Long expectedVersion, ApiV2Caller caller) {
    return update(id, document, caller);
  }

  /** Read-document field used for a strong ETag, or empty for an unversioned resource. */
  default Optional<String> versionField() {
    return Optional.empty();
  }

  /** Problem code used when a versioned singular update omits {@code If-Match}. */
  default Optional<String> ifMatchRequiredCode() {
    return Optional.empty();
  }

  default List<T> updateMany(ResourceRequest request, ParsedDocument document, ApiV2Caller caller) {
    throw readOnly("update");
  }

  default Optional<T> delete(ID id, ApiV2Caller caller) {
    throw readOnly("delete");
  }

  default List<T> deleteMany(ResourceRequest request, ApiV2Caller caller) {
    throw readOnly("delete");
  }

  private UnsupportedOperationException readOnly(String operation) {
    return new UnsupportedOperationException(
        getClass().getName() + " is read-only; it cannot " + operation);
  }
}
