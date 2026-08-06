package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
import java.util.Optional;

/**
 * Collection-specific domain adapter used by the generic REST v2 CRUD dispatcher.
 *
 * <p>A read-only collection pairs the three read methods with {@link
 * com.researchspace.model.collection.AccessPolicy#readOnly}, which refuses every mutation before
 * dispatch reaches the adapter, so the throws below are unreachable defence rather than the
 * mechanism that makes a collection read-only.
 */
public interface ResourceOperations<T, ID> {

  /** Finds one page, resolving any caller-specific values for {@code actor}. */
  ResourcePage<T> find(ResourceRequest request, User actor);

  /** Counts matching resources after caller-specific values are resolved. */
  long count(ResourceRequest request, User actor);

  /**
   * Finds one resource with caller-specific values and collection-specific read authorization.
   *
   * <p>The collection access policy remains the normal authorization mechanism. Collections whose
   * existing permission model cannot be represented as a query constraint can implement that check
   * here. Relationship resolution uses this method, so it cannot bypass those permissions.
   */
  Optional<T> findById(ID id, User actor);

  default T create(ParsedDocument document, User actor) {
    throw readOnly("create");
  }

  default List<T> createMany(List<ParsedDocument> documents, User actor) {
    throw readOnly("create");
  }

  default Optional<T> update(ID id, ParsedDocument document, User actor) {
    throw readOnly("update");
  }

  default List<T> updateMany(ResourceRequest request, ParsedDocument document, User actor) {
    throw readOnly("update");
  }

  default Optional<T> delete(ID id, User actor) {
    throw readOnly("delete");
  }

  default List<T> deleteMany(ResourceRequest request, User actor) {
    throw readOnly("delete");
  }

  private UnsupportedOperationException readOnly(String operation) {
    return new UnsupportedOperationException(
        getClass().getName() + " is read-only; it cannot " + operation);
  }
}
