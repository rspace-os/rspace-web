package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.license.InactiveLicenseTestService;
import com.researchspace.model.Role;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.User;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.filter.MockRemoteUserPolicy;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import com.researchspace.webapp.filter.SSOShiroFormAuthFilterExt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.web.servlet.MvcResult;

public class SignupControllerMVCIT extends MVCTestBase {

  private static final String SIGNUP_PASSWORD_RESET_REQUEST = "/signup/passwordResetRequest";
  private static final String SIGNUP_PASSWORD_RESET_REPLY = "/signup/passwordResetReply";
  private static final String TOKEN_PARAM = "token";
  static final String CONFIRM_PASSWORD_PARAM = "confirmPassword";
  static final String PASSWORD_PARAM = "password";
  static final String VALID_PWD = "pwd12345";

  private @Autowired SignupController signupController;
  private @Autowired RemoteUserRetrievalPolicy remoteUserPolicy;
  private @Autowired UserEnablementUtils userEnablementUtils;

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    userEnablementUtils.setLicenseService(new NoCheckLicenseService());
    propertyHolder.setUserSignup("true");
    propertyHolder.setStandalone("true");
    propertyHolder.setPicreateGroupOnSignupEnabled(false);
  }

  @Before
  public void setup() throws Exception {
    super.setUp();
    RSpaceTestUtils.logout(); // make sure any previously logged in user is logged out
  }

  @Test
  public void getSignupFormTest() throws Exception {
    this.mockMvc
        .perform(get(SignupController.SIGNUP_URL))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("user"));
  }

  @Test
  public void postUsernameReminderRequest() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("test1"));

    this.mockMvc
        .perform(post("/signup/usernameReminderRequest").param("email", user.getEmail()))
        .andExpect(view().name("usernameReminder/remindUsernameEmailSent"));
  }

  @Test
  public void postPasswordResetRequestThrottling() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("test1"));
    // make valid attempts
    for (int i = 0; i < UsernameReminderByEmailHandler.MAX_REMINDERS_PER_EMAIL_PER_HOUR; i++) {
      this.mockMvc
          .perform(post(SIGNUP_PASSWORD_RESET_REQUEST).param("email", user.getEmail()))
          .andExpect(view().name("passwordReset/resetPasswordRequestSent"));
    }
    // next one fails
    MvcResult result =
        this.mockMvc
            .perform(post(SIGNUP_PASSWORD_RESET_REQUEST).param("email", user.getEmail()))
            .andReturn();
    assertNotNull(result.getResolvedException());
  }

  @Test
  public void postPasswordResetRequest() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("test1"));

    this.mockMvc
        .perform(post(SIGNUP_PASSWORD_RESET_REQUEST).param("email", user.getEmail()))
        .andExpect(view().name("passwordReset/resetPasswordRequestSent"));

    // we want the same message returned to user even if email doesn't exist, so
    // that people can't find out who has an account by this approach.
    this.mockMvc
        .perform(post(SIGNUP_PASSWORD_RESET_REQUEST).param("email", "unknownemail@email.com"))
        .andExpect(view().name("passwordReset/resetPasswordRequestSent"));

    // now we'll get the password reset form from a valid link
    TokenBasedVerification token = getTokenForUser(user);
    this.mockMvc
        .perform(get(SIGNUP_PASSWORD_RESET_REPLY).param(TOKEN_PARAM, token.getToken()))
        .andExpect(model().attributeExists("passwordResetCommand"))
        .andExpect(view().name("passwordReset/resetPassword"));

    // check that this doesn'twork from an invalid link and gets redirected to fail
    this.mockMvc
        .perform(get(SIGNUP_PASSWORD_RESET_REPLY).param(TOKEN_PARAM, "wrongToken"))
        .andExpect(view().name("passwordReset/resetPasswordFail"));

    // now let's submit a new password change:
    this.mockMvc
        .perform(
            post(SIGNUP_PASSWORD_RESET_REPLY)
                .param(PASSWORD_PARAM, "")
                .param(CONFIRM_PASSWORD_PARAM, "")
                .param(TOKEN_PARAM, token.getToken()))
        .andExpect(model().hasErrors());

    this.mockMvc
        .perform(
            post(SIGNUP_PASSWORD_RESET_REPLY)
                .param(PASSWORD_PARAM, "newpassword")
                .param(CONFIRM_PASSWORD_PARAM, "newpassword2")
                .param(TOKEN_PARAM, token.getToken()))
        .andExpect(model().hasErrors());
    this.mockMvc
        .perform(
            post(SIGNUP_PASSWORD_RESET_REPLY)
                .param(PASSWORD_PARAM, "password")
                .param(CONFIRM_PASSWORD_PARAM, "password")
                .param(TOKEN_PARAM, token.getToken()))
        .andExpect(model().hasErrors());

    this.mockMvc
        .perform(
            post(SIGNUP_PASSWORD_RESET_REPLY)
                .param(PASSWORD_PARAM, "newpasswordOK")
                .param(CONFIRM_PASSWORD_PARAM, "newpasswordOK")
                .param(TOKEN_PARAM, token.getToken()))
        .andExpect(model().hasNoErrors())
        .andExpect(view().name("passwordReset/resetPasswordComplete"));

    token = getTokenForUser(user);
    assertTrue(token.isResetCompleted());
    // check that this doesn'twork from an invalid link and gets redirected to fail
    this.mockMvc
        .perform(get(SIGNUP_PASSWORD_RESET_REPLY).param(TOKEN_PARAM, "wrongToken"))
        .andExpect(view().name("passwordReset/resetPasswordFail"));

    // check a second attempt to use the token fails - it is a one-time token.
    this.mockMvc
        .perform(get(SIGNUP_PASSWORD_RESET_REPLY).param(TOKEN_PARAM, token.getToken()))
        .andExpect(view().name("passwordReset/resetPasswordFail"));

    RSpaceTestUtils.logoutCurrUserAndLoginAs(user.getUsername(), "newpasswordOK");
    RSpaceTestUtils.logout();
  }

  private TokenBasedVerification getTokenForUser(User user) {
    openTransaction();
    TokenBasedVerification upc =
        (TokenBasedVerification)
            sessionFactory
                .getCurrentSession()
                .createQuery("from TokenBasedVerification where email=:email")
                .setParameter("email", user.getEmail())
                .uniqueResult();
    commitTransaction();
    return upc;
  }

  @Test
  public void postSignupFormTest() throws Exception {

    final String VALID_USER_NAME = "XXXXXX";
    this.mockMvc
        .perform(post(SignupController.SIGNUP_URL))
        .
        // empty user, fails with username message first
        andExpect(model().attributeHasFieldErrors("user", "username"));

    this.mockMvc
        .perform(post(SignupController.SIGNUP_URL).param("username", VALID_USER_NAME))
        .andExpect(model().attributeHasFieldErrors("user", "email"));

    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", VALID_USER_NAME)
                .param("email", "xxx"))
        .andExpect(model().attributeHasFieldErrors("user", PASSWORD_PARAM));

    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", VALID_USER_NAME)
                .param("email", "xxx")
                .param(PASSWORD_PARAM, VALID_PWD)
                .param(CONFIRM_PASSWORD_PARAM, VALID_PWD))
        .andExpect(model().attributeHasFieldErrors("user", "firstName"));
    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", VALID_USER_NAME)
                .param("email", "xxx")
                .param(PASSWORD_PARAM, VALID_PWD)
                .param(CONFIRM_PASSWORD_PARAM, VALID_PWD)
                .param("firstName", "pwd"))
        .andExpect(model().attributeHasFieldErrors("user", "lastName"));

    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", VALID_USER_NAME)
                .param("email", "xxx")
                .param("firstName", "pwd")
                .param("lastName", "pwd")
                .param(PASSWORD_PARAM, VALID_PWD)
                .param(CONFIRM_PASSWORD_PARAM, "otherpwd"))
        // passwords don't match
        .andExpect(model().attributeHasFieldErrors("user", PASSWORD_PARAM));

    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", VALID_USER_NAME)
                .param("email", "xxx")
                .param(PASSWORD_PARAM, VALID_PWD)
                .param(CONFIRM_PASSWORD_PARAM, VALID_PWD)
                .param("firstName", "pwd")
                .param("lastName", "pwd"))
        .andExpect(model().attributeHasNoErrors("user"));

    // now repeat with same username, should fail due to duplicate username
    mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", VALID_USER_NAME)
                .param("email", "xxx")
                .param(PASSWORD_PARAM, VALID_PWD)
                .param(CONFIRM_PASSWORD_PARAM, VALID_PWD)
                .param("firstName", "pwd")
                .param("lastName", "pwd"))
        .andExpect(model().attributeHasFieldErrors("user", "username"));

    // no underscores
    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", "INVLID__")
                .param("email", "xxx")
                .param(PASSWORD_PARAM, VALID_PWD)
                .param(CONFIRM_PASSWORD_PARAM, VALID_PWD)
                .param("firstName", "pwd")
                .param("lastName", "pwd"))
        .andExpect(model().attributeHasFieldErrors("user", "username"));

    // if role is set, will ignore and force User role -RSPAC-473
    String uname = CoreTestUtils.getRandomName(10);
    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", uname)
                .param("email", uname + "@xxx.com")
                .param(PASSWORD_PARAM, VALID_PWD)
                .param(CONFIRM_PASSWORD_PARAM, VALID_PWD)
                .param("firstName", "pwd")
                .param("lastName", "pwd")
                .param("role", "ROLE_ADMIN"))
        .andReturn();
    assertFalse(userMgr.getUserByUsername(uname).hasRole(Role.ADMIN_ROLE));
  }

  @Test
  public void testExceptionThrownIfNoLicense() throws Exception {
    String uname = getRandomAlphabeticString("user");
    userEnablementUtils.setLicenseService(new InactiveLicenseTestService());
    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", uname)
                .param("email", "xxx")
                .param(PASSWORD_PARAM, VALID_PWD)
                .param(CONFIRM_PASSWORD_PARAM, VALID_PWD)
                .param("firstName", "pwd")
                .param("lastName", "pwd"))
        .andExpect(model().attributeExists(ControllerExceptionHandler.EXCEPTION_MESSAGE_ATTR_NAME))
        .andReturn();
  }

  @Test
  public void testSignupFormForSSOUsers() throws Exception {

    // saving to re-set at the end of the test
    String originalRemoteUser = remoteUserPolicy.getRemoteUser(null);
    MockRemoteUserPolicy mockPolicy = (MockRemoteUserPolicy) remoteUserPolicy;
    String ssoUsername = "testSSO";
    mockPolicy.setUsername(ssoUsername);

    // switch to SSO mode
    propertyHolder.setStandalone("false");

    // all logins accepted, so user should get signup page with username filled in
    MvcResult result = this.mockMvc.perform(get("/signup")).andExpect(status().isOk()).andReturn();
    Object user = result.getModelAndView().getModel().get("user");
    assertNotNull(user);
    assertEquals(ssoUsername, ((User) user).getUsername());

    // let's only accept one domain
    signupController.setAcceptedSignupDomains("@ox.ac.uk");

    // non-matching user should redirected to sso info
    this.mockMvc
        .perform(get("/signup"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl(SSOShiroFormAuthFilterExt.SSOINFO_URL));

    // matching user is allowed to sign up
    String oxSsoUsername = "testSSO@ox.ac.uk";
    mockPolicy.setUsername(oxSsoUsername);

    MvcResult result2 = this.mockMvc.perform(get("/signup")).andExpect(status().isOk()).andReturn();
    Object user2 = result2.getModelAndView().getModel().get("user");
    assertNotNull(user2);
    assertEquals(oxSsoUsername, ((User) user2).getUsername());

    // let's sign up. sso user doesn't need to provide password fields
    User signedUser = null;
    try {
      signedUser = userMgr.getUserByUsername(oxSsoUsername);
    } catch (ObjectRetrievalFailureException e) {
      // expected
    }
    assertNull(signedUser);

    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", oxSsoUsername)
                .param("email", "xxx@junit")
                .param("firstName", "fn")
                .param("lastName", "ln"))
        .andExpect(model().attributeHasNoErrors("user"));

    signedUser = userMgr.getUserByUsername(oxSsoUsername);
    assertNotNull(signedUser);
    assertEquals(oxSsoUsername, signedUser.getUsername());
    assertEquals("xxx@junit", signedUser.getEmail());
    assertEquals("fn", signedUser.getFirstName());
    assertEquals("ln", signedUser.getLastName());

    // cleanup
    mockPolicy.setUsername(originalRemoteUser);
  }
}
