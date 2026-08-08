package com.researchspace.api.v2.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.UserManager;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserResourceOperationsTest {

  private final UserManager manager = mock(UserManager.class);
  private final UserResourceOperations operations = new UserResourceOperations(manager);
  private final User actor = mock(User.class);
  private final ParsedDocument document = mock(ParsedDocument.class);
  private final ResourceRequest request = mock(ResourceRequest.class);

  @Test
  void exposesOnlyReadOperations() {
    assertEquals(
        EnumSet.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ),
        operations.userApiV2Resource().exposedOperations());
  }

  @Test
  void writeOperationsFailClosedAsDefenceInDepth() {
    assertThrows(UnsupportedOperationException.class, () -> operations.create(document, actor));
    assertThrows(
        UnsupportedOperationException.class, () -> operations.createMany(List.of(document), actor));
    assertThrows(UnsupportedOperationException.class, () -> operations.update(1L, document, actor));
    assertThrows(
        UnsupportedOperationException.class, () -> operations.updateMany(request, document, actor));
    assertThrows(UnsupportedOperationException.class, () -> operations.delete(1L, actor));
    assertThrows(UnsupportedOperationException.class, () -> operations.deleteMany(request, actor));
    verifyNoInteractions(manager);
  }

  @Test
  void everyReadCarriesTheActorToTheManagerBoundary() {
    when(manager.getUserResource(1L, actor)).thenReturn(Optional.of(actor));

    operations.find(request, actor);
    operations.count(request, actor);
    operations.findById(1L, actor);

    verify(manager).getUsers(request, actor);
    verify(manager).countUsers(request, actor);
    verify(manager).getUserResource(1L, actor);
  }
}
