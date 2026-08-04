package com.researchspace.api.v2.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
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
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.permissions.SecurityLogger;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One collection registered for automatic REST API v2 CRUD routing. */
public final class ApiV2ResourceRegistration<T, ID> implements ApiV2ReadableResourceTarget {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private final CollectionDescription<T> description;
  private final ResourceRegistry registry;
  private final ResourceOperations<T, ID> operations;
  private final ApiV2CrudDispatcher<T, ID> crud;
  private final ApiV2ResourceSpec<T, ID> spec;
  private final ApiV2RelationshipResolver relationshipResolver;

  ApiV2ResourceRegistration(
      ApiV2ResourceSpec<T, ID> spec,
      ResourceRegistry registry,
      ApiV2RelationshipResolver relationshipResolver) {
    this.spec = Objects.requireNonNull(spec, "Resource spec");
    this.description = spec.description();
    this.registry = Objects.requireNonNull(registry, "Resource registry");
    this.operations = spec.operations();
    this.relationshipResolver =
        Objects.requireNonNull(relationshipResolver, "Relationship resolver");
    if (registry.requireResource(description.resourceName()) != description) {
      throw new IllegalArgumentException(
          "REST API v2 resource description is not registered: " + description.resourceName());
    }
    crud = new ApiV2CrudDispatcher<>(description, registry, operations, relationshipResolver);
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
    return spec.operationDocumentation()
        .getOrDefault(operation, OpenApiOperationDocumentation.EMPTY);
  }

  public ApiV2ListResult<Map<String, Object>> list(ResourceRequest request, User caller) {
    return crud.list(authorize(Operation.READ, caller, request), caller);
  }

  public ApiV2CountResult count(ResourceRequest request, User caller) {
    return crud.count(authorize(Operation.READ, caller, request), caller);
  }

  public Map<String, Object> get(String rawId, ResourceRequest request, User caller) {
    ID id = parseId(rawId);
    AccessContext context =
        new AccessContext(caller, Operation.READ, description.resourceName(), id);
    AccessResult result = decide(context, Operation.READ);
    rejectUnreadableQueryFields(context, request);
    ResourceRequest narrowed = narrowSelection(context, request);
    Optional<FilterExpression> constraint = result.constraintOrEmpty();
    if (constraint.isEmpty()) {
      return crud.get(id, narrowed, caller);
    }
    return crud.getMatching(
        singleRow(withFilter(narrowed, and(constraint.get(), idEquals(id)))), caller);
  }

  public Map<String, Object> create(JsonNode body, User actor) {
    requireDocumentedAuthentication(Operation.CREATE, actor);
    AccessContext base = new AccessContext(actor, Operation.CREATE, description.resourceName());
    ParsedDocument document =
        ApiV2DocumentParser.parseStructure(
            body, description, WriteOperation.CREATE, spec.createErrorKey(), base);
    AccessContext context = authorizeWrite(base.withInput(document));
    ApiV2DocumentParser.authorizeFields(
        body, description, WriteOperation.CREATE, spec.createErrorKey(), context);
    document = resolve(document, actor, context, spec.createErrorKey(), false);
    return crud.create(document, actor, narrowFields(context, FieldSelection.all()));
  }

  public ApiV2BulkResult<Map<String, Object>> createMany(JsonNode body, User actor) {
    requireDocumentedAuthentication(Operation.CREATE, actor);
    AccessContext base = new AccessContext(actor, Operation.CREATE, description.resourceName());
    List<ParsedDocument> documents =
        ApiV2DocumentParser.parseManyStructure(body, description, spec.createErrorKey(), base);
    AccessContext context = authorizeWrite(base.withInputs(documents));
    ApiV2DocumentParser.authorizeManyFields(
        body, documents, description, spec.createErrorKey(), context);
    return crud.createMany(
        resolveMany(documents, actor, context, spec.createErrorKey()),
        actor,
        narrowFields(context, FieldSelection.all()));
  }

  public Map<String, Object> update(String rawId, JsonNode body, User actor) {
    ID id = parseId(rawId);
    AccessContext context = authorizeWrite(Operation.UPDATE, actor, id);
    ParsedDocument document =
        ApiV2DocumentParser.parse(
            body, description, WriteOperation.UPDATE, spec.updateErrorKey(), context);
    document = resolve(document, actor, context, spec.updateErrorKey(), false);
    return crud.update(id, document, actor, narrowFields(context, FieldSelection.all()));
  }

  public ApiV2BulkResult<Map<String, Object>> updateMany(
      ResourceRequest request, JsonNode body, User actor) {
    AccessContext context = new AccessContext(actor, Operation.UPDATE, description.resourceName());
    ResourceRequest authorized = authorizeWith(context, request);
    ParsedDocument document =
        ApiV2DocumentParser.parse(
            body, description, WriteOperation.UPDATE, spec.updateErrorKey(), context);
    document = resolve(document, actor, context, spec.updateErrorKey(), true);
    return crud.updateMany(authorized, document, actor);
  }

  public Map<String, Object> delete(String rawId, User actor) {
    ID id = parseId(rawId);
    AccessContext context = authorizeWrite(Operation.DELETE, actor, id);
    return crud.delete(id, actor, narrowFields(context, FieldSelection.all()));
  }

  public ApiV2BulkResult<Map<String, Object>> deleteMany(ResourceRequest request, User actor) {
    AccessContext context = new AccessContext(actor, Operation.DELETE, description.resourceName());
    return crud.deleteMany(authorizeWith(context, request), actor);
  }

  /** Finds one readable entity for the default audit endpoints. */
  ResolvedTarget requireReadableForAudit(String rawId, User actor) {
    return resolveReadable(parseId(rawId), actor).orElseThrow(NotFoundException::new);
  }

  /** Finds one entity for a relationship without disclosing why it is unavailable. */
  @Override
  public Optional<ResolvedTarget> resolveReadable(Object rawId, User actor) {
    ID id = castId(rawId);
    AccessContext context =
        new AccessContext(actor, Operation.READ, description.resourceName(), id);
    AccessResult result = description.accessPolicy().readAccess().check(context);
    if (result.isDenied()) {
      return Optional.empty();
    }
    try {
      return result
          .constraintOrEmpty()
          .map(
              constraint ->
                  operations
                      .find(ResourceRequest.unpaged(and(constraint, idEquals(id))), actor)
                      .resources()
                      .stream()
                      .findFirst())
          .orElseGet(() -> operations.findById(id, actor))
          .map(entity -> new ResolvedTarget(entity, narrowFields(context, FieldSelection.all())));
    } catch (AuthorizationException ex) {
      SECURITY_LOG.warn(
          "REST API v2 relationship target authorization failure for user [{}], resource [{}]",
          actor == null ? "(anonymous)" : actor.getUsername(),
          description.resourceName(),
          ex);
      return Optional.empty();
    }
  }

  /**
   * The single place collection and field access are enforced.
   *
   * <p>Order matters. Field access is checked against the caller's own query first, then the
   * policy's constraint is folded in: the constraint legitimately references fields the caller may
   * not read (an ownership check on {@code id} being the obvious case), so it must not be subject
   * to the same rejection.
   */
  private ResourceRequest authorize(Operation operation, User caller, ResourceRequest request) {
    return authorizeWith(new AccessContext(caller, operation, description.resourceName()), request);
  }

  private ResourceRequest authorizeWith(AccessContext context, ResourceRequest request) {
    AccessResult result = decide(context, context.operation());
    rejectUnreadableQueryFields(context, request);
    ResourceRequest narrowed = narrowSelection(context, request);
    return result
        .constraintOrEmpty()
        .map(constraint -> withFilter(narrowed, and(constraint, narrowed.filter())))
        .orElse(narrowed);
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

  private FilterExpression idEquals(ID id) {
    return new FilterExpression.Comparison(
        description.idField(), CollectionDescription.Operator.EQUAL, List.of(id), false);
  }

  /** A get returns at most one row, whatever page the request carried. */
  private static ResourceRequest singleRow(ResourceRequest request) {
    return new ResourceRequest(
        request.filter(),
        request.sort(),
        new ResourceRequest.Page(1, 1),
        request.fieldSelections(),
        request.includes());
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
              if (!description.fieldReadable(field, context)) {
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
   * Omits unreadable fields from the response rather than refusing the request, matching
   * PayloadCMS, which deletes them after read and never errors. Omission cannot be told apart from
   * "absent here", so it leaks nothing.
   */
  private ResourceRequest narrowSelection(AccessContext context, ResourceRequest request) {
    FieldSelection narrowed = narrowFields(context, request.fields());
    if (narrowed == request.fields()) {
      return request;
    }
    return new ResourceRequest(
        request.filter(),
        request.sort(),
        request.page(),
        request.fieldSelections().withRoot(narrowed),
        request.includes());
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

  private static FilterExpression and(FilterExpression constraint, FilterExpression caller) {
    return caller == null ? constraint : new FilterExpression.And(List.of(constraint, caller));
  }

  private static ResourceRequest withFilter(ResourceRequest request, FilterExpression filter) {
    return new ResourceRequest(
        filter, request.sort(), request.page(), request.fieldSelections(), request.includes());
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

  @SuppressWarnings("unchecked")
  private ID castId(Object id) {
    try {
      return (ID) description.requireField(description.idField()).type().javaType().cast(id);
    } catch (ClassCastException ex) {
      throw new IllegalArgumentException("Relationship target ID has the wrong type", ex);
    }
  }
}
