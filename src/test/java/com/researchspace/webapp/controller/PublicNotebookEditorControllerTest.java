package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.UserManager;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
class PublicNotebookEditorControllerTest {

  private @Mock UserManager userManager;
  private @Mock RecordSharingManager recordSharingManager;
  private @InjectMocks PublicNotebookEditorController controller;

  @AfterEach
  void unbindSubject() {
    ThreadContext.unbindSubject();
  }

  @Test
  void unknownLinkIsNotFoundWithoutSigningInTheGuest() {
    Subject subject = mock(Subject.class);
    ThreadContext.bind(subject);

    assertThrows(
        PublicLinkNotFoundException.class,
        () ->
            controller.openNotebook(
                "made-up-link",
                null,
                new ExtendedModelMap(),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()));
    verify(subject, never()).login(any(AuthenticationToken.class));
  }
}
