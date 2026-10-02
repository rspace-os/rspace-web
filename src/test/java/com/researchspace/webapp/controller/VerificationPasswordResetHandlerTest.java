package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class VerificationPasswordResetHandlerTest {

  private @Mock IVerificationPasswordValidator verificationPasswordValidator;
  private @Mock IReauthenticator reauthenticator;
  private @Mock UserManager userManager;
  private @Mock MessageSourceUtils messages;
  private @InjectMocks VerificationPasswordResetHandler handler;

  @Test
  void currentPasswordIsCheckedWithLockoutAndRefusalChangesNothing() {
    User user = TestFactory.createAnyUser("sso");
    when(reauthenticator.reauthenticateWithVerificationPassword(user, "current")).thenReturn(false);
    when(messages.getMessage("passwordChange.errors.incorrectCurrentPassword"))
        .thenReturn("incorrect");

    assertEquals(
        "incorrect",
        handler.changePassword(
            "current", "newPassword1", "newPassword1", new MockHttpServletRequest(), user));
    verify(verificationPasswordValidator, never()).authenticateVerificationPassword(any(), any());
    verify(userManager, never()).saveUser(any());
  }
}
