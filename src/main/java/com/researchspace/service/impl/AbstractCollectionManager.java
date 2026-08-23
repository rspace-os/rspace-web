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
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.CollectionManager;
import com.researchspace.service.CollectionMutationException;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.ObjectProvider;

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
  private final ObjectProvider<ResourceRegistry> resourceRegistry;

  protected AbstractCollectionManager(
      CollectionDao<T, ID> collectionDao,
      CollectionDescription<T> collectionDescription,
      ObjectProvider<ResourceRegistry> resourceRegistry) {
    this(collectionDao, collectionDescription, CollectionMutationLimits.DEFAULT, resourceRegistry);
  }

  protected AbstractCollectionManager(
      CollectionDao<T, ID> collectionDao,
      CollectionDescription<T> collectionDescription,
      CollectionMutationLimits mutationLimits,
      ObjectProvider<ResourceRegistry> resourceRegistry) {
    this.collectionDao = collectionDao;
    this.collectionDescription =
        Objects.requireNonNull(collectionDescription, "Collection description");
    this.mutationLimits = Objects.requireNonNull(mutationLimits, "Collection mutation limits");
    this.resourceRegistry = Objects.requireNonNull(resourceRegistry, "Resource registry provider");
  }

  @Override
  public ResourcePage<T> getResources(ResourceRequest request, User actor) {
    return collectionDao.getResources(authorizeRead(request, actor), relationshipReadAccess(actor));
  }

  @Override
  public long countResources(ResourceRequest request, User actor) {
    return collectionDao.countResources(
        authorizeRead(request, actor), relationshipReadAccess(actor));
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
    // The default sort, not an empty one: this request goes to the paginated query, and Blaze
    // rejects a page with no order by. A single row still needs the ordering to be legal.
    ResourceRequest constrained =
        new ResourceRequest(
                idFilter,
                collectionDescription.defaultSort(),
                new ResourceRequest.Page(1, 1),
                FieldSelection.all(),
                IncludeTree.empty())
            .restrict(access.constraintOrEmpty().orElseThrow());
    return collectionDao
        .getResources(constrained, relationshipReadAccess(actor))
        .resources()
        .stream()
        .findFirst();
  }

  private ResourceRequest authorizeRead(ResourceRequest request, User actor) {
    AccessResult access = readAccess(actor, null);
    return access.constraintOrEmpty().map(request::restrict).orElse(request);
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

  /**
   * Supplies every registered target policy lazily. Unrelated target policies are not evaluated.
   */
  protected RelationshipReadAccess relationshipReadAccess(User actor) {
    return RelationshipReadAccess.forActor(resourceRegistry.getObject(), actor);
  }

  /** Uses the read policy for mutation filters unless a domain explicitly authorizes otherwise. */
  protected RelationshipReadAccess relationshipMutationAccess(User actor) {
    return relationshipReadAccess(actor);
  }

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
        collectionDao.getResources(
            request,
            mutationLimits.maxBulkUpdateDeleteRows() + 1,
            relationshipMutationAccess(actor));
    if (matches.size() > mutationLimits.maxBulkUpdateDeleteRows()) {
      throw new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT);
    }
    return matches;
  }
}
