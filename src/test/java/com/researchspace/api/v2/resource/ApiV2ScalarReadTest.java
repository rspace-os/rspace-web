package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.collection.Sort;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApiV2ScalarReadTest {

  private static final FilterExpression ROW_ACCESS =
      new FilterExpression.Comparison("id", Operator.GREATER_THAN, List.of(0L), false);
  private static final CollectionDescription<Item> ITEMS =
      new CollectionDescription<>(
          "items",
          Item.class,
          List.of(
              Field.<Item, Long>readOnly(
                  "id", "id", CollectionFieldTypes.longNumber(), ignored -> unread()),
              Field.<Item, String>readOnly(
                  "name", "name", CollectionFieldTypes.text(), ignored -> unread()),
              Field.<Item, String>readOnly(
                      "secret", "secret", CollectionFieldTypes.text(), ignored -> unread())
                  .readableBy(
                      AccessFunction.when(
                          context -> context.targetId() == null || context.targets(7L)))),
          "id",
          List.of(new Sort("id", true)),
          AccessPolicy.readOnly(
              AccessFunction.documented(
                  "Test scalar row access.",
                  Set.of(),
                  ignored -> AccessResult.allowedWhere(ROW_ACCESS))));

  private final ResourceOperations<Item, Long> operations = mock(ResourceOperations.class);
  private final User actor = new User("reader");

  @Test
  void listUsesOnlyScalarDocumentsAndBatchesRuntimeValuesFromTheAuthorizedPageIds() {
    RuntimeCollectionFields<Item> provider =
        mock(RuntimeCollectionFields.class, CALLS_REAL_METHODS);
    when(provider.namespace()).thenReturn("customFields");
    when(provider.projectsThroughRelationship()).thenReturn(true);
    ResolvedRuntimeField runtime =
        new ResolvedRuntimeField(
            new RuntimeFieldDefinition(
                "SF1", "customFields.SF1", "Custom", RuntimeFieldValueType.TEXT, "", "", List.of()),
            new RuntimeFieldBinding(Item.class, "id", "name", Map.of()));
    QueryConstraint server =
        new FilterExpression.Comparison("id", Operator.LESS_THAN, List.of(100L), false);
    ResourceRequest request =
        new ResourceRequest(
                null,
                List.of(new Sort("name", false)),
                new ResourceRequest.Page(2, 2),
                ResourceFieldSelections.root(FieldSelection.all()),
                IncludeTree.empty(),
                new RuntimeFieldSelection(
                    Map.of(runtime.selector(), runtime), Set.of(runtime.selector())))
            .restrict(server);
    when(operations.usesReadDocuments()).thenReturn(true);
    when(operations.findReadDocuments(any(), eq(actor)))
        .thenReturn(
            new ResourcePage<>(
                List.of(
                    Map.of("id", 7L, "name", "one", "secret", "allowed"),
                    Map.of("id", 8L, "name", "two", "secret", "hidden")),
                5));
    when(provider.valuesForIds(List.of(7L, 8L), Set.of("SF1"), actor))
        .thenReturn(Map.of(7L, Map.of("SF1", "selected")));

    var result = register(List.of(provider)).list(request, actor);

    assertEquals(
        List.of(
            Map.of(
                "id",
                7L,
                "name",
                "one",
                "secret",
                "allowed",
                "customFields",
                Map.of("SF1", "selected")),
            Map.of("id", 8L, "name", "two", "customFields", Map.of())),
        result.docs());
    assertEquals(5L, result.totalDocs());
    verify(provider, never()).valuesForAuthorizedIds(any(), any(), any());
    verify(provider).valuesForIds(List.of(7L, 8L), Set.of("SF1"), actor);
    verify(provider, never()).values(any(), any(), any());
    verify(operations, never()).find(any(), any());
    ArgumentCaptor<ResourceRequest> captured = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(operations).findReadDocuments(captured.capture(), eq(actor));
    assertEquals(
        new QueryConstraint.And(List.of(server, ROW_ACCESS)),
        captured.getValue().serverConstraint());
    assertEquals(request.page(), captured.getValue().page());
    assertEquals(request.sort(), captured.getValue().sort());
    assertEquals(request.runtime(), captured.getValue().runtime());
  }

  @Test
  void singleReadRetainsBothRequestAndPolicyConstraintsAndUsesTheTargetId() {
    FilterExpression filter =
        new FilterExpression.Comparison("name", Operator.EQUAL, List.of("visible"), false);
    RuntimeCollectionFields<Item> provider =
        mock(RuntimeCollectionFields.class, CALLS_REAL_METHODS);
    when(provider.namespace()).thenReturn("customFields");
    when(provider.projectsThroughRelationship()).thenReturn(true);
    ResolvedRuntimeField runtime =
        new ResolvedRuntimeField(
            new RuntimeFieldDefinition(
                "SF1", "customFields.SF1", "Custom", RuntimeFieldValueType.TEXT, "", "", List.of()),
            new RuntimeFieldBinding(Item.class, "id", "name", Map.of()));
    RuntimeFieldSelection runtimeSelection =
        new RuntimeFieldSelection(Map.of(runtime.selector(), runtime), Set.of(runtime.selector()));
    QueryConstraint server =
        new FilterExpression.Comparison("id", Operator.LESS_THAN, List.of(20L), false);
    ResourceRequest request = ResourceRequest.unpaged(filter, runtimeSelection).restrict(server);
    when(operations.usesReadDocuments()).thenReturn(true);
    when(operations.findReadDocuments(any(), eq(actor)))
        .thenReturn(
            new ResourcePage<>(
                List.of(Map.of("id", 8L, "name", "visible", "secret", "hidden")), 1));
    when(provider.valuesForIds(List.of(8L), Set.of("SF1"), actor))
        .thenReturn(Map.of(8L, Map.of("SF1", "selected")));

    Map<String, Object> result = register(List.of(provider)).get("8", request, actor);

    assertEquals(
        Map.of("id", 8L, "name", "visible", "customFields", Map.of("SF1", "selected")), result);
    assertFalse(result.containsKey("secret"));
    ArgumentCaptor<ResourceRequest> captured = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(operations).findReadDocuments(captured.capture(), eq(actor));
    assertEquals(
        new FilterExpression.And(
            List.of(
                filter, new FilterExpression.Comparison("id", Operator.EQUAL, List.of(8L), false))),
        captured.getValue().filter());
    assertEquals(
        new QueryConstraint.And(List.of(server, ROW_ACCESS)),
        captured.getValue().serverConstraint());
    assertEquals(new ResourceRequest.Page(1, 1), captured.getValue().page());
    assertEquals(request.runtime(), captured.getValue().runtime());
    verify(provider).valuesForIds(List.of(8L), Set.of("SF1"), actor);
    verify(operations, never()).findById(any(), any());
    verify(operations, never()).find(any(), any());
  }

  @Test
  void excludedSingleReadReturnsNotFoundWithoutEntityFallback() {
    when(operations.usesReadDocuments()).thenReturn(true);
    when(operations.findReadDocuments(any(), eq(actor)))
        .thenReturn(new ResourcePage<>(List.of(), 0));

    assertThrows(
        NotFoundException.class,
        () -> register(List.of()).get("8", ResourceRequest.unpaged(null), actor));

    verify(operations, never()).findById(any(), any());
  }

  private ApiV2ResourceRegistration<Item, Long> register(
      List<RuntimeCollectionFields<Item>> providers) {
    return new ApiV2ResourceSpec<>(
            ITEMS,
            operations,
            Long::valueOf,
            "errors.api.v2.invalidRequest",
            "errors.api.v2.invalidRequest",
            Set.of(ResourceOperation.LIST, ResourceOperation.READ, ResourceOperation.COUNT),
            Map.of(),
            Map.of(),
            CollectionMutationLimits.DEFAULT,
            providers)
        .bind(new ResourceRegistry(List.of(ITEMS)), ApiV2RelationshipResolver.unavailable());
  }

  private static <V> V unread() {
    throw new AssertionError("Scalar reads must not invoke entity field readers");
  }

  private record Item(Long id, String name, String secret) {}
}
