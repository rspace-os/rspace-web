package com.researchspace.api.v2.resource;

import com.researchspace.api.v2.model.ApiV2BulkResult;
import com.researchspace.api.v2.model.ApiV2CountResult;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Shared CRUD mechanics for Payload-shaped REST v2 collection endpoints. */
public final class ApiV2CrudDispatcher<T, ID> {

  private final CollectionDescription<T> description;
  private final ResourceOperations<T, ID> operations;
  private final ResourceRenderer renderer;
  private final ApiV2RelationshipResolver relationshipResolver;
  private final BiFunction<ResourceOperation, RuntimeException, RuntimeException> errorTranslator;

  public ApiV2CrudDispatcher(
      CollectionDescription<T> description,
      ResourceRegistry registry,
      ResourceOperations<T, ID> operations) {
    this(
        description,
        registry,
        operations,
        ApiV2RelationshipResolver.unavailable(),
        (operation, exception) -> exception);
  }

  ApiV2CrudDispatcher(
      CollectionDescription<T> description,
      ResourceRegistry registry,
      ResourceOperations<T, ID> operations,
      ApiV2RelationshipResolver relationshipResolver,
      BiFunction<ResourceOperation, RuntimeException, RuntimeException> errorTranslator) {
    this.description = description;
    this.operations = operations;
    this.relationshipResolver = relationshipResolver;
    this.errorTranslator = Objects.requireNonNull(errorTranslator, "Resource error translator");
    renderer = new ResourceRenderer(registry);
  }

  public ApiV2ListResult<Map<String, Object>> list(ResourceRequest request, User actor) {
    return invoke(
        ResourceOperation.LIST,
        () -> {
          ResourcePage<T> page = operations.find(request, actor);
          TargetResolver targetResolver = targetResolver(actor);
          return ApiV2ListResult.of(
              page.resources().stream()
                  .map(resource -> document(resource, request, targetResolver))
                  .toList(),
              page.total(),
              request.page().size(),
              request.page().number());
        });
  }

  public ApiV2CountResult count(ResourceRequest request, User actor) {
    return invoke(
        ResourceOperation.COUNT, () -> new ApiV2CountResult(operations.count(request, actor)));
  }

  public Map<String, Object> get(ID id, ResourceRequest request, User actor) {
    return invoke(
        ResourceOperation.READ,
        () ->
            operations
                .findById(id, actor)
                .map(resource -> document(resource, request, targetResolver(actor)))
                .orElseThrow(NotFoundException::new));
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
    return invoke(
        ResourceOperation.READ,
        () ->
            operations.find(request, actor).resources().stream()
                .findFirst()
                .map(resource -> document(resource, request, targetResolver(actor)))
                .orElseThrow(NotFoundException::new));
  }

  public Map<String, Object> create(ParsedDocument document, User actor, FieldSelection selection) {
    return invoke(
        ResourceOperation.CREATE,
        () -> document(operations.create(document, actor), selection, targetResolver(actor)));
  }

  public ApiV2BulkResult<Map<String, Object>> createMany(
      List<ParsedDocument> documents, User actor, FieldSelection selection) {
    return invoke(
        ResourceOperation.BULK_CREATE,
        () ->
            ApiV2BulkResult.success(
                renderMany(operations.createMany(documents, actor), selection, actor)));
  }

  public Map<String, Object> update(
      ID id, ParsedDocument document, User actor, FieldSelection selection) {
    return invoke(
        ResourceOperation.UPDATE,
        () ->
            operations
                .update(id, document, actor)
                .map(resource -> document(resource, selection, targetResolver(actor)))
                .orElseThrow(NotFoundException::new));
  }

  public ApiV2BulkResult<Map<String, Object>> updateMany(
      ResourceRequest request, ParsedDocument document, User actor) {
    return invoke(
        ResourceOperation.BULK_UPDATE,
        () ->
            ApiV2BulkResult.success(
                renderMany(
                    operations.updateMany(request, document, actor), request.fields(), actor)));
  }

  public Map<String, Object> delete(ID id, User actor, FieldSelection selection) {
    return invoke(
        ResourceOperation.DELETE,
        () ->
            operations
                .delete(id, actor)
                .map(resource -> document(resource, selection, targetResolver(actor)))
                .orElseThrow(NotFoundException::new));
  }

  public ApiV2BulkResult<Map<String, Object>> deleteMany(ResourceRequest request, User actor) {
    return invoke(
        ResourceOperation.BULK_DELETE,
        () ->
            ApiV2BulkResult.success(
                renderMany(operations.deleteMany(request, actor), request.fields(), actor)));
  }

  private <R> R invoke(ResourceOperation operation, Supplier<R> action) {
    try {
      return action.get();
    } catch (RuntimeException exception) {
      throw errorTranslator.apply(operation, exception);
    }
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

  private List<Map<String, Object>> renderMany(
      List<T> resources, FieldSelection selection, User actor) {
    TargetResolver targetResolver = targetResolver(actor);
    return resources.stream()
        .map(resource -> document(resource, selection, targetResolver))
        .toList();
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
