package com.researchspace.api.v2.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.model.ApiV2BulkResult;
import com.researchspace.api.v2.model.ApiV2CountResult;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRenderer;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import com.researchspace.model.collection.ResourceRenderer.TargetResolver;
import com.researchspace.model.collection.ResourceRequest;
import jakarta.ws.rs.NotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Shared CRUD mechanics for Payload-shaped REST v2 collection endpoints. */
public final class ApiV2CrudDispatcher<T, ID> {

  private final CollectionDescription<T> description;
  private final ResourceOperations<T, ID> operations;
  private final ResourceRenderer renderer;
  private final ApiV2RelationshipResolver relationshipResolver;

  public ApiV2CrudDispatcher(
      CollectionDescription<T> description,
      ResourceRegistry registry,
      ResourceOperations<T, ID> operations) {
    this(description, registry, operations, ApiV2RelationshipResolver.unavailable());
  }

  ApiV2CrudDispatcher(
      CollectionDescription<T> description,
      ResourceRegistry registry,
      ResourceOperations<T, ID> operations,
      ApiV2RelationshipResolver relationshipResolver) {
    this.description = description;
    this.operations = operations;
    this.relationshipResolver = relationshipResolver;
    renderer = new ResourceRenderer(registry);
  }

  public ApiV2ListResult<Map<String, Object>> list(ResourceRequest request, User actor) {
    ResourcePage<T> page = operations.find(request);
    TargetResolver targetResolver = targetResolver(actor);
    return ApiV2ListResult.of(
        page.resources().stream()
            .map(resource -> document(resource, request, targetResolver))
            .toList(),
        page.total(),
        request.page().size(),
        request.page().number());
  }

  public ApiV2CountResult count(ResourceRequest request) {
    return new ApiV2CountResult(operations.count(request));
  }

  public Map<String, Object> get(ID id, ResourceRequest request, User actor) {
    return operations
        .findById(id, actor)
        .map(resource -> document(resource, request, targetResolver(actor)))
        .orElseThrow(NotFoundException::new);
  }

  /**
   * The single row matching a request's filter, for a {@code get} whose access policy returned a
   * row constraint. Goes through {@link ResourceOperations#find} rather than {@code findById}
   * because only the query can apply the constraint; {@code findById} sees an id and nothing else,
   * so a constraint would be silently dropped and the row returned to a caller not entitled to it.
   *
   * <p>A row excluded by the constraint is a 404, not a 403: telling the two apart would let a
   * caller enumerate ids that exist, which is the same probe the {@code where} and {@code sort}
   * checks close.
   */
  public Map<String, Object> getMatching(ResourceRequest request, User actor) {
    return operations.find(request).resources().stream()
        .findFirst()
        .map(resource -> document(resource, request, targetResolver(actor)))
        .orElseThrow(NotFoundException::new);
  }

  public Map<String, Object> create(
      JsonNode body, User actor, String errorKey, FieldSelection selection, AccessContext context) {
    ParsedDocument document =
        ApiV2DocumentParser.parse(body, description, WriteOperation.CREATE, errorKey, context);
    document = relationshipResolver.resolve(document, description, actor, context, errorKey, false);
    return document(operations.create(document, actor), selection, targetResolver(actor));
  }

  public Map<String, Object> update(
      ID id,
      JsonNode body,
      User actor,
      String errorKey,
      FieldSelection selection,
      AccessContext context) {
    ParsedDocument document =
        ApiV2DocumentParser.parse(body, description, WriteOperation.UPDATE, errorKey, context);
    document = relationshipResolver.resolve(document, description, actor, context, errorKey, false);
    return operations
        .update(id, document, actor)
        .map(resource -> document(resource, selection, targetResolver(actor)))
        .orElseThrow(NotFoundException::new);
  }

  public ApiV2BulkResult<Map<String, Object>> updateMany(
      ResourceRequest request, JsonNode body, User actor, String errorKey, AccessContext context) {
    ParsedDocument document =
        ApiV2DocumentParser.parse(body, description, WriteOperation.UPDATE, errorKey, context);
    document = relationshipResolver.resolve(document, description, actor, context, errorKey, true);
    return ApiV2BulkResult.success(
        operations.updateMany(request, document, actor).stream()
            .map(resource -> document(resource, request.fields(), targetResolver(actor)))
            .toList());
  }

  public Map<String, Object> delete(ID id, User actor, FieldSelection selection) {
    return operations
        .delete(id, actor)
        .map(resource -> document(resource, selection, targetResolver(actor)))
        .orElseThrow(NotFoundException::new);
  }

  public ApiV2BulkResult<Map<String, Object>> deleteMany(ResourceRequest request, User actor) {
    return ApiV2BulkResult.success(
        operations.deleteMany(request, actor).stream()
            .map(resource -> document(resource, request.fields(), targetResolver(actor)))
            .toList());
  }

  private Map<String, Object> document(
      T resource, ResourceRequest request, TargetResolver targetResolver) {
    return renderer.render(
        resource, description, request.fieldSelections(), request.includes(), targetResolver);
  }

  /**
   * Renders a write response.
   *
   * <p>There is deliberately no overload that renders every field. Write responses used to call
   * one, which meant a field restricted by {@code readAccess} was echoed straight back in the 201
   * or 200 body even though a subsequent read would have hidden it. The selection must always come
   * from {@code ApiV2ResourceRegistration}, which narrows it against the caller's access.
   */
  private Map<String, Object> document(
      T resource, FieldSelection selection, TargetResolver targetResolver) {
    return renderer.render(resource, description, selection, IncludeTree.empty(), targetResolver);
  }

  private TargetResolver targetResolver(User actor) {
    Map<TargetKey, Optional<ResolvedTarget>> cache = new HashMap<>();
    return (resourceName, id) ->
        cache.computeIfAbsent(
            new TargetKey(resourceName, id),
            key -> relationshipResolver.resolveReadable(key.resourceName(), key.id(), actor));
  }

  private record TargetKey(String resourceName, Object id) {}
}
