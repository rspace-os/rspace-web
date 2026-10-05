package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
  private static final String FAIL_VIEW = "passwordReset/resetPasswordFail";
  private static final String RESET_VIEW = "passwordReset/resetPassword";
  private static final String COMPLETE_VIEW = "passwordReset/resetPasswordComplete";

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
    cmd = newCommand();
    errors = new BeanPropertyBindingResult(cmd, "passwordResetCommand");
    handler.setMaxResetsPerFiveSeconds(10);
    handler.initResetRateLimiter();
  }

  @Test
  void usedTokenReturnsTheFailViewBeforeThePasswordIsChanged() throws Exception {
    TokenBasedVerification token = freshToken();
    token.setResetCompleted(true);
    cmd.setToken(token.getToken());
    when(userManager.getUserVerificationToken(token.getToken())).thenReturn(token);

    assertEquals(FAIL_VIEW, handler.submitResetPage(cmd, errors, request).getViewName());
    verify(userManager, never()).applyLoginPasswordChange(anyString(), anyString());
  }

  @Test
  void expiredTokenReturnsTheFailViewBeforeThePasswordIsChanged() throws Exception {
    long timeout = TokenBasedVerificationType.PASSWORD_CHANGE.getTimeout();
    TokenBasedVerification token =
        new TokenBasedVerification(
            EMAIL,
            new Date(System.currentTimeMillis() - timeout - 1000),
            TokenBasedVerificationType.PASSWORD_CHANGE);
    cmd.setToken(token.getToken());
    when(userManager.getUserVerificationToken(token.getToken())).thenReturn(token);

    assertEquals(FAIL_VIEW, handler.submitResetPage(cmd, errors, request).getViewName());
    verify(userManager, never()).applyLoginPasswordChange(anyString(), anyString());
  }

  @Test
  void unknownTokenReturnsTheFailView() throws Exception {
    cmd.setToken("unknown");
    when(userManager.getUserVerificationToken("unknown")).thenReturn(null);

    assertEquals(FAIL_VIEW, handler.submitResetPage(cmd, errors, request).getViewName());
    verify(userManager, never()).applyLoginPasswordChange(anyString(), anyString());
  }

  @Test
  void validTokenStillCompletesTheReset() throws Exception {
    TokenBasedVerification token = freshToken();
    stubCompletableReset(cmd, token);

    ModelAndView mav = handler.submitResetPage(cmd, errors, request);

    assertEquals(COMPLETE_VIEW, mav.getViewName());
  }

  @Test
  void resetBeyondTheRateLimitIsRefusedWithTheTokenUntouched() throws Exception {
    handler.setMaxResetsPerFiveSeconds(1);
    handler.initResetRateLimiter();
    TokenBasedVerification first = freshToken();
    stubCompletableReset(cmd, first);
    assertEquals(COMPLETE_VIEW, handler.submitResetPage(cmd, errors, request).getViewName());

    PasswordResetCommand secondCmd = newCommand();
    TokenBasedVerification second = freshToken();
    secondCmd.setToken(second.getToken());
    when(userManager.getUserVerificationToken(second.getToken())).thenReturn(second);
    when(userManager.getUsernameByToken(second.getToken())).thenReturn(Optional.of("other"));
    BindingResult secondErrors = new BeanPropertyBindingResult(secondCmd, "passwordResetCommand");

    ModelAndView mav = handler.submitResetPage(secondCmd, secondErrors, request);

    assertEquals(RESET_VIEW, mav.getViewName());
    assertTrue(secondErrors.hasGlobalErrors());
    assertEquals("errors.passwordReset.rateLimited", secondErrors.getGlobalError().getCode());
    assertFalse(second.isResetCompleted());
    verify(userManager, times(1)).applyLoginPasswordChange(anyString(), anyString());
    verify(userManager, never()).applyLoginPasswordChange(anyString(), eq(second.getToken()));
  }

  @Test
  void invalidPasswordsDoNotConsumeARateLimitSlot() throws Exception {
    handler.setMaxResetsPerFiveSeconds(1);
    handler.initResetRateLimiter();
    TokenBasedVerification invalidToken = freshToken();
    cmd.setToken(invalidToken.getToken());
    when(userManager.getUserVerificationToken(invalidToken.getToken())).thenReturn(invalidToken);
    when(userManager.getUsernameByToken(invalidToken.getToken()))
        .thenReturn(Optional.of("someone"));
    doAnswer(
            invocation -> {
              errors.rejectValue("password", "errors.required");
              return null;
            })
        .when(userValidator)
        .validatePasswords(cmd.getPassword(), cmd.getConfirmPassword(), "someone", errors);
    assertEquals(RESET_VIEW, handler.submitResetPage(cmd, errors, request).getViewName());

    PasswordResetCommand validCmd = newCommand();
    stubCompletableReset(validCmd, freshToken());
    BindingResult validErrors = new BeanPropertyBindingResult(validCmd, "passwordResetCommand");

    assertEquals(
        COMPLETE_VIEW, handler.submitResetPage(validCmd, validErrors, request).getViewName());
    assertFalse(validErrors.hasErrors());
  }

  private void stubCompletableReset(PasswordResetCommand command, TokenBasedVerification token) {
    command.setToken(token.getToken());
    when(userManager.getUserVerificationToken(token.getToken())).thenReturn(token);
    when(userManager.getUsernameByToken(token.getToken())).thenReturn(Optional.of("someone"));
    when(userManager.applyLoginPasswordChange(command.getPassword(), token.getToken()))
        .thenReturn(token);
    when(emailContentGenerator.render(anyString(), anyString(), any())).thenReturn(emailContent);
  }

  private static PasswordResetCommand newCommand() {
    PasswordResetCommand command = new PasswordResetCommand();
    command.setPassword("newpassword1");
    command.setConfirmPassword("newpassword1");
    return command;
  }

  private static TokenBasedVerification freshToken() {
    return new TokenBasedVerification(
        EMAIL, new Date(), TokenBasedVerificationType.PASSWORD_CHANGE);
  }
}
