package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.controller.ApiControllerAdvice;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.model.User;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.ChildAddPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.LocalizedIllegalAddChildOperation;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class FolderMessagesTest {

  private final MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());

  @Test
  void selfAdditionUsesLocalizedIllegalAddChildExceptionAndApiError() {
    Folder folder = TestFactory.createAFolder("self", TestFactory.createAnyUser("owner"));

    LocalizedIllegalAddChildOperation exception =
        assertThrows(
            LocalizedIllegalAddChildOperation.class,
            () ->
                folder.addChild(
                    folder,
                    ChildAddPolicy.DEFAULT,
                    folder.getOwner(),
                    ACLPropagationPolicy.DEFAULT_POLICY));

    assertInstanceOf(IllegalAddChildOperation.class, exception);
    assertEquals(
        "Cannot add this object [" + folder + "] as a child of itself.",
        messages.getExceptionMessage(exception));
    assertEquals(0, folder.getChildren().size());

    ResponseEntity<Object> response =
        apiAdvice().handleLocalizedIllegalStateException(exception, null);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    ApiError error = assertInstanceOf(ApiError.class, response.getBody());
    assertEquals(messages.getExceptionMessage(exception), error.getMessage());
    assertEquals(ApiErrorCodes.GENERAL_ERROR.getCode(), error.getInternalCode());
  }

  @Test
  void rejectedPolicyUsesItsPolicyAndRecordNames() {
    User owner = TestFactory.createAnyUser("owner");
    Folder parent = TestFactory.createAFolder("parent", owner);
    Folder child = TestFactory.createAFolder("child", owner);

    LocalizedIllegalAddChildOperation exception =
        assertThrows(
            LocalizedIllegalAddChildOperation.class,
            () ->
                parent.addChild(
                    child, ChildAddPolicy.NULL_OBJECT, owner, ACLPropagationPolicy.DEFAULT_POLICY));

    assertEquals(
        "ChildAddPolicy ["
            + ChildAddPolicy.NULL_OBJECT
            + "] does not permit child addition of ["
            + child
            + "] to  folder["
            + parent
            + "]",
        messages.getExceptionMessage(exception));
    assertNull(child.getParent());
    assertEquals(0, parent.getChildren().size());
  }

  @Test
  void cycleRejectionKeepsExistingParentRelationshipOnly() {
    User owner = TestFactory.createAnyUser("owner");
    Folder parent = TestFactory.createAFolder("parent", owner);
    Folder child = TestFactory.createAFolder("child", owner);
    parent.addChild(child, owner);

    LocalizedIllegalAddChildOperation exception =
        assertThrows(LocalizedIllegalAddChildOperation.class, () -> child.addChild(parent, owner));

    assertEquals(
        "Addition of [parent] to  folder[child]  would produce a cycle in the folder tree,"
            + " which currently is not permitted.",
        messages.getExceptionMessage(exception));
    assertEquals(parent, child.getParent());
    assertNull(parent.getParent());
    assertEquals(1, parent.getChildren().size());
  }

  @Test
  void blankCreationNamesResolveToExistingEnglishDefaults() {
    assertEquals("Untitled Folder", messages.getMessage("folder.create.defaultName"));
    assertEquals("Untitled Notebook", messages.getMessage("notebook.create.defaultName"));
    assertEquals("Untitled document", messages.getMessage("document.create.defaultName"));
  }

  private TestApiControllerAdvice apiAdvice() {
    return new TestApiControllerAdvice(messages);
  }

  private static class TestApiControllerAdvice extends ApiControllerAdvice {
    TestApiControllerAdvice(MessageSourceUtils messages) {
      this.messages = messages;
    }
  }
}
