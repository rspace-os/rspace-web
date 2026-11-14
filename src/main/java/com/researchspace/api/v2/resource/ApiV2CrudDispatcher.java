package com.researchspace.api.v2.resource;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.model.ApiV2BulkResult;
import com.researchspace.api.v2.model.ApiV2CountResult;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRenderer;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import com.researchspace.model.collection.ResourceRenderer.TargetKey;
import com.researchspace.model.collection.ResourceRenderer.TargetResolver;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.ResourceMutationManager;
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

/** Shared CRUD mechanics for REST v2 collection endpoints. */
public final class ApiV2CrudDispatcher<T, ID> {

  private final ResourceMutationManager mutations;
  private final MutationPolicy mutationPolicy;
  private final CollectionDescription<T> description;
  private final ResourceOperations<T, ID> operations;
  private final ResourceRenderer renderer;
  private final ApiV2RelationshipResolver relationshipResolver;
  private final BiFunction<ResourceOperation, RuntimeException, RuntimeException> errorTranslator;

  ApiV2CrudDispatcher(
      CollectionDescription<T> description,
      ResourceRegistry registry,
      ResourceOperations<T, ID> operations,
      ApiV2RelationshipResolver relationshipResolver,
      BiFunction<ResourceOperation, RuntimeException, RuntimeException> errorTranslator,
      ResourceMutationManager mutations,
      MutationPolicy mutationPolicy) {
    this.mutationPolicy = mutationPolicy;
    this.mutations = mutations;
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
      DocumentDecorator<T> decorator,
      boolean totals,
      Integer max) {
    return invoke(
        ResourceOperation.LIST,
        () -> {
          ResourcePage<T> page = operations.find(request, actor);
          return assemble(
              page.resources(),
              page.total(),
              null,
              request,
              totals,
              max,
              resources ->
                  renderer.renderAll(
                      resources,
                      description,
                      fieldsForRow,
                      resource -> Map.of(),
                      request.fieldSelections(),
                      request.includes(),
                      targetResolver(actor),
                      readContext(actor)),
              decorator);
        });
  }

  /** Lists scalar documents with per-ID field access, preserving the ordinary page metadata. */
  public ApiV2ListResult<Map<String, Object>> listReadDocuments(
      ScalarReader reader,
      ResourceRequest request,
      User actor,
      Function<Object, FieldSelection> fieldsForId,
      DocumentDecorator<Map<String, Object>> decorator,
      boolean totals,
      Integer max) {
    return invoke(
        ResourceOperation.LIST,
        () -> {
          var slice = reader.slice(request, actor);
          ResourcePage<Map<String, Object>> page =
              slice == null ? reader.read(request, actor) : null;
          var resources = slice == null ? page.resources() : slice.resources();
          Long total =
              slice == null
                  ? Long.valueOf(page.total())
                  : totals
                      ? Long.valueOf(operations.count(request.withCountMax(max), actor))
                      : null;
          return assemble(
              resources,
              total,
              slice == null ? null : slice.hasNext(),
              request,
              totals,
              max,
              rows -> renderScalars(rows, request, actor, fieldsForId),
              decorator);
        });
  }

  /** Reads one scalar document through the same filtered and constrained page query as a list. */
  public Map<String, Object> getReadDocument(
      ScalarReader reader,
      ID id,
      ResourceRequest request,
      User actor,
      DocumentDecorator<Map<String, Object>> decorator) {
    return readOne(
        () ->
            reader.read(request.forId(description.idField(), id), actor).resources().stream()
                .findFirst(),
        rows -> renderScalars(rows, request, actor, ignored -> request.fields()),
        decorator);
  }

  public ApiV2CountResult count(ResourceRequest request, User actor, Integer max) {
    return invoke(
        ResourceOperation.COUNT,
        () -> ApiV2CountResult.of(operations.count(request.withCountMax(max), actor), max));
  }

  public Map<String, Object> get(
      ID id, ResourceRequest request, User actor, DocumentDecorator<T> decorator) {
    return readOne(
        () -> findOne(id, request, actor),
        rows -> List.of(document(rows.get(0), request, actor)),
        decorator);
  }

  private Optional<T> findOne(ID id, ResourceRequest request, User actor) {
    if (request.serverConstraint() == null && request.filter() == null)
      return operations.findById(id, actor);
    return operations.find(request.forId(description.idField(), id), actor).resources().stream()
        .findFirst();
  }

  private <R> Map<String, Object> readOne(
      Supplier<Optional<R>> read,
      Function<List<R>, List<Map<String, Object>>> render,
      DocumentDecorator<R> decorator) {
    return invoke(
        ResourceOperation.READ,
        () -> {
          List<R> resources = List.of(read.get().orElseThrow(NotFoundException::new));
          List<Map<String, Object>> documents = render.apply(resources);
          decorator.decorate(resources, documents);
          return documents.get(0);
        });
  }

  private <R> ApiV2ListResult<Map<String, Object>> assemble(
      List<R> resources,
      Long total,
      Boolean hasNext,
      ResourceRequest request,
      boolean totals,
      Integer max,
      Function<List<R>, List<Map<String, Object>>> render,
      DocumentDecorator<R> decorator) {
    List<Map<String, Object>> documents = render.apply(resources);
    decorator.decorate(resources, documents);
    return hasNext == null
        ? ApiV2ListResult.counted(
            documents, total, request.page().number(), request.page().size(), totals, max)
        : ApiV2ListResult.slice(
            documents, request.page().number(), request.page().size(), hasNext, total, max);
  }

  private List<Map<String, Object>> renderScalars(
      List<Map<String, Object>> resources,
      ResourceRequest request,
      User actor,
      Function<Object, FieldSelection> fieldsForId) {
    return renderer.renderReadDocuments(
        resources,
        description,
        fieldsForId,
        request.fieldSelections(),
        request.includes(),
        targetResolver(actor),
        readContext(actor));
  }

  private Map<String, Object> document(T resource, ResourceRequest request, User actor) {
    return renderer.render(
        resource,
        description,
        request.fieldSelections(),
        request.includes(),
        targetResolver(actor),
        Map.of(),
        readContext(actor));
  }

  public CreateResult<Map<String, Object>> create(
      ParsedDocument document, ApiV2Caller caller, FieldSelection selection, String key) {
    return invoke(
        ResourceOperation.CREATE,
        () -> {
          CreateResult<T> result =
              operations.create(
                  document,
                  caller,
                  mutationPolicy.idempotency() == IdempotencyPolicy.CREATOR_SCOPED ? key : null);
          return new CreateResult<>(
              document(result.resource(), selection, caller.subject()), result.replayed());
        });
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
      FieldSelection selection,
      ResourceRequest scope) {
    return invoke(
        ResourceOperation.UPDATE,
        () ->
            mutations.execute(
                () -> {
                  requireScope(scope, caller);
                  return operations
                      .update(id, document, expectedVersion, caller)
                      .map(resource -> document(resource, selection, caller.subject()))
                      .orElseThrow(NotFoundException::new);
                }));
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
      ID id,
      ResourceDeleteOptions options,
      ApiV2Caller caller,
      FieldSelection selection,
      ResourceRequest scope) {
    return invoke(
        ResourceOperation.DELETE,
        () ->
            mutations.execute(
                () -> {
                  requireScope(scope, caller);
                  return operations
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
                      .orElseThrow(NotFoundException::new);
                }));
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

  private void requireScope(ResourceRequest scope, ApiV2Caller caller) {
    if (scope != null && operations.find(scope, caller.subject()).resources().isEmpty()) {
      throw new NotFoundException();
    }
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
