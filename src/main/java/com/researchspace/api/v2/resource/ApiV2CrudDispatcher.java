package com.researchspace.api.v2.resource;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.model.ApiV2BulkResult;
import com.researchspace.api.v2.model.ApiV2CountResult;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceFieldSelections;
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
                  targetResolver,
                  readContext(actor));
          decorator.decorate(page.resources(), documents);
          return ApiV2ListResult.of(
              documents, page.total(), request.page().size(), request.page().number());
        });
  }

  /** Adds batched runtime values to an authorized page without constructing entity carriers. */
  @FunctionalInterface
  public interface ReadDocumentDecorator {

    void decorate(List<Map<String, Object>> resources, List<Map<String, Object>> documents);

    static ReadDocumentDecorator none() {
      return (resources, documents) -> {};
    }
  }

  /** Lists scalar documents with per-ID field access, preserving the ordinary page metadata. */
  public ApiV2ListResult<Map<String, Object>> listReadDocuments(
      ResourceRequest request,
      User actor,
      Function<Object, FieldSelection> fieldsForId,
      ReadDocumentDecorator decorator) {
    return invoke(
        ResourceOperation.LIST,
        () -> {
          ResourcePage<Map<String, Object>> page = operations.findReadDocuments(request, actor);
          List<Map<String, Object>> documents =
              renderer.renderReadDocuments(
                  page.resources(), description, fieldsForId, readContext(actor));
          decorator.decorate(page.resources(), documents);
          return ApiV2ListResult.of(
              documents, page.total(), request.page().size(), request.page().number());
        });
  }

  /** Reads one scalar document through the same filtered and constrained page query as a list. */
  public Map<String, Object> getReadDocument(
      ID id, ResourceRequest request, User actor, ReadDocumentDecorator decorator) {
    FilterExpression target =
        new FilterExpression.Comparison(description.idField(), Operator.EQUAL, List.of(id), false);
    FilterExpression filter =
        request.filter() == null
            ? target
            : new FilterExpression.And(List.of(request.filter(), target));
    ResourceRequest matching =
        new ResourceRequest(
            filter,
            request.serverConstraint(),
            request.sort(),
            new ResourceRequest.Page(1, 1),
            request.fieldSelections(),
            request.includes(),
            request.runtime());
    return invoke(
        ResourceOperation.READ,
        () -> {
          Map<String, Object> resource =
              operations.findReadDocuments(matching, actor).resources().stream()
                  .findFirst()
                  .orElseThrow(NotFoundException::new);
          List<Map<String, Object>> documents =
              renderer.renderReadDocuments(
                  List.of(resource), description, ignored -> request.fields(), readContext(actor));
          decorator.decorate(List.of(resource), documents);
          return documents.get(0);
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
        overrides.getOrDefault(description.idValue(resource), Map.of()),
        readContext(actor));
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

  public Map<String, Object> create(
      ParsedDocument document, ApiV2Caller caller, FieldSelection selection) {
    return invoke(
        ResourceOperation.CREATE,
        () -> document(operations.create(document, caller), selection, caller.subject()));
  }

  public ApiV2BulkResult<Map<String, Object>> createMany(
      List<ParsedDocument> documents, ApiV2Caller caller, FieldSelection selection) {
    return invoke(
        ResourceOperation.BULK_CREATE,
        () ->
            new ApiV2BulkResult<>(
                renderMany(operations.createMany(documents, caller), selection, caller.subject())));
  }

  public Map<String, Object> update(
      ID id,
      ParsedDocument document,
      Long expectedVersion,
      ApiV2Caller caller,
      FieldSelection selection) {
    return invoke(
        ResourceOperation.UPDATE,
        () ->
            operations
                .update(id, document, expectedVersion, caller)
                .map(resource -> document(resource, selection, caller.subject()))
                .orElseThrow(NotFoundException::new));
  }

  public ApiV2BulkResult<Map<String, Object>> updateMany(
      ResourceRequest request, ParsedDocument document, ApiV2Caller caller) {
    return invoke(
        ResourceOperation.BULK_UPDATE,
        () ->
            new ApiV2BulkResult<>(
                renderMany(
                    operations.updateMany(request, document, caller),
                    request.fields(),
                    caller.subject())));
  }

  public ResourceDeleteResult<Map<String, Object>> delete(
      ID id, ResourceDeleteOptions options, ApiV2Caller caller, FieldSelection selection) {
    return invoke(
        ResourceOperation.DELETE,
        () ->
            operations
                .delete(id, options, caller)
                .map(
                    result ->
                        result
                            .resource()
                            .map(
                                resource ->
                                    ResourceDeleteResult.retained(
                                        document(resource, selection, caller.subject())))
                            .orElseGet(ResourceDeleteResult::permanentlyDeleted))
                .orElseThrow(NotFoundException::new));
  }

  public ApiV2BulkResult<Map<String, Object>> deleteMany(
      ResourceRequest request, ApiV2Caller caller) {
    return invoke(
        ResourceOperation.BULK_DELETE,
        () ->
            new ApiV2BulkResult<>(
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
   * <p>There is deliberately no overload that renders every field. The selection must always come
   * from {@code ApiV2ResourceRegistration}, which narrows it against the caller's access.
   */
  private Map<String, Object> document(T resource, FieldSelection selection, User actor) {
    return renderer.render(
        resource,
        description,
        ResourceFieldSelections.root(selection),
        IncludeTree.empty(),
        targetResolver(actor),
        Map.of(),
        readContext(actor));
  }

  private List<Map<String, Object>> renderMany(
      List<T> resources, FieldSelection selection, User actor) {
    TargetResolver targetResolver = targetResolver(actor);
    return renderer.renderAll(
        resources,
        description,
        ignored -> selection,
        ignored -> Map.of(),
        ResourceFieldSelections.root(selection),
        IncludeTree.empty(),
        targetResolver,
        readContext(actor));
  }

  private AccessContext readContext(User actor) {
    return new AccessContext(actor, AccessContext.Operation.READ, description.resourceName());
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
