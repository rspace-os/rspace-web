package com.researchspace.webapp.controller.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.dtos.CreateCloudGroupValidator;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.cloud.CloudGroupManager;
import com.researchspace.service.cloud.CommunityUserManager;
import com.researchspace.session.SessionAttributeUtils;
import java.security.Principal;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.ModelAndView;

@RunWith(MockitoJUnitRunner.class)
public class CommunityControllerTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();
  @InjectMocks RSCommunityController controller;
  @Mock UserManager userMgr;
  @Mock CommunityUserManager cloudUserMgr;
  @Mock Principal principal;
  @Mock CloudGroupManager cloudGroupManager;
  @Mock CreateCloudGroupValidator validator;
  StaticMessageSource msges = new StaticMessageSource();

  User user;

  @Before
  public void setUp() throws Exception {
    controller.setMessageSource(new MessageSourceUtils(msges));
    user = TestFactory.createAnyUser("any");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetEmailChangeVerificationPageHappyCase() {
    String email = "x@y.com";
    TokenBasedVerification token =
        new TokenBasedVerification(email, new Date(), TokenBasedVerificationType.EMAIL_CHANGE);
    token.setUser(user);
    when(userMgr.getUserVerificationToken(token.getToken())).thenReturn(token);
    principalReturnsUserName();
    // msges.addMessage(code, locale, msg);
    ModelAndView mav =
        controller.getEmailChangeVerificationPage(
            token.getToken(), new ExtendedModelMap(), principal);
    assertEquals(token, mav.getModel().get(RSCommunityController.EMAIL_CHANGE_TOKEN_ATT_NAME));
  }

  private void principalReturnsUserName() {
    when(principal.getName()).thenReturn(user.getUsername());
  }

  @Test
  public void testPostEmailChangeConfirmationUpdatesSessionUser() {
    principalReturnsUserName();
    when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
    when(userMgr.getUserByUsername(user.getUsername(), true)).thenReturn(user);
    when(cloudUserMgr.emailChangeConfirmed("TOKEN", user)).thenReturn(true);
    MockHttpSession session = new MockHttpSession();
    controller.postEmailChangeConfirmation("TOKEN", session, principal);
    assertNotNull(session.getAttribute(SessionAttributeUtils.USER_INFO));
  }

  @Test
  public void testPostEmailChangeConfirmationDoesntUpdatesSessionUserIfFails() {
    principalReturnsUserName();
    when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
    when(cloudUserMgr.emailChangeConfirmed("TOKEN", user)).thenReturn(false);
    MockHttpSession session = new MockHttpSession();
    controller.postEmailChangeConfirmation("TOKEN", session, principal);
    assertNull(session.getAttribute(SessionAttributeUtils.USER_INFO));
  }

  @Test
  public void validEmail() {
    assertTrue(controller.isEmailValid("bartlomiej.marzec@med.uni-goettingen.de"));
    assertTrue(controller.isEmailValid("bartlomiej.marzec@a.com"));
    assertFalse(controller.isEmailValid("bar\\tlomiej.marzec@a.com"));
    assertFalse(controller.isEmailValid("bar tlomiej.marzec@a.com"));
    assertFalse(controller.isEmailValid("bar tlomiej.marzec@'"));
  }
}
