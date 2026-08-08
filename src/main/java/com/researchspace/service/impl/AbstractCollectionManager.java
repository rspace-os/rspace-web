package com.researchspace.service.impl;

import com.researchspace.dao.CollectionDao;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.CollectionManager;
import com.researchspace.service.CollectionMutationException;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;

/**
 * Standard transaction-scoped mechanics for a mutable REST API v2 collection.
 *
 * <p>Subclasses retain domain authorization, resource validation, and change notifications.
 */
public abstract class AbstractCollectionManager<T, ID extends Serializable>
    implements CollectionManager<T, ID> {

  private final CollectionDao<T, ID> collectionDao;
  private final CollectionDescription<T> collectionDescription;
  private final CollectionMutationLimits mutationLimits;

  protected AbstractCollectionManager(
      CollectionDao<T, ID> collectionDao, CollectionDescription<T> collectionDescription) {
    this(collectionDao, collectionDescription, CollectionMutationLimits.DEFAULT);
  }

  protected AbstractCollectionManager(
      CollectionDao<T, ID> collectionDao,
      CollectionDescription<T> collectionDescription,
      CollectionMutationLimits mutationLimits) {
    this.collectionDao = collectionDao;
    this.collectionDescription =
        Objects.requireNonNull(collectionDescription, "Collection description");
    this.mutationLimits = Objects.requireNonNull(mutationLimits, "Collection mutation limits");
  }

  @Override
  public ResourcePage<T> getResources(ResourceRequest request, User actor) {
    return collectionDao.getResources(authorizeRead(request, actor));
  }

  @Override
  public long countResources(ResourceRequest request, User actor) {
    return collectionDao.countResources(authorizeRead(request, actor));
  }

  @Override
  public Optional<T> getResource(ID id, User actor) {
    AccessResult access = readAccess(actor, id);
    if (access.constraintOrEmpty().isEmpty()) {
      return collectionDao.getSafeNull(id);
    }
    FilterExpression idFilter =
        new FilterExpression.Comparison(
            collectionDescription.idField(), Operator.EQUAL, List.of(id), false);
    ResourceRequest constrained =
        ResourceRequest.unpaged(and(access.constraintOrEmpty().orElseThrow(), idFilter));
    return collectionDao.getResources(constrained).resources().stream().findFirst();
  }

  private ResourceRequest authorizeRead(ResourceRequest request, User actor) {
    AccessResult access = readAccess(actor, null);
    return access
        .constraintOrEmpty()
        .map(
            constraint ->
                new ResourceRequest(
                    and(constraint, request.filter()),
                    request.sort(),
                    request.page(),
                    request.fieldSelections(),
                    request.includes()))
        .orElse(request);
  }

  private AccessResult readAccess(User actor, Object id) {
    AccessResult access =
        collectionDescription
            .accessPolicy()
            .readAccess()
            .check(
                new AccessContext(actor, Operation.READ, collectionDescription.resourceName(), id));
    if (access.isDenied()) {
      throw new AuthorizationException("Collection read access refused");
    }
    return access;
  }

  private static FilterExpression and(FilterExpression left, FilterExpression right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return new FilterExpression.And(List.of(left, right));
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
  public List<T> createResources(List<T> resources, User actor) {
    authorizeMutation(actor);
    if (resources.size() > mutationLimits.maxBulkCreateRows()) {
      throw new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT);
    }
    resources.forEach(this::validateResource);
    List<T> saved = resources.stream().map(collectionDao::save).toList();
    if (!saved.isEmpty()) {
      resourcesChanged();
    }
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
    List<T> matches =
        collectionDao.getResources(request, mutationLimits.maxBulkUpdateDeleteRows() + 1);
    if (matches.size() > mutationLimits.maxBulkUpdateDeleteRows()) {
      throw new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT);
    }
    return matches;
  }
}
