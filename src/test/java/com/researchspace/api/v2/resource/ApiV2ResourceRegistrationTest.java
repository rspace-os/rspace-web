package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.model.User;
import com.researchspace.model.collection.*;
import jakarta.ws.rs.NotFoundException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApiV2ResourceRegistrationTest {
  record Item(Long id) {}

  private static final FilterExpression READ =
      new FilterExpression.Comparison("id", Operator.GREATER_THAN, List.of(5L), false);
  private static final FilterExpression WRITE =
      new FilterExpression.Comparison("id", Operator.LESS_THAN, List.of(20L), false);
  private final User actor = new User("reader");
  private final ResourceOperations<Item, Long> operations =
      mock(ResourceOperations.class, CALLS_REAL_METHODS);
  private final ApiV2ResourceRegistration<?, ?> registration = registration();

  private ApiV2ResourceRegistration<?, ?> registration() {
    var read =
        AccessFunction.documented(
            "Read scope", Set.of(), context -> AccessResult.allowedWhere(READ));
    var write =
        AccessFunction.documented(
            "Write scope", Set.of(), context -> AccessResult.allowedWhere(WRITE));
    var description =
        new CollectionDescription<>(
            "items",
            Item.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id),
                Field.<Item, String>writable(
                    "name",
                    "name",
                    CollectionFieldTypes.text(),
                    ignored -> "old",
                    (item, value) -> {})),
            "id",
            List.of(new Sort("id", true)),
            new AccessPolicy(read, AccessFunction.anyone(), write, write, write));
    var spec = new ApiV2ResourceSpec<>(description, operations, Long::valueOf, "create", "update");
    return new ApiV2ResourceCatalog(List.of(spec)).find("items").orElseThrow();
  }

  @Test
  void listAndCountApplyReadScopeAndDeduplicateIt() {
    when(operations.find(any(), same(actor))).thenReturn(new ResourcePage<>(List.of(), 0));
    registration.list(ResourceRequest.unpaged(null), actor);
    registration.count(ResourceRequest.unpaged(null).restrict(READ), actor);
    var captor = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(operations).find(captor.capture(), same(actor));
    assertEquals(READ, captor.getValue().serverConstraint());
    verify(operations).count(captor.capture(), same(actor));
    assertEquals(READ, captor.getValue().serverConstraint());
  }

  @Test
  void singleWritesOutsideScopeReturnNotFound() throws Exception {
    when(operations.find(any(), same(actor))).thenReturn(new ResourcePage<>(List.of(), 0));
    var caller = ApiV2Caller.direct(actor);
    assertThrows(
        NotFoundException.class,
        () ->
            registration.update(
                "1", new ObjectMapper().createObjectNode().put("name", "new"), null, caller));
    assertThrows(
        NotFoundException.class,
        () -> registration.delete("1", new ResourceDeleteOptions(null, false), caller));
    verify(operations, never()).update(eq(1L), any(), any(Long.class), any());
    verify(operations, never()).delete(eq(1L), any(ResourceDeleteOptions.class), any());
  }

  @Test
  void bulkWritesCombineReadAndWriteScopes() {
    doReturn(List.of()).when(operations).updateMany(any(), any(), any());
    doReturn(List.of()).when(operations).deleteMany(any(), any());
    var request =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison("id", Operator.GREATER_THAN, List.of(0L), false));
    registration.updateMany(
        request,
        new ObjectMapper().createObjectNode().put("name", "new"),
        ApiV2Caller.direct(actor));
    registration.deleteMany(request, ApiV2Caller.direct(actor));
    var captor = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(operations).updateMany(captor.capture(), any(), any());
    assertEquals(
        new QueryConstraint.And(List.of(WRITE, READ)), captor.getValue().serverConstraint());
    assertEquals(request.filter(), captor.getValue().filter());
    verify(operations).deleteMany(captor.capture(), any());
    assertEquals(
        new QueryConstraint.And(List.of(WRITE, READ)), captor.getValue().serverConstraint());
  }
}
