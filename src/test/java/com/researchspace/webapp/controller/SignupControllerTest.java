package com.researchspace.webapp.controller;

import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.SignupCaptchaVerifier;
import com.researchspace.webapp.filter.SAMLRemoteUserPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

public class SignupControllerTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Mock IPropertyHolder properties;
  private @Mock UserValidator userValidator;
  private @Mock SignupCaptchaVerifier captchaVerifier;
  MockHttpServletRequest mockRequest;
  @InjectMocks SignupController signupCtrller;

  @Before
  public void setUp() throws Exception {
    mockRequest = new MockHttpServletRequest();
  }

  @After
  public void tearDown() throws Exception {}

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
  public void ssoSignupAllowedRequiresGeneralUseRSignup() {
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
    assertEquals("Schødt-Żębski", signupCtrller.getLastnameFromRemote(mockIso8859Request));

    // check if still works for utf8 chars
    MockHttpServletRequest mockUtf8Request = new MockHttpServletRequest();
    mockUtf8Request.setAttribute("Shib-givenName", "Mæck");
    mockUtf8Request.setAttribute("Shib-surName", "Schødt-Żębski");
    assertEquals("Mæck", signupCtrller.getFirstNameFromRemote(mockUtf8Request));
    assertEquals("Schødt-Żębski", signupCtrller.getLastnameFromRemote(mockUtf8Request));

    // confirm not conversion when encoding disabled in deployment property
    signupCtrller.setDeploymentSsoRecodeNamesToUft8(false);
    assertEquals("MÃ¦ck", signupCtrller.getFirstNameFromRemote(mockIso8859Request));
    assertEquals("SchÃ¸dt-Å»Ä\u0099bski", signupCtrller.getLastnameFromRemote(mockIso8859Request));
  }

}
