package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axiope.userimport.IPostUserSignup;
import com.researchspace.auth.LoginHelper;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.googleauth.ExternalAuthTokenVerifier;
import com.researchspace.googleauth.ExternalProfile;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.ISignupHandlerPolicy;
import com.researchspace.service.RoleManager;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.CommunityTestContext;
import java.util.Collections;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;

@CommunityTestContext
public class ExternalOAuthControllerTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Mock RoleManager roleMgr;
  @Mock ExternalAuthTokenVerifier verifier;
  @Mock LoginHelper login;
  @Mock IPostUserSignup signup;
  @Mock UserManager userMgr;
  @Mock ISignupHandlerPolicy policy;
  @Mock UserEnablementUtils userEnablementUtils;

  @InjectMocks ExternalAuthController signupCtrller;
  String clientId = "client";
  String token = "token";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testExternalLoginUnverifiedTokenReturnsError() throws UserExistsException {
    when(verifier.verify(clientId, token)).thenReturn(Optional.empty());
    AjaxReturnObject<String> rc =
        signupCtrller.externalLogin(token, clientId, new MockHttpServletRequest());
    assertNotNull(rc.getError());
    assertNoLogin();
  }

  @Test
  public void testExternalLoginHappyCase() throws UserExistsException {
    ExternalProfile profile = createEXternalPRofile();
    when(verifier.verify(clientId, token)).thenReturn(Optional.of(profile));
    User u = TestFactory.createAnyUser("any");
    when(userMgr.getUserByEmail(profile.getEmail())).thenReturn(TransformerUtils.toList(u));
    AjaxReturnObject<String> rc =
        signupCtrller.externalLogin(token, clientId, new MockHttpServletRequest());
    assertEquals(WorkspaceController.ROOT_URL, rc.getData());
    assertLogin();
  }

  @Test(expected = IllegalStateException.class)
  public void testNonexistentEmail() {
    ExternalProfile profile = createEXternalPRofile();
    when(verifier.verify(clientId, token)).thenReturn(Optional.of(profile));
    when(userMgr.getUserByEmail(profile.getEmail())).thenReturn(Collections.emptyList());
    signupCtrller.externalLogin(token, clientId, new MockHttpServletRequest());
    assertNoLogin();
  }

  private ExternalProfile createEXternalPRofile() {
    ExternalProfile profile = new ExternalProfile();
    profile.setEmail("a@b.com");
    return profile;
  }

  private void assertNoLogin() {
    verifyNoInteractions(login);
    verifyNoInteractions(userEnablementUtils);
  }

  private void assertLogin() {
    verify(login, Mockito.times(1))
        .login(
            Mockito.any(User.class),
            Mockito.anyString(),
            Mockito.any(MockHttpServletRequest.class));
  }

  @Test
  public void testExternalSignupUnverifiedTokenReturnsError() throws UserExistsException {
    when(verifier.verify(clientId, token)).thenReturn(Optional.empty());
    AjaxReturnObject<String> rc =
        signupCtrller.externalSignup(token, clientId, new MockHttpServletRequest());
    assertNotNull(rc.getError());
    assertNoSignup();
  }

  private void assertNoSignup() throws UserExistsException {
    verifyNoInteractions(policy);
    verifyNoInteractions(userEnablementUtils);
  }

  @Test
  public void testExternalSignupHappyCase() throws UserExistsException {
    ExternalProfile profile = createEXternalPRofile();
    when(verifier.verify(clientId, token)).thenReturn(Optional.of(profile));
    when(signup.getRedirect(Mockito.any(User.class))).thenReturn("redirect:/workspace");
    when(policy.saveUser(Mockito.any(User.class), Mockito.any(MockHttpServletRequest.class)))
        .thenReturn(TestFactory.createAnyUser("any"));
    AjaxReturnObject<String> rc =
        signupCtrller.externalSignup(token, clientId, new MockHttpServletRequest());
    assertNotNull(rc.getData());
    verify(userEnablementUtils, times(1)).checkLicenseForUserInRole(anyInt(), any());
  }
}
