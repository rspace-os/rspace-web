package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.service.SnippetService;
import com.researchspace.model.User;
import com.researchspace.model.record.Snippet;
import com.researchspace.service.MessageSourceUtils;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectRetrievalFailureException;

@ExtendWith(MockitoExtension.class)
class SnippetApiControllerTest {

  @Mock private SnippetService snippetService;

  private MessageSourceUtils messageSourceUtils;

  @InjectMocks private SnippetApiController controller;

  private User user;

  @BeforeEach
  void setUp() {
    messageSourceUtils = Mockito.mock(MessageSourceUtils.class);
    controller.setMessageSource(messageSourceUtils);
    user = new User();
    user.setUsername("some-user");
  }

  @Test
  void whenSnippetFoundThenReturnsContent() {
    long id = 123L;
    String content = "some content";
    Snippet snip = new Snippet();
    snip.setId(id);
    snip.setContent(content);
    when(snippetService.getSnippet(id, user)).thenReturn(snip);

    String result = controller.getSnippetContentById(id, user);
    assertEquals(content, result);
  }

  @Test
  void whenSnippetNotFoundThenThrowsNotFound() {
    long id = 123L;
    when(snippetService.getSnippet(id, user))
        .thenThrow(new ObjectRetrievalFailureException("Snippet", id));
    mockNotFoundMessage();
    NotFoundException ex =
        assertThrows(NotFoundException.class, () -> controller.getSnippetContentById(id, user));
    assertEquals("not found", ex.getMessage());
  }

  @Test
  void whenSnippetAccessNotPermittedThenThrowsNotFound() {
    long id = 123L;
    when(snippetService.getSnippet(id, user)).thenThrow(new AuthorizationException(""));
    mockNotFoundMessage();
    NotFoundException ex =
        assertThrows(NotFoundException.class, () -> controller.getSnippetContentById(id, user));
    assertEquals("not found", ex.getMessage());
  }

  void mockNotFoundMessage() {
    when(messageSourceUtils.getResourceNotFoundMessage(Mockito.anyString(), Mockito.anyLong()))
        .thenReturn("not found");
  }
}
