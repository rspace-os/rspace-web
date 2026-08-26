package com.researchspace.api.v2.resource;

import com.researchspace.api.v2.auth.ApiV2Caller;
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
import com.researchspace.model.collection.ResourceRenderer.TargetKey;
import com.researchspace.model.collection.ResourceRenderer.TargetResolver;
import com.researchspace.model.collection.ResourceRequest;
import jakarta.ws.rs.NotFoundException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
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

  /** Adds values the description cannot render, given a whole page at once. */
  @FunctionalInterface
  public interface DocumentDecorator<T> {

    void decorate(List<T> resources, List<Map<String, Object>> documents);

    static <T> DocumentDecorator<T> none() {
      return (resources, documents) -> {};
    }
  }

  public ApiV2ListResult<Map<String, Object>> list(ResourceRequest request, User actor) {
    return list(request, actor, ignored -> request.fields());
  }

  public ApiV2ListResult<Map<String, Object>> list(
      ResourceRequest request, User actor, Function<T, FieldSelection> fieldsForRow) {
    return list(request, actor, fieldsForRow, DocumentDecorator.none());
  }

  /**
   * Lists a page and lets a decorator add values that no field reader can produce.
   *
   * <p>The decorator receives the whole page rather than one row, which is what keeps a projected
   * runtime field to a single extra query instead of one per row. Documents are in the same order
   * as their resources, because {@code renderAll} maps them positionally.
   */
  public ApiV2ListResult<Map<String, Object>> list(
      ResourceRequest request,
      User actor,
      Function<T, FieldSelection> fieldsForRow,
      DocumentDecorator<T> decorator) {
    return invoke(
        ResourceOperation.LIST,
        () -> {
          ResourcePage<T> page = operations.find(request, actor);
          TargetResolver targetResolver = targetResolver(actor);
          Map<Object, Map<String, Object>> overrides =
              readOverrides(page.resources(), fieldsForRow, actor);
          List<Map<String, Object>> documents =
              renderer.renderAll(
                  page.resources(),
                  description,
                  fieldsForRow,
                  resource -> overrides.getOrDefault(description.idValue(resource), Map.of()),
                  request.fieldSelections(),
                  request.includes(),
                  targetResolver);
          decorator.decorate(page.resources(), documents);
          return ApiV2ListResult.of(
              documents, page.total(), request.page().size(), request.page().number());
        });
  }

  public ApiV2CountResult count(ResourceRequest request, User actor) {
    return invoke(
        ResourceOperation.COUNT, () -> new ApiV2CountResult(operations.count(request, actor)));
  }

  public Map<String, Object> get(ID id, ResourceRequest request, User actor) {
    return get(id, request, actor, DocumentDecorator.none());
  }

  public Map<String, Object> get(
      ID id, ResourceRequest request, User actor, DocumentDecorator<T> decorator) {
    return invoke(
        ResourceOperation.READ,
        () ->
            operations
                .findById(id, actor)
                .map(resource -> decorated(resource, request, actor, decorator))
                .orElseThrow(NotFoundException::new));
  }

  private Map<String, Object> decorated(
      T resource, ResourceRequest request, User actor, DocumentDecorator<T> decorator) {
    Map<String, Object> document = document(resource, request, actor);
    decorator.decorate(List.of(resource), List.of(document));
    return document;
  }

  private Map<String, Object> document(T resource, ResourceRequest request, User actor) {
    Map<Object, Map<String, Object>> overrides =
        readOverrides(List.of(resource), ignored -> request.fields(), actor);
    return renderer.render(
        resource,
        description,
        request.fieldSelections(),
        request.includes(),
        targetResolver(actor),
        overrides.getOrDefault(description.idValue(resource), Map.of()));
  }

  private Map<Object, Map<String, Object>> readOverrides(
      List<T> resources, Function<T, FieldSelection> fieldsForResource, User actor) {
    Set<String> overrideFields = operations.readOverrideFields();
    if (overrideFields.isEmpty()
        || resources.stream()
            .noneMatch(
                resource ->
                    overrideFields.stream()
                        .anyMatch(
                            field ->
                                fieldsForResource
                                    .apply(resource)
                                    .includes(field, description.idField())))) {
      return Map.of();
    }
    return operations.readOverrides(resources, actor);
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
    return getMatching(request, actor, DocumentDecorator.none());
  }

  public Map<String, Object> getMatching(
      ResourceRequest request, User actor, DocumentDecorator<T> decorator) {
    return invoke(
        ResourceOperation.READ,
        () ->
            operations.find(request, actor).resources().stream()
                .findFirst()
                .map(resource -> decorated(resource, request, actor, decorator))
                .orElseThrow(NotFoundException::new));
  }

  public Map<String, Object> create(
      ParsedDocument document, ApiV2Caller caller, FieldSelection selection) {
    return invoke(
        ResourceOperation.CREATE,
        () ->
            document(
                operations.create(document, caller), selection, targetResolver(caller.subject())));
  }

  public ApiV2BulkResult<Map<String, Object>> createMany(
      List<ParsedDocument> documents, ApiV2Caller caller, FieldSelection selection) {
    return invoke(
        ResourceOperation.BULK_CREATE,
        () ->
            ApiV2BulkResult.success(
                renderMany(operations.createMany(documents, caller), selection, caller.subject())));
  }

  public Map<String, Object> update(
      ID id, ParsedDocument document, ApiV2Caller caller, FieldSelection selection) {
    return invoke(
        ResourceOperation.UPDATE,
        () ->
            operations
                .update(id, document, caller)
                .map(resource -> document(resource, selection, targetResolver(caller.subject())))
                .orElseThrow(NotFoundException::new));
  }

  public ApiV2BulkResult<Map<String, Object>> updateMany(
      ResourceRequest request, ParsedDocument document, ApiV2Caller caller) {
    return invoke(
        ResourceOperation.BULK_UPDATE,
        () ->
            ApiV2BulkResult.success(
                renderMany(
                    operations.updateMany(request, document, caller),
                    request.fields(),
                    caller.subject())));
  }

  public Map<String, Object> delete(ID id, ApiV2Caller caller, FieldSelection selection) {
    return invoke(
        ResourceOperation.DELETE,
        () ->
            operations
                .delete(id, caller)
                .map(resource -> document(resource, selection, targetResolver(caller.subject())))
                .orElseThrow(NotFoundException::new));
  }

  public ApiV2BulkResult<Map<String, Object>> deleteMany(
      ResourceRequest request, ApiV2Caller caller) {
    return invoke(
        ResourceOperation.BULK_DELETE,
        () ->
            ApiV2BulkResult.success(
                renderMany(
                    operations.deleteMany(request, caller), request.fields(), caller.subject())));
  }

  private <R> R invoke(ResourceOperation operation, Supplier<R> action) {
    try {
      return action.get();
    } catch (RuntimeException exception) {
      throw errorTranslator.apply(operation, exception);
    }
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
    return renderer.renderAll(
        resources,
        description,
        ignored -> selection,
        com.researchspace.model.collection.ResourceFieldSelections.root(selection),
        IncludeTree.empty(),
        targetResolver);
  }

  private TargetResolver targetResolver(User actor) {
    return new TargetResolver() {
      @Override
      public Optional<ResolvedTarget> resolve(String resourceName, Object id) {
        return relationshipResolver
            .resolveReadable(Set.of(new TargetKey(resourceName, id)), actor)
            .getOrDefault(new TargetKey(resourceName, id), Optional.empty());
      }

      @Override
      public Map<TargetKey, Optional<ResolvedTarget>> resolveAll(Collection<TargetKey> targets) {
        return relationshipResolver.resolveReadable(new LinkedHashSet<>(targets), actor);
      }
    };
  }
}
