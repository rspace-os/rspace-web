package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.testutils.SpringTransactionalTest;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SnippetControllerTest extends SpringTransactionalTest {

  @Autowired private SnippetController snippetController;

  private User user;
  private Principal principalTestUserStub = null;

  @BeforeEach
  public void setUp() throws IllegalAddChildOperation {
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithExampleContent(user);
    assertTrue(user.isContentInitialized());
    principalTestUserStub = new MockPrincipal(user.getUsername());
  }

  @Test
  public void testCreateNewSimpleSnippet() throws Exception {
    AjaxReturnObject<String> response =
        snippetController.createSnippet("a", "b", 0L, principalTestUserStub);
    String createResultMsg = response.getData();
    assertEquals(createResultMsg, messages.getMessage("snippet.creation.ok", new String[] {"a"}));

    // test invalid names
    String invalidName = "<img src=\"image.png\" onerror=\"alert('1');\">";
    response = snippetController.createSnippet(invalidName, "b", 0L, principalTestUserStub);
    assertNull(response.getData());
    assertEquals(
        response.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy(""),
        messages.getMessage("errors.invalidchars", new String[] {"/,> or <", "name"}));
  }
}
