package com.researchspace.webapp.controller;

import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axiope.userimport.IPostUserSignup;
import com.researchspace.Constants;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.auth.password.NewPasswordEncodeGate;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.ISignupHandlerPolicy;
import com.researchspace.service.RoleManager;
import com.researchspace.service.SignupCaptchaVerifier;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserExistsException;
import com.researchspace.webapp.filter.SAMLRemoteUserPolicy;
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

@ExtendWith(MockitoExtension.class)
public class SignupControllerTest {

  @Mock IPropertyHolder properties;
  private @Mock UserValidator userValidator;
  private @Mock SignupCaptchaVerifier captchaVerifier;
  private @Mock RoleManager roleManager;
  private @Mock UserEnablementUtils userEnablementUtils;
  private @Mock ISignupHandlerPolicy manualSignupPolicy;
  private @Mock IPostUserSignup postSignup;
  private @Spy NewPasswordEncodeGate encodeGate = new NewPasswordEncodeGate(1);
  MockHttpServletRequest mockRequest;
  @InjectMocks SignupController signupCtrller;

  @BeforeEach
  public void setUp() throws Exception {
    mockRequest = new MockHttpServletRequest();
  }

  @Test
  public void signupWithNoFreeEncodePermitIsRefusedWithoutSaving() throws UserExistsException {
    when(roleManager.getRole(Constants.USER_ROLE)).thenReturn(new Role(Constants.USER_ROLE));
    User user = createAnyUser("refused1");
    assertTrue(encodeGate.tryAcquire());

    BindingResult errors = new BeanPropertyBindingResult(user, "user");
    assertEquals("signup", signupCtrller.onSubmit(user, errors, mockRequest));
    assertTrue(errors.hasGlobalErrors());
    assertEquals("errors.signup.rateLimited", errors.getGlobalError().getCode());
    verify(manualSignupPolicy, never()).saveUser(eq(user), any());

    encodeGate.release();
    when(manualSignupPolicy.saveUser(eq(user), any())).thenReturn(user);
    when(postSignup.getRedirect(user)).thenReturn("redirect:workspace");
    BindingResult retryErrors = new BeanPropertyBindingResult(user, "user");
    assertEquals("redirect:workspace", signupCtrller.onSubmit(user, retryErrors, mockRequest));
  }

  @Test
  public void invalidSignupDoesNotTakeAnEncodePermit() throws UserExistsException {
    User invalid = createAnyUser("invalid1");
    BindingResult invalidErrors = new BeanPropertyBindingResult(invalid, "user");
    doAnswer(
            invocation -> {
              invalidErrors.rejectValue("email", "errors.required");
              return null;
            })
        .when(userValidator)
        .validate(invalid, invalidErrors);
    assertEquals("signup", signupCtrller.onSubmit(invalid, invalidErrors, mockRequest));

    assertTrue(encodeGate.tryAcquire());
  }

  @Test
  public void existingUserReleasesTheEncodePermit() throws UserExistsException {
    when(roleManager.getRole(Constants.USER_ROLE)).thenReturn(new Role(Constants.USER_ROLE));
    User user = createAnyUser("exists1");
    when(manualSignupPolicy.saveUser(eq(user), any())).thenThrow(new UserExistsException("exists"));
    BindingResult errors = new BeanPropertyBindingResult(user, "user");

    assertEquals("signup", signupCtrller.onSubmit(user, errors, mockRequest));

    assertTrue(encodeGate.tryAcquire());
  }

  @Test
  public void busyPostSignupLoginRedirectsToLoginWithTheAccountKept() throws UserExistsException {
    when(roleManager.getRole(Constants.USER_ROLE)).thenReturn(new Role(Constants.USER_ROLE));
    User user = createAnyUser("busy1");
    when(manualSignupPolicy.saveUser(eq(user), any())).thenReturn(user);
    doThrow(new LoginVerificationBusyException("busy"))
        .when(postSignup)
        .postUserCreate(eq(user), any(), any());
    BindingResult errors = new BeanPropertyBindingResult(user, "user");

    String view = signupCtrller.onSubmit(user, errors, mockRequest);

    assertEquals(SignupController.ACCOUNT_CREATED_LOGIN_BUSY_VIEW, view);
    verify(manualSignupPolicy).saveUser(eq(user), any());
    verify(postSignup, never()).getRedirect(user);
  }

  @Test
  public void recaptchaRejectsEarly() {
    when(properties.getSignupCaptchaEnabled()).thenReturn("true");
    when(captchaVerifier.verifyCaptchaFromRequest(mockRequest)).thenReturn("notOK");
    User user = createAnyUser("any123");
    BindingResult errors = new BeanPropertyBindingResult(user, "errors");
    signupCtrller.onSubmit(user, errors, mockRequest);
    verify(userValidator, never()).validate(user, errors);
  }

  @Test
  public void ssoSignupAllowed() {
    setupMocks();
    signupCtrller.setAcceptedSignupDomains(null);
    assertTrue(signupCtrller.isSsoSignupAllowed("any"));

    signupCtrller.setAcceptedSignupDomains("");
    assertTrue(signupCtrller.isSsoSignupAllowed("any.somwhere"));

    signupCtrller.setAcceptedSignupDomains("@xyz");
    assertTrue(signupCtrller.isSsoSignupAllowed("any.somwhere@xyz"));

    signupCtrller.setAcceptedSignupDomains("@xyz, abc, def");
    assertTrue(signupCtrller.isSsoSignupAllowed("any.somwhere@abc"));
    assertTrue(signupCtrller.isSsoSignupAllowed("any.somwhere@xyz"));
    assertTrue(signupCtrller.isSsoSignupAllowed("any.somwhere@def"));

    assertFalse(signupCtrller.isSsoSignupAllowed(""));
    assertFalse(signupCtrller.isSsoSignupAllowed("any"));
    assertFalse(signupCtrller.isSsoSignupAllowed("any.somwhere@mno"));
  }

  private void setupMocks() {
    when(properties.isUserSignup()).thenReturn(Boolean.TRUE);
  }

  @Test
  public void ssoSignupAllowedRequiresGeneralUserSignup() {
    when(properties.isUserSignup()).thenReturn(Boolean.FALSE);
    signupCtrller.setAcceptedSignupDomains("@xyz, abc, def");
    assertFalse(signupCtrller.isSsoSignupAllowed("any.somwhere@abc"));
    assertFalse(signupCtrller.isSsoSignupAllowed("any.somwhere@xyz"));
    assertFalse(signupCtrller.isSsoSignupAllowed("any.somwhere@def"));
  }

  @Test
  public void testIncomingSAMLFirstNameLastNameUtfRecoding() {

    // enable encoding
    signupCtrller.setRemoteUserPolicy(new SAMLRemoteUserPolicy());
    signupCtrller.setDeploymentSsoRecodeNamesToUft8(true);

    // check iso-encoded chars
    MockHttpServletRequest mockIso8859Request = new MockHttpServletRequest();
    mockIso8859Request.setAttribute("Shib-givenName", "MÃ¦ck");
    mockIso8859Request.setAttribute("Shib-surName", "SchÃ¸dt-Å»Ä\u0099bski");
    assertEquals("Mæck", signupCtrller.getFirstNameFromRemote(mockIso8859Request));
    assertEquals("Schødt-Żębski", signupCtrller.getLastNameFromRemote(mockIso8859Request));

    // check if still works for utf8 chars
    MockHttpServletRequest mockUtf8Request = new MockHttpServletRequest();
    mockUtf8Request.setAttribute("Shib-givenName", "Mæck");
    mockUtf8Request.setAttribute("Shib-surName", "Schødt-Żębski");
    assertEquals("Mæck", signupCtrller.getFirstNameFromRemote(mockUtf8Request));
    assertEquals("Schødt-Żębski", signupCtrller.getLastNameFromRemote(mockUtf8Request));

    // confirm no conversion if encoding is disabled in deployment property
    signupCtrller.setDeploymentSsoRecodeNamesToUft8(false);
    assertEquals("MÃ¦ck", signupCtrller.getFirstNameFromRemote(mockIso8859Request));
    assertEquals("SchÃ¸dt-Å»Ä\u0099bski", signupCtrller.getLastNameFromRemote(mockIso8859Request));
  }

  @Test
  public void testIncomingSsoUsernameSuffixReplacement_RSDEV_669() {

    // suffix deployment props not configured
    User user =
        signupCtrller.setUsernameAndAliasFromRemoteUserInSsoMode(new User(), "test#EXT#something");
    assertEquals("test#EXT#something", user.getUsername());
    assertEquals(null, user.getUsernameAlias());

    // configure deployment props
    signupCtrller.setDeploymentSsoSignupUsernameSuffixToReplace("#EXT#toReplace");
    signupCtrller.setDeploymentSsoSignupUsernameSuffixReplacement("#EXT#replacement");

    // incoming username not matching suffix replacement prop
    user = signupCtrller.setUsernameAndAliasFromRemoteUserInSsoMode(new User(), "test#EXT#other");
    assertEquals("test#EXT#other", user.getUsername());
    assertEquals(null, user.getUsernameAlias());

    // incoming username containing toReplace string, but not as suffix
    user =
        signupCtrller.setUsernameAndAliasFromRemoteUserInSsoMode(
            new User(), "test#EXT#toReplace#EXT#other");
    assertEquals("test#EXT#toReplace#EXT#other", user.getUsername());
    assertEquals(null, user.getUsernameAlias());

    // incoming username matching suffix replacement prop
    user =
        signupCtrller.setUsernameAndAliasFromRemoteUserInSsoMode(new User(), "test#EXT#toReplace");
    assertEquals("test#EXT#replacement", user.getUsername());
    assertEquals("test#EXT#toReplace", user.getUsernameAlias());

    // chained suffix also works fine
    user =
        signupCtrller.setUsernameAndAliasFromRemoteUserInSsoMode(
            new User(), "test#EXT#toReplace#EXT#toReplace");
    assertEquals("test#EXT#toReplace#EXT#replacement", user.getUsername());
    assertEquals("test#EXT#toReplace#EXT#toReplace", user.getUsernameAlias());
  }
}
