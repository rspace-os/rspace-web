package com.researchspace.webapp.controller;

import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.SignupCaptchaVerifier;
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
  public void ssoSignupAllowedREquiresGeneralUseRSignup() {
    when(properties.isUserSignup()).thenReturn(Boolean.FALSE);
    signupCtrller.setAcceptedSignupDomains("@xyz, abc, def");
    assertFalse(signupCtrller.isSsoSignupAllowed("any.somwhere@abc"));
    assertFalse(signupCtrller.isSsoSignupAllowed("any.somwhere@xyz"));
    assertFalse(signupCtrller.isSsoSignupAllowed("any.somwhere@def"));
  }
}
