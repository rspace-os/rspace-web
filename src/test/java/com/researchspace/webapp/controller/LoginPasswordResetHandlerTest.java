package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.ReauthenticationResult;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class LoginPasswordResetHandlerTest {

  @Mock IReauthenticator reauthenticator;
  @Mock MessageSourceUtils messages;
  @Mock UserValidator validator;
  @Mock UserManager userManager;
  @InjectMocks LoginPasswordResetHandler handler;

  @Test
  void busyPasswordCheckSaysBusyAndChangesNothing() {
    User user = TestFactory.createAnyUser("any");
    when(reauthenticator.reauthenticate(user, "current")).thenReturn(ReauthenticationResult.BUSY);
    when(messages.getMessage("errors.reauthentication.busy")).thenReturn("busy");

    String answer =
        handler.changePassword(
            "current", "newPass1", "newPass1", new MockHttpServletRequest(), user);

    assertEquals("busy", answer);
    verify(userManager, never()).saveUser(any());
  }
}
