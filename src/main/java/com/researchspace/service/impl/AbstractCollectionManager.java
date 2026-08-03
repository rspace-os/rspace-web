package com.researchspace.service.impl;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.CollectionDao;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.CollectionManager;
import com.researchspace.service.CollectionMutationException;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Standard transaction-scoped mechanics for a mutable REST API v2 collection.
 *
 * <p>Subclasses retain domain authorization, resource validation, and change notifications.
 */
public abstract class AbstractCollectionManager<T, ID extends Serializable>
    implements CollectionManager<T, ID> {

  private static final int MAX_BULK_ROWS = 1000;

  private final CollectionDao<T, ID> collectionDao;
  private final CollectionDescription<T> collectionDescription;

  protected AbstractCollectionManager(
      CollectionDao<T, ID> collectionDao, CollectionDescription<T> collectionDescription) {
    this.collectionDao = collectionDao;
    this.collectionDescription =
        Objects.requireNonNull(collectionDescription, "Collection description");
  }

  @Override
  public ISearchResults<T> getResources(ResourceRequest request) {
    return collectionDao.getResources(request);
  }

  @Override
  public long countResources(ResourceRequest request) {
    return collectionDao.countResources(request);
  }

  @Override
  public Optional<T> getResource(ID id) {
    return collectionDao.getSafeNull(id);
  }

  @Override
  public T createResource(T resource, User actor) {
    authorizeMutation(actor);
    validateResource(resource);
    T saved = collectionDao.save(resource);
    resourcesChanged();
    return saved;
  }

  @Override
  public Optional<T> updateResource(ID id, ParsedDocument patch, User actor) {
    authorizeMutation(actor);
    return collectionDao
        .getSafeNull(id)
        .map(
            resource -> {
              applyPatch(patch, resource);
              validateResource(resource);
              T saved = collectionDao.save(resource);
              resourcesChanged();
              return saved;
            });
  }

  @Override
  public Optional<T> removeResource(ID id, User actor) {
    authorizeMutation(actor);
    Optional<T> resource = collectionDao.getSafeNull(id);
    resource.ifPresent(
        ignored -> {
          collectionDao.remove(id);
          resourcesChanged();
        });
    return resource;
  }

  @Override
  public List<T> updateResources(ResourceRequest request, ParsedDocument patch, User actor) {
    List<T> matches = bulkMatches(request, actor);
    matches.forEach(
        resource -> {
          applyPatch(patch, resource);
          validateResource(resource);
        });
    matches.forEach(collectionDao::save);
    if (!matches.isEmpty()) {
      resourcesChanged();
    }
    return matches;
  }

  @Override
  public List<T> removeResources(ResourceRequest request, User actor) {
    List<T> matches = bulkMatches(request, actor);
    matches.forEach(resource -> collectionDao.remove(getId(resource)));
    if (!matches.isEmpty()) {
      resourcesChanged();
    }
    return matches;
  }

  /** Refuses a mutation before any row is loaded or changed. */
  protected abstract void authorizeMutation(User actor);

  /** Applies an update document in field-definition order. */
  protected void applyPatch(ParsedDocument patch, T resource) {
    if (patch.operation() != WriteOperation.UPDATE) {
      throw new IllegalArgumentException("Patch requires an update document");
    }
    collectionDescription.apply(resource, patch);
  }

  /** Validates collection-specific invariants after construction or patch application. */
  protected abstract void validateResource(T resource);

  /** Extracts the persistent identifier used for bulk deletion. */
  protected abstract ID getId(T resource);

  /** Runs once after a successful mutation changed at least one resource. */
  protected void resourcesChanged() {}

  private List<T> bulkMatches(ResourceRequest request, User actor) {
    authorizeMutation(actor);
    if (request.filter() == null) {
      throw new CollectionMutationException(CollectionMutationException.Reason.FILTER_REQUIRED);
    }
    List<T> matches = collectionDao.getResources(request, MAX_BULK_ROWS + 1);
    if (matches.size() > MAX_BULK_ROWS) {
      throw new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT);
    }
    return matches;
  }
}
