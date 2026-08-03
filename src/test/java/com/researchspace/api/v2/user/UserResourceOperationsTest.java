package com.researchspace.api.v2.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.UserManager;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class UserResourceOperationsTest {

  private final UserManager manager = mock(UserManager.class);
  private final UserResourceOperations operations = new UserResourceOperations(manager);
  private final User actor = mock(User.class);
  private final ParsedDocument document = mock(ParsedDocument.class);
  private final ResourceRequest request = mock(ResourceRequest.class);

  @Test
  void exposesTheStandardOperationSet() {
    assertEquals(
        EnumSet.allOf(ResourceOperation.class), operations.userApiV2Resource().exposedOperations());
  }

  @Test
  void writeOperationsDoNotPersistAnything() {
    assertSame(actor, operations.create(document, actor));
    assertTrue(operations.update(1L, document, actor).isEmpty());
    assertTrue(operations.updateMany(request, document, actor).isEmpty());
    assertTrue(operations.delete(1L, actor).isEmpty());
    assertTrue(operations.deleteMany(request, actor).isEmpty());
    verifyNoInteractions(manager);
  }
}
