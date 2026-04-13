package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.security.Principal;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

public class SnippetControllerTest extends SpringTransactionalTest {

  @Autowired private SnippetController snippetController;

  @Autowired private RecordManager recordManager;

  @Autowired private UserManager userManager;

  private MockHttpServletResponse response;

  private User user;
  private Principal principalTestUserStub = null;

  @Before
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
