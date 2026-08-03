package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ibm.icu.text.ListFormatter;
import com.researchspace.model.User;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.ListFormatUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.controller.SnippetController.SnippetResponse;
import java.security.Principal;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SnippetControllerTest extends SpringTransactionalTest {

  @Autowired private SnippetController snippetController;

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
    SnippetResponse response = snippetController.createSnippet("a", "b", 0L, principalTestUserStub);
    assertEquals("gallery.snippet.creation.ok", response.data().key());
    assertEquals(List.of("a"), response.data().arguments());

    // test invalid names
    String invalidName = "<img src=\"image.png\" onerror=\"alert('1');\">";
    response = snippetController.createSnippet(invalidName, "b", 0L, principalTestUserStub);
    assertNull(response.data());
    assertEquals("errors.invalidChars", response.errorMsg().key());
    assertEquals(
        List.of(
            ListFormatUtils.formatList(List.of("/", ">", "<"), ListFormatter.Type.OR),
            messages.getMessage("label.nameLowercase")),
        response.errorMsg().arguments());
  }
}
