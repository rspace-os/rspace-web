package com.researchspace.api.v2.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2BadRequestException;
import com.researchspace.api.v2.model.ApiV2BulkResult;
import com.researchspace.api.v2.model.ApiV2CountResult;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessDocumentation.AuthenticationRequirement;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldContext;
import com.researchspace.model.collection.WriteOperation;
import com.researchspace.service.ResourceMutationManager;
import com.researchspace.service.resourceaccess.RemoveSelfResourceAccess;
import com.researchspace.service.resourceaccess.ReplaceResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessDocument;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.shiro.authz.AuthorizationException;

/** One collection registered for automatic REST API v2 CRUD routing. */
public final class ApiV2ResourceRegistration<T, ID> implements ApiV2ReadableResourceTarget {

  private final CollectionDescription<T> description;
  private final ResourceRegistry registry;
  private final ResourceOperations<T, ID> operations;
  private final ApiV2CrudDispatcher<T, ID> crud;
  private final ApiV2ResourceSpec<T, ID> spec;
  private final ApiV2RelationshipResolver relationshipResolver;
  private final ApiV2RuntimeFieldProjection<T> runtimeProjection;

  private final Function<String, List<RuntimeCollectionFields<?>>> providersByResource;

  ApiV2ResourceRegistration(
      ApiV2ResourceSpec<T, ID> spec,
      ResourceRegistry registry,
      ApiV2RelationshipResolver relationshipResolver) {
    this(spec, registry, relationshipResolver, name -> List.of());
  }

  ApiV2ResourceRegistration(
      ApiV2ResourceSpec<T, ID> spec,
      ResourceRegistry registry,
      ApiV2RelationshipResolver relationshipResolver,
      Function<String, List<RuntimeCollectionFields<?>>> providersByResource) {
    this(spec, registry, relationshipResolver, providersByResource, new ResourceMutationManager());
  }

  ApiV2ResourceRegistration(
      ApiV2ResourceSpec<T, ID> spec,
      ResourceRegistry registry,
      ApiV2RelationshipResolver relationshipResolver,
      Function<String, List<RuntimeCollectionFields<?>>> providersByResource,
      ResourceMutationManager mutations) {
    this.providersByResource =
        providersByResource == null ? name -> List.of() : providersByResource;
    this.spec = Objects.requireNonNull(spec, "Resource spec");
    this.description = spec.description();
    this.registry = Objects.requireNonNull(registry, "Resource registry");
    this.operations = spec.operations();
    if (operations.deleteRequiresIfMatch() && operations.ifMatchRequiredCode().isEmpty()) {
      throw new IllegalStateException(
          "Versioned DELETE on " + description.resourceName() + " requires an If-Match error code");
    }
    this.relationshipResolver =
        Objects.requireNonNull(relationshipResolver, "Relationship resolver");
    if (registry.requireResource(description.resourceName()) != description) {
      throw new IllegalArgumentException(
          "REST API v2 resource description is not registered: " + description.resourceName());
    }
    crud =
        new ApiV2CrudDispatcher<>(
            description, registry, operations, relationshipResolver, spec::translate, mutations);
    runtimeProjection =
        new ApiV2RuntimeFieldProjection<>(description, spec, this.providersByResource);
  }

  public String resourceName() {
    return description.resourceName();
  }

  public CollectionDescription<T> description() {
    return description;
  }

  public ResourceRegistry registry() {
    return registry;
  }

  public Set<ResourceOperation> exposedOperations() {
    return spec.exposedOperations();
  }

  public boolean supports(ResourceOperation operation) {
    return spec.exposedOperations().contains(operation);
  }

  public OpenApiOperationDocumentation operationDocumentation(ResourceOperation operation) {
    return spec.documentationFor(operation);
  }

  public ApiV2ListResult<Map<String, Object>> list(ResourceRequest request, User caller) {
    return list(request, caller, false);
  }

  public ApiV2ListResult<Map<String, Object>> list(
      ResourceRequest request, User caller, boolean totals) {
    return list(request, caller, totals, null);
  }

  public ApiV2ListResult<Map<String, Object>> list(
      ResourceRequest request, User caller, boolean totals, Integer max) {
    ResourceRequest authorized = authorize(Operation.READ, caller, request);
    if (operations.usesReadDocuments()) {
      return crud.listReadDocuments(
          authorized,
          caller,
          id ->
              narrowFields(
                  new AccessContext(caller, Operation.READ, description.resourceName(), id),
                  authorized.fields()),
          runtimeReadDocumentValues(authorized, caller),
          totals,
          max);
    }
    return crud.list(
        authorized,
        caller,
        entity ->
            narrowFields(
                new AccessContext(
                    caller,
                    Operation.READ,
                    description.resourceName(),
                    description.idValue(entity)),
                authorized.fields()),
        runtimeValues(authorized, caller),
        totals,
        max);
  }

  public List<RuntimeCollectionFields<T>> runtimeFields() {
    return runtimeProjection.runtimeFields();
  }

  public Optional<ResourceAccessSpec<T, ID>> resourceAccess() {
    return spec.resourceAccess();
  }

  Set<String> relatedAuditFields() {
    return operations.relatedAuditFields();
  }

  Set<String> relatedAuditIdentifierFields() {
    return operations.relatedAuditIdentifierFields();
  }

  boolean auditBypassesActorDirectory() {
    return operations.auditBypassesActorDirectory();
  }

  public ID parseResourceId(String rawId) {
    return parseId(rawId);
  }

  public ResourceAccessDocument getAccess(
      String rawId, User subject, ResourceAccessManager manager) {
    ResourceAccessSpec<T, ID> access = spec.resourceAccess().orElseThrow(NotFoundException::new);
    return manager.get(access.protectedResource(), parseId(rawId), subject);
  }

  public ResourceAccessDocument replaceAccess(
      String rawId,
      long expectedVersion,
      List<com.researchspace.service.resourceaccess.ResourceAccessGrant> assignments,
      ApiV2Caller caller,
      ResourceAccessManager manager) {
    ResourceAccessSpec<T, ID> access = spec.resourceAccess().orElseThrow(NotFoundException::new);
    return manager.replace(
        access.protectedResource(),
        new ReplaceResourceAccess<>(parseId(rawId), expectedVersion, assignments),
        caller.subject(),
        caller.actor());
  }

  public void removeSelfAccess(String rawId, ApiV2Caller caller, ResourceAccessManager manager) {
    ResourceAccessSpec<T, ID> access = spec.resourceAccess().orElseThrow(NotFoundException::new);
    manager.removeSelf(
        access.protectedResource(),
        new RemoveSelfResourceAccess<>(parseId(rawId)),
        caller.subject(),
        caller.actor());
  }

  public Optional<RuntimeCollectionFields<T>> runtimeFields(String namespace) {
    return runtimeProjection.runtimeFields(namespace);
  }

  public List<String> runtimeNamespaces() {
    return runtimeProjection.runtimeNamespaces();
  }

  public RuntimeFieldContext runtimeFieldContext(User caller) {
    return runtimeProjection.runtimeFieldContext(caller);
  }

  /**
   * Adds projected runtime values to an already authorized, rendered page.
   *
   * <p>Specialized list endpoints use this after applying their own read policy and rendering the
   * surviving resources. Runtime providers still receive the caller, preserving their row-level
   * authorization.
   */
  public void decorateRuntimeValues(
      ResourceRequest request,
      User caller,
      List<?> resources,
      List<Map<String, Object>> documents) {
    List<T> typedResources = resources.stream().map(description.entityType()::cast).toList();
    runtimeValues(request, caller).decorate(typedResources, documents);
  }

  public List<RuntimeCollectionFields<?>> providers() {
    return runtimeProjection.providers();
  }

  /**
   * The catalog of runtime fields this caller may name.
   *
   * <p>Behind the collection's own read policy, so an anonymous or refused caller gets the same
   * answer here as for the collection itself and cannot use the catalog as a side channel.
   */
  public RuntimeFieldCatalogPage runtimeFieldCatalog(
      User caller, String namespace, RuntimeFieldCatalogQuery query) {
    decide(new AccessContext(caller, Operation.READ, description.resourceName()), Operation.READ);
    return runtimeFields(namespace)
        .map(provider -> provider.discover(caller, query))
        .orElseGet(RuntimeFieldCatalogPage::empty);
  }

  private ApiV2CrudDispatcher.DocumentDecorator<T> runtimeValues(
      ResourceRequest request, User caller) {
    return runtimeProjection.runtimeValues(request, caller);
  }

  private ApiV2CrudDispatcher.ReadDocumentDecorator runtimeReadDocumentValues(
      ResourceRequest request, User caller) {
    return runtimeProjection.runtimeReadDocumentValues(request, caller);
  }

  public ApiV2CountResult count(ResourceRequest request, User caller) {
    return count(request, caller, null);
  }

  public ApiV2CountResult count(ResourceRequest request, User caller, Integer max) {
    return crud.count(authorize(Operation.READ, caller, request), caller, max);
  }

  public Map<String, Object> get(String rawId, ResourceRequest request, User caller) {
    ID id = parseId(rawId);
    AccessContext context =
        new AccessContext(caller, Operation.READ, description.resourceName(), id);
    AccessResult access = decide(context, Operation.READ);
    rejectUnreadableQueryFields(context, request);
    ResourceRequest narrowed = narrowSelection(context, request);
    if (operations.usesReadDocuments()) {
      ResourceRequest authorized =
          access.constraintOrEmpty().map(narrowed::restrict).orElse(narrowed);
      return crud.getReadDocument(
          id, authorized, caller, runtimeReadDocumentValues(authorized, caller));
    }
    ResourceRequest authorized =
        access.constraintOrEmpty().map(narrowed::restrict).orElse(narrowed);
    return crud.get(id, authorized, caller, runtimeValues(authorized, caller));
  }

  public Optional<String> documentEtag(Map<String, Object> document) {
    return operations
        .versionField()
        .map(document::get)
        .filter(Number.class::isInstance)
        .map(Number.class::cast)
        .map(version -> '"' + Long.toString(version.longValue()) + '"');
  }

  public Optional<String> ifMatchRequiredCode() {
    return operations.ifMatchRequiredCode();
  }

  public boolean deleteRequiresIfMatch() {
    return operations.deleteRequiresIfMatch();
  }

  public boolean supportsPermanentDelete() {
    return operations.supportsPermanentDelete();
  }

  public boolean deleteIsSoft() {
    return operations.deleteIsSoft();
  }

  public Map<String, Object> create(JsonNode body, ApiV2Caller caller) {
    User subject = subject(caller);
    requireDocumentedAuthentication(Operation.CREATE, subject);
    AccessContext base = new AccessContext(subject, Operation.CREATE, description.resourceName());
    ParsedDocument document =
        ApiV2DocumentParser.parseStructure(
            body, description, WriteOperation.CREATE, spec.createErrorKey(), base);
    AccessContext context = authorizeWrite(base.withInput(document));
    ApiV2DocumentParser.authorizeFields(
        body, description, WriteOperation.CREATE, spec.createErrorKey(), context);
    document = resolve(document, subject, context, spec.createErrorKey(), false);
    return crud.create(document, caller, narrowFields(context, FieldSelection.all()));
  }

  public ApiV2BulkResult<Map<String, Object>> createMany(JsonNode body, ApiV2Caller caller) {
    User subject = subject(caller);
    requireDocumentedAuthentication(Operation.CREATE, subject);
    AccessContext base = new AccessContext(subject, Operation.CREATE, description.resourceName());
    List<ParsedDocument> documents =
        ApiV2DocumentParser.parseManyStructure(
            body,
            description,
            spec.createErrorKey(),
            base,
            spec.mutationLimits().maxBulkCreateRows());
    AccessContext context = authorizeWrite(base.withInputs(documents));
    ApiV2DocumentParser.authorizeManyFields(
        body, documents, description, spec.createErrorKey(), context);
    return crud.createMany(
        resolveMany(documents, subject, context, spec.createErrorKey()),
        caller,
        narrowFields(context, FieldSelection.all()));
  }

  public Map<String, Object> update(
      String rawId, JsonNode body, Long expectedVersion, ApiV2Caller caller) {
    User subject = subject(caller);
    ID id = parseId(rawId);
    AccessContext context =
        new AccessContext(subject, Operation.UPDATE, description.resourceName(), id);
    ResourceRequest scope = authorizeWith(context, ResourceRequest.unpaged(idFilter(id)));
    ParsedDocument document =
        ApiV2DocumentParser.parse(
            body, description, WriteOperation.UPDATE, spec.updateErrorKey(), context);
    document = resolve(document, subject, context, spec.updateErrorKey(), false);
    return crud.update(
        id, document, expectedVersion, caller, narrowFields(context, FieldSelection.all()), scope);
  }

  Map<String, Object> update(String rawId, JsonNode body, ApiV2Caller caller) {
    return update(rawId, body, null, caller);
  }

  public ApiV2BulkResult<Map<String, Object>> updateMany(
      ResourceRequest request, JsonNode body, ApiV2Caller caller) {
    User subject = subject(caller);
    AccessContext context =
        new AccessContext(subject, Operation.UPDATE, description.resourceName());
    ResourceRequest authorized = authorizeWith(context, request);
    ParsedDocument document =
        ApiV2DocumentParser.parse(
            body, description, WriteOperation.UPDATE, spec.updateErrorKey(), context);
    document = resolve(document, subject, context, spec.updateErrorKey(), true);
    return crud.updateMany(authorized, document, caller);
  }

  public ResourceDeleteResult<Map<String, Object>> delete(
      String rawId, ResourceDeleteOptions options, ApiV2Caller caller) {
    if (options.permanent() && !operations.supportsPermanentDelete()) {
      throw new ApiV2BadRequestException("errors.api.v2.delete.permanent.unsupported");
    }
    User subject = subject(caller);
    ID id = parseId(rawId);
    Operation operation =
        options.permanent() || !operations.deleteIsSoft()
            ? Operation.DELETE
            : Operation.SOFT_DELETE;
    AccessContext context = new AccessContext(subject, operation, description.resourceName(), id);
    ResourceRequest scope = authorizeWith(context, ResourceRequest.unpaged(idFilter(id)));
    return crud.delete(id, options, caller, narrowFields(context, FieldSelection.all()), scope);
  }

  public ApiV2BulkResult<Map<String, Object>> deleteMany(
      ResourceRequest request, ApiV2Caller caller) {
    User subject = subject(caller);
    Operation operation = operations.deleteIsSoft() ? Operation.SOFT_DELETE : Operation.DELETE;
    AccessContext context = new AccessContext(subject, operation, description.resourceName());
    return crud.deleteMany(authorizeWith(context, request), caller);
  }

  /** Finds one readable entity for the default audit endpoints. */
  ResolvedTarget requireReadableForAudit(
      String rawId, User actor, ResourceAccessManager accessManager) {
    ID id = parseId(rawId);
    AccessContext context =
        new AccessContext(actor, Operation.READ, description.resourceName(), id);
    decide(context, Operation.READ);
    T entity = operations.findByIdForAudit(id, actor).orElseThrow(NotFoundException::new);
    ResolvedTarget target = new ResolvedTarget(entity, narrowFields(context, FieldSelection.all()));
    spec.resourceAccess()
        .ifPresent(
            access -> {
              var resolved =
                  access.protectedResource().isInherited(entity)
                      ? access.protectedResource().resolveInherited(entity, actor)
                      : accessManager.resolve(access.protectedResource().access(entity), actor);
              if (!resolved.hasCapability(access.protectedResource().viewAuditCapability())) {
                throw new AuthorizationException("errors.api.v2.forbidden");
              }
            });
    operations.requireAuditAccess(entity, actor);
    return target;
  }

  /** Finds entities for relationship projection without disclosing why any one is unavailable. */
  @Override
  public Map<Object, ResolvedTarget> resolveReadable(Set<Object> rawIds, User actor) {
    Set<ID> ids = rawIds.stream().map(this::castId).collect(java.util.stream.Collectors.toSet());
    AccessContext context = new AccessContext(actor, Operation.READ, description.resourceName());
    AccessResult result = description.accessPolicy().readAccess().check(context);
    if (result.isDenied()) {
      return Map.of();
    }
    return ApiV2ReadableTargetSupport.hideAuthorizationFailure(
        actor,
        description.resourceName(),
        () -> {
          Optional<Map<Object, Map<String, Object>>> readDocuments =
              operations.relationshipReadDocuments(ids, actor);
          if (readDocuments.isPresent()) {
            Map<Object, ResolvedTarget> resolved = new LinkedHashMap<>();
            readDocuments
                .orElseThrow()
                .forEach(
                    (id, document) ->
                        resolved.put(
                            id,
                            ResolvedTarget.lazy(
                                () ->
                                    operations
                                        .findById(castId(id), actor)
                                        .orElseThrow(NotFoundException::new),
                                narrowFields(
                                    new AccessContext(
                                        actor, Operation.READ, description.resourceName(), id),
                                    FieldSelection.all()),
                                document)));
            return resolved;
          }
          List<T> resources = new ArrayList<>();
          List<ID> orderedIds = new ArrayList<>(ids);
          for (int from = 0;
              from < orderedIds.size();
              from += CollectionQueryLimits.MAX_PAGE_SIZE) {
            List<ID> batch =
                orderedIds.subList(
                    from, Math.min(from + CollectionQueryLimits.MAX_PAGE_SIZE, orderedIds.size()));
            FilterExpression idsFilter =
                new FilterExpression.Comparison(
                    description.idField(), Operator.IN, new ArrayList<>(batch), false);
            resources.addAll(
                operations
                    .find(
                        new ResourceRequest(
                            idsFilter,
                            description.defaultSort(),
                            new ResourceRequest.Page(1, batch.size()),
                            FieldSelection.all(),
                            IncludeTree.empty()),
                        actor)
                    .resources()
                    .stream()
                    .filter(entity -> batch.contains(description.idValue(entity)))
                    .toList());
          }
          Map<Object, Map<String, Object>> overrides = operations.readOverrides(resources, actor);
          Map<Object, ResolvedTarget> resolved = new LinkedHashMap<>();
          for (T entity : resources) {
            Object id = description.idValue(entity);
            resolved.put(
                id,
                new ResolvedTarget(
                    entity,
                    narrowFields(
                        new AccessContext(actor, Operation.READ, description.resourceName(), id),
                        FieldSelection.all()),
                    overrides.getOrDefault(id, Map.of())));
          }
          return resolved;
        });
  }

  /**
   * The single place collection and field access are enforced at the HTTP boundary.
   *
   * <p>Order matters. Field access is checked against the caller's own query first, then the policy
   * decision is made. The read constraint bounds every query; equal manager restrictions are
   * de-duplicated by ResourceRequest.restrict.
   */
  private ResourceRequest authorize(Operation operation, User caller, ResourceRequest request) {
    AccessContext context = new AccessContext(caller, operation, description.resourceName());
    AccessResult result = decide(context, operation);
    rejectUnreadableQueryFields(context, request);
    ResourceRequest narrowed = narrowSelection(context, request);
    return result.constraintOrEmpty().map(narrowed::restrict).orElse(narrowed);
  }

  private ResourceRequest authorizeWith(AccessContext context, ResourceRequest request) {
    AccessResult result = decide(context, context.operation());
    rejectUnreadableQueryFields(context, request);
    ResourceRequest narrowed = narrowSelection(context, request);
    ResourceRequest authorized =
        result.constraintOrEmpty().map(narrowed::restrict).orElse(narrowed);
    AccessContext readContext =
        new AccessContext(
            context.user(), Operation.READ, description.resourceName(), context.targetId());
    AccessResult read = decide(readContext, Operation.READ);
    return read.constraintOrEmpty().map(authorized::restrict).orElse(authorized);
  }

  private FilterExpression idFilter(ID id) {
    return new FilterExpression.Comparison(
        description.idField(), Operator.EQUAL, List.of(id), false);
  }

  /**
   * Authorizes a single-row write and returns the context governing it.
   *
   * <p>The returned context carries the target id, so a {@code writeAccess} lambda can compare the
   * row being written against the caller. Its narrowed field selection also governs the write
   * response, which is subject to the same field access as a read: without that it would echo back
   * a field {@code readAccess} hides, so a write-only secret such as a password would leak in the
   * 201 body while looking configured and safe.
   */
  private AccessContext authorizeWrite(Operation operation, User actor, ID targetId) {
    return authorizeWrite(
        new AccessContext(actor, operation, description.resourceName(), targetId));
  }

  private AccessContext authorizeWrite(AccessContext context) {
    Operation operation = context.operation();
    AccessResult result = decide(context, operation);
    if (result.constraintOrEmpty().isPresent()) {
      // No query to fold a constraint into, so fail loudly rather than proceed unconstrained.
      throw new IllegalStateException(
          "A row constraint cannot be enforced on a single-row "
              + operation
              + " of "
              + description.resourceName());
    }
    return context;
  }

  private AccessResult decide(AccessContext context, Operation operation) {
    AccessResult result = description.accessPolicy().forOperation(operation).check(context);
    if (result instanceof AccessResult.Denied denied) {
      throw refusal(denied.reasonCode());
    }
    return result;
  }

  private void requireDocumentedAuthentication(Operation operation, User actor) {
    AuthenticationRequirement authentication =
        description
            .accessPolicy()
            .forOperation(operation)
            .documentation()
            .orElseThrow()
            .authenticationRequirement();
    if (actor == null && authentication == AuthenticationRequirement.AUTHENTICATED) {
      throw new ApiV2AuthenticationException();
    }
  }

  /**
   * 401 when the caller is anonymous, 403 when they are known but refused, so a client can tell
   * "log in" from "not for you".
   */
  private static RuntimeException refusal(String reasonCode) {
    return AccessPolicy.AUTHENTICATION_REQUIRED.equals(reasonCode)
        ? new ApiV2AuthenticationException()
        : new AuthorizationException("REST API v2 access refused");
  }

  /**
   * Rejects a {@code where} or {@code sort} naming a field the caller may not read, with exactly
   * the error an unknown field produces. Distinguishing the two would let a caller probe for field
   * names; silently dropping the term would let them infer masked values from which rows came back.
   */
  private void rejectUnreadableQueryFields(AccessContext context, ResourceRequest request) {
    queryFields(request.filter())
        .forEach(
            field -> {
              if (request.runtime().find(field) != null) {
                return;
              }
              if (!registry.isQueryFieldReadable(field, context)) {
                throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
              }
            });
    request
        .sort()
        .forEach(
            sort -> {
              if (!description.fieldReadable(sort.field(), context)) {
                throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
              }
            });
  }

  private static Stream<String> queryFields(FilterExpression filter) {
    if (filter == null) {
      return Stream.empty();
    }
    if (filter instanceof FilterExpression.Comparison comparison) {
      return Stream.of(comparison.field());
    }
    if (filter instanceof FilterExpression.And and) {
      return and.children().stream().flatMap(ApiV2ResourceRegistration::queryFields);
    }
    if (filter instanceof FilterExpression.Or or) {
      return or.children().stream().flatMap(ApiV2ResourceRegistration::queryFields);
    }
    throw new IllegalStateException("Unsupported filter expression " + filter.getClass());
  }

  /**
   * Omits unreadable fields from the response rather than refusing the request. Omission cannot be
   * told apart from "absent here", so it leaks nothing.
   */
  private ResourceRequest narrowSelection(AccessContext context, ResourceRequest request) {
    FieldSelection narrowed = narrowFields(context, request.fields());
    if (narrowed == request.fields()) {
      return request;
    }
    return new ResourceRequest(
        request.filter(),
        request.serverConstraint(),
        request.sort(),
        request.page(),
        request.fieldSelections().withRoot(narrowed),
        request.includes(),
        request.runtime());
  }

  /** The subset of {@code selection} this caller may actually see. */
  private FieldSelection narrowFields(AccessContext context, FieldSelection selection) {
    Set<String> unreadable = description.unreadableFields(context);
    if (unreadable.isEmpty()) {
      return selection;
    }
    return switch (selection.mode()) {
      case ALL -> FieldSelection.exclude(unreadable);
      case EXCLUDE -> FieldSelection.exclude(union(selection.fields(), unreadable));
      case INCLUDE -> includeWithoutUnreadable(selection.fields(), unreadable);
    };
  }

  /** An include list emptied by access control still yields the id, never the whole document. */
  private FieldSelection includeWithoutUnreadable(Set<String> requested, Set<String> unreadable) {
    Set<String> remaining = new LinkedHashSet<>(requested);
    remaining.removeAll(unreadable);
    return FieldSelection.include(remaining.isEmpty() ? Set.of(description.idField()) : remaining);
  }

  private static Set<String> union(Set<String> first, Set<String> second) {
    Set<String> all = new LinkedHashSet<>(first);
    all.addAll(second);
    return all;
  }

  private ID parseId(String rawId) {
    try {
      return spec.idParser().apply(rawId);
    } catch (IllegalArgumentException ex) {
      throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
    }
  }

  private ParsedDocument resolve(
      ParsedDocument document,
      User actor,
      AccessContext context,
      String errorKey,
      boolean bulkUpdate) {
    return relationshipResolver.resolve(
        document, description, actor, context, errorKey, bulkUpdate);
  }

  private List<ParsedDocument> resolveMany(
      List<ParsedDocument> documents, User actor, AccessContext context, String errorKey) {
    List<ParsedDocument> resolved = new ArrayList<>(documents.size());
    for (int index = 0; index < documents.size(); index++) {
      try {
        resolved.add(resolve(documents.get(index), actor, context, errorKey, false));
      } catch (DocumentValidationException ex) {
        throw ApiV2DocumentParser.prefixed(ex, "docs[" + index + "]");
      }
    }
    return List.copyOf(resolved);
  }

  private ID castId(Object id) {
    try {
      return spec.idParser().apply(String.valueOf(id));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Relationship target ID has the wrong type", ex);
    }
  }

  private static User subject(ApiV2Caller caller) {
    return caller == null ? null : caller.subject();
  }
}
