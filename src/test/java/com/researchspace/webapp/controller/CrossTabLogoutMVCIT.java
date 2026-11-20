package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.Constants;
import com.researchspace.auth.ApiAwareWebSecurityManager;
import com.researchspace.model.User;
import com.researchspace.session.UserSessionTracker;
import jakarta.servlet.http.Cookie;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.verification.VerificationMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@TestPropertySource(properties = "deployment.sso.logout.url=/public/ssologout?custom=true")
class CrossTabLogoutMVCIT extends MVCTestBase {

  private static final String USER_PASSWORD = "TestPassw0rd!";
  private static final String SSO_LOGOUT_URL = "/public/ssologout?custom=true";

  @Autowired
  @Qualifier("securityManagerTest")
  private RealmSecurityManager testSecurityManager;

  @BeforeEach
  void useWebSessions() throws Exception {
    var securityManager = new ApiAwareWebSecurityManager();
    securityManager.setRealms(testSecurityManager.getRealms());
    var shiro = new ShiroFilterFactoryBean();
    shiro.setSecurityManager(securityManager);
    shiro.setLoginUrl("/login");
    shiro.setSuccessUrl("/workspace");
    shiro.setFilterChainDefinitions(
        "/adminLogin/** = anon\n/api/** = noSessionCreation\n/** = authc");
    wac.getServletContext().setAttribute(UserSessionTracker.USERS_KEY, activeUsers);
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(shiro.getObject()).build();
  }

  @Test
  void oldTokenIsRejectedAfterLogoutAndAfterAnotherUserSignsIn() throws Exception {
    User firstUser = createAndSaveUser(getRandomName(10), Constants.USER_ROLE, USER_PASSWORD);
    User secondUser = createAndSaveUser(getRandomName(10), Constants.USER_ROLE, USER_PASSWORD);
    initUsers(firstUser, secondUser);
    var browser = new BrowserSession();
    browser.login(firstUser.getUsername(), USER_PASSWORD);
    String oldToken = browser.uiToken();
    browser.useToken(oldToken).andExpect(status().isOk());

    browser
        .perform(get("/logout"))
        .andExpect(view().name("logout"))
        .andExpect(model().attribute("logoutMode", "standalone"));
    assertTrue(browser.session.isInvalid());
    browser.useToken(oldToken).andExpect(status().isUnauthorized());

    browser.login(secondUser.getUsername(), USER_PASSWORD);
    browser.useToken(browser.uiToken()).andExpect(status().isOk());
    browser.useToken(oldToken).andExpect(status().isUnauthorized());
  }

  @Test
  void operatedUsersTokenIsRejectedAfterRelease() throws Exception {
    User operatedUser = createAndSaveUser(getRandomName(10), Constants.USER_ROLE, USER_PASSWORD);
    initUser(operatedUser);
    var browser = new BrowserSession();
    browser.login(SYS_ADMIN_UNAME, SYS_ADMIN_PWD);
    browser
        .perform(
            post("/system/ajax/runAs")
                .param("sysadminPassword", SYS_ADMIN_PWD)
                .param(
                    "runAsUsername",
                    operatedUser.getUsername() + "<" + operatedUser.getEmail() + ">")
                .param("incognito", "true"))
        .andExpect(model().attribute("completed", true));
    String operatedUsersToken = browser.uiToken();
    browser.useToken(operatedUsersToken).andExpect(status().isOk());

    browser
        .perform(get("/logout/runAsRelease"))
        .andExpect(status().is3xxRedirection())
        .andExpect(view().name("redirect:/workspace"));
    browser.useToken(operatedUsersToken).andExpect(status().isUnauthorized());
  }

  @Test
  void ssoLogoutRendersConfiguredDestinationWhenSignedIn() throws Exception {
    User user = createAndSaveUser(getRandomName(10), Constants.USER_ROLE, USER_PASSWORD);
    initUser(user);
    var browser = new BrowserSession();
    browser.login(user.getUsername(), USER_PASSWORD);
    expectSsoLogoutView(browser, times(1));
    assertTrue(browser.session.isInvalid());
  }

  @Test
  void ssoLogoutRendersConfiguredDestinationWithoutAPrincipal() throws Exception {
    expectSsoLogoutView(new BrowserSession(), never());
  }

  private void expectSsoLogoutView(BrowserSession browser, VerificationMode logoutCalls)
      throws Exception {
    var adminLogin = wac.getBean(AdminLoginController.class);
    var original = (LogoutController) ReflectionTestUtils.getField(adminLogin, "logoutController");
    var recordedLogout = spy(original);
    ReflectionTestUtils.setField(adminLogin, "logoutController", recordedLogout);
    try {
      browser
          .perform(get("/adminLogin/logoutFromSso"))
          .andExpect(status().isOk())
          .andExpect(view().name("logout"))
          .andExpect(model().attribute("logoutMode", "sso"))
          .andExpect(model().attribute("redirectLocation", SSO_LOGOUT_URL));
      verify(recordedLogout, logoutCalls).logout(any(), any());
    } finally {
      ReflectionTestUtils.setField(adminLogin, "logoutController", original);
    }
  }

  private class BrowserSession {
    private Cookie sessionCookie;
    private MockHttpSession session;

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
      // MockMvc does not resolve container sessions from cookies on its own.
      if (sessionCookie != null) {
        request.cookie(sessionCookie);
        request.with(
            mockRequest -> {
              mockRequest.setRequestedSessionId(sessionCookie.getValue());
              mockRequest.setRequestedSessionIdFromCookie(true);
              mockRequest.setRequestedSessionIdValid(!session.isInvalid());
              return mockRequest;
            });
        if (!session.isInvalid()) {
          request.session(session);
        }
      }
      return mockMvc.perform(request);
    }

    private void login(String username, String password) throws Exception {
      MvcResult login =
          perform(post("/login").param("username", username).param("password", password))
              .andExpect(status().is3xxRedirection())
              .andReturn();
      session = (MockHttpSession) login.getRequest().getSession(false);
      assertNotNull(session);
      sessionCookie = new Cookie("JSESSIONID", session.getId());
    }

    private String uiToken() throws Exception {
      MvcResult result =
          perform(get("/userform/ajax/inventoryOauthToken")).andExpect(status().isOk()).andReturn();
      return getFromJsonAjaxReturnObject(result, String.class);
    }

    private ResultActions useToken(String token) throws Exception {
      return perform(get("/api/v1/userDetails/whoami").header("Authorization", "Bearer " + token));
    }
  }
}
