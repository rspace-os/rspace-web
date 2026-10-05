package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.EmailContentGenerator;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.ModelAndView;

@ExtendWith(MockitoExtension.class)
public class LoginPasswordResetByEmailHandlerTest {

  private static final String EMAIL = "reset@example.com";

  private @Mock UserManager userManager;
  private @Mock UserValidator userValidator;
  private @Mock EmailContentGenerator emailContentGenerator;
  private @Mock EmailBroadcast emailer;
  private @Mock EmailContent emailContent;
  private @InjectMocks LoginPasswordResetByEmailHandler handler;

  private MockHttpServletRequest request;
  private PasswordResetCommand cmd;
  private BindingResult errors;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    cmd = new PasswordResetCommand();
    cmd.setPassword("newpassword1");
    cmd.setConfirmPassword("newpassword1");
    errors = new BeanPropertyBindingResult(cmd, "passwordResetCommand");
  }

  @Test
  void usedTokenIsRefusedBeforeThePasswordIsChanged() {
    TokenBasedVerification token = freshToken();
    token.setResetCompleted(true);
    cmd.setToken(token.getToken());
    when(userManager.getUserVerificationToken(token.getToken())).thenReturn(token);

    assertThrows(IllegalStateException.class, () -> handler.submitResetPage(cmd, errors, request));
    verify(userManager, never()).applyLoginPasswordChange(anyString(), anyString());
  }

  @Test
  void expiredTokenIsRefusedBeforeThePasswordIsChanged() {
    long timeout = TokenBasedVerificationType.PASSWORD_CHANGE.getTimeout();
    TokenBasedVerification token =
        new TokenBasedVerification(
            EMAIL,
            new Date(System.currentTimeMillis() - timeout - 1000),
            TokenBasedVerificationType.PASSWORD_CHANGE);
    cmd.setToken(token.getToken());
    when(userManager.getUserVerificationToken(token.getToken())).thenReturn(token);

    assertThrows(IllegalStateException.class, () -> handler.submitResetPage(cmd, errors, request));
    verify(userManager, never()).applyLoginPasswordChange(anyString(), anyString());
  }

  @Test
  void unknownTokenIsRefused() {
    cmd.setToken("unknown");
    when(userManager.getUserVerificationToken("unknown")).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> handler.submitResetPage(cmd, errors, request));
    verify(userManager, never()).applyLoginPasswordChange(anyString(), anyString());
  }

  @Test
  void validTokenStillCompletesTheReset() throws Exception {
    TokenBasedVerification token = freshToken();
    cmd.setToken(token.getToken());
    when(userManager.getUserVerificationToken(token.getToken())).thenReturn(token);
    when(userManager.getUsernameByToken(token.getToken())).thenReturn(Optional.of("someone"));
    when(userManager.applyLoginPasswordChange(cmd.getPassword(), token.getToken()))
        .thenReturn(token);
    when(emailContentGenerator.render(anyString(), anyString(), any())).thenReturn(emailContent);

    ModelAndView mav = handler.submitResetPage(cmd, errors, request);

    assertEquals("passwordReset/resetPasswordComplete", mav.getViewName());
  }

  private static TokenBasedVerification freshToken() {
    return new TokenBasedVerification(
        EMAIL, new Date(), TokenBasedVerificationType.PASSWORD_CHANGE);
  }
}
