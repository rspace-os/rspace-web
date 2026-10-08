package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.password.NewPasswordEncodeGate;
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
import org.mockito.Spy;
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
  private @Spy NewPasswordEncodeGate encodeGate = new NewPasswordEncodeGate(1);
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
  void tokenClaimedByAConcurrentSubmitReturnsTheFailViewWithoutAnEmail() throws Exception {
    TokenBasedVerification token = freshToken();
    cmd.setToken(token.getToken());
    when(userManager.getUserVerificationToken(token.getToken())).thenReturn(token);
    when(userManager.getUsernameByToken(token.getToken())).thenReturn(Optional.of("someone"));
    when(userManager.applyLoginPasswordChange(cmd.getPassword(), token.getToken()))
        .thenReturn(null);

    assertEquals(FAIL_VIEW, handler.submitResetPage(cmd, errors, request).getViewName());
    verify(emailer, never()).sendEmail(any(), any(), any());
    assertTrue(encodeGate.tryAcquire());
  }

  @Test
  void resetWithNoFreeEncodePermitIsRefusedWithTheTokenUntouched() throws Exception {
    TokenBasedVerification token = freshToken();
    stubCompletableReset(cmd, token);
    assertTrue(encodeGate.tryAcquire());

    ModelAndView mav = handler.submitResetPage(cmd, errors, request);

    assertEquals(RESET_VIEW, mav.getViewName());
    assertTrue(errors.hasGlobalErrors());
    assertEquals("errors.passwordReset.rateLimited", errors.getGlobalError().getCode());
    assertFalse(token.isResetCompleted());
    verify(userManager, never()).applyLoginPasswordChange(anyString(), anyString());

    encodeGate.release();
    BindingResult retryErrors = new BeanPropertyBindingResult(cmd, "passwordResetCommand");
    assertEquals(COMPLETE_VIEW, handler.submitResetPage(cmd, retryErrors, request).getViewName());
  }

  @Test
  void failedPasswordChangeReleasesTheEncodePermit() {
    TokenBasedVerification token = freshToken();
    cmd.setToken(token.getToken());
    when(userManager.getUserVerificationToken(token.getToken())).thenReturn(token);
    when(userManager.getUsernameByToken(token.getToken())).thenReturn(Optional.of("someone"));
    when(userManager.applyLoginPasswordChange(cmd.getPassword(), token.getToken()))
        .thenThrow(new IllegalStateException("db down"));

    assertThrows(IllegalStateException.class, () -> handler.submitResetPage(cmd, errors, request));

    assertTrue(encodeGate.tryAcquire());
  }

  @Test
  void invalidPasswordsDoNotTakeAnEncodePermit() throws Exception {
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

    assertTrue(encodeGate.tryAcquire());
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
