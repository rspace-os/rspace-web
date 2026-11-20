package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.model.User;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.filter.MockRemoteUserPolicy;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A signup submission that carries the User id or version is refused without changing any existing
 * account, in every signup mode. Other User fields the form does not expose are ignored.
 */
public class SignupIdBindingMVCIT {

  private static final String PWD = "pwd12345";

  abstract class SignupModeTestBase extends MVCTestBase {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired IMutablePropertyHolder propertyHolder;

    @BeforeEach
    void logout() {
      RSpaceTestUtils.logout();
    }

    @AfterEach
    void rollbackOpenTransaction() throws Exception {
      tearDown();
    }

    Map<String, Object> userRow(Long id) {
      return jdbcTemplate.queryForMap(
          "select username, email, password, first_name, last_name, version, account_enabled,"
              + " account_locked, tempAccount from User where id = ?",
          id);
    }

    MockHttpServletRequestBuilder signupForm(String newUsername) {
      return post(SignupController.SIGNUP_URL)
          .param("username", newUsername)
          .param("email", newUsername + "@somewhere.com")
          .param("password", PWD)
          .param("confirmPassword", PWD)
          .param("firstName", "new")
          .param("lastName", "user")
          .param("affiliation", "Uni of RSpace");
    }

    MockHttpServletRequestBuilder signupNaming(User existing, String newUsername) {
      return signupForm(newUsername)
          .param("id", existing.getId().toString())
          .param("version", currentVersion(existing))
          .param("usernameAlias", newUsername + "Alias");
    }

    String currentVersion(User existing) {
      return userRow(existing.getId()).get("version").toString();
    }

    void assertSignupRejected(ResultActions result, User existing, Map<String, Object> before)
        throws Exception {
      result.andExpect(view().name("signup")).andExpect(model().hasErrors());
      assertEquals(before, userRow(existing.getId()));
    }
  }

  @Nested
  class Standalone extends SignupModeTestBase {

    @Test
    void standaloneSignupWithForcedUserFieldsIsRejected() throws Exception {
      User existing = createAndSaveUser(getRandomAlphabeticString("existing"));
      Map<String, Object> before = userRow(existing.getId());
      String newUsername = getRandomAlphabeticString("newcomer");

      ResultActions result = mockMvc.perform(signupNaming(existing, newUsername));

      assertSignupRejected(result, existing, before);
      assertFalse(userMgr.userExists(newUsername));
    }

    @Test
    void signupWithOnlyTheIdIsRejected() throws Exception {
      User existing = createAndSaveUser(getRandomAlphabeticString("existing"));
      Map<String, Object> before = userRow(existing.getId());
      String newUsername = getRandomAlphabeticString("newcomer");

      ResultActions result =
          mockMvc.perform(signupForm(newUsername).param("id", existing.getId().toString()));

      assertSignupRejected(result, existing, before);
      assertFalse(userMgr.userExists(newUsername));
    }

    @Test
    void signupWithOnlyTheVersionIsRejected() throws Exception {
      User existing = createAndSaveUser(getRandomAlphabeticString("existing"));
      Map<String, Object> before = userRow(existing.getId());
      String newUsername = getRandomAlphabeticString("newcomer");

      ResultActions result =
          mockMvc.perform(signupForm(newUsername).param("version", currentVersion(existing)));

      assertSignupRejected(result, existing, before);
      assertFalse(userMgr.userExists(newUsername));
    }

    @Test
    void aNonFormFieldWithoutIdIsDroppedAndSignupProceeds() throws Exception {
      String newUsername = getRandomAlphabeticString("newcomer");

      mockMvc
          .perform(signupForm(newUsername).param("usernameAlias", newUsername + "Alias"))
          .andExpect(status().is3xxRedirection());

      assertNull(userMgr.getUserByUsername(newUsername).getUsernameAlias());
    }
  }

  @Nested
  class Sso extends SignupModeTestBase {

    @Autowired RemoteUserRetrievalPolicy remoteUserPolicy;

    @AfterEach
    void restoreStandalone() {
      propertyHolder.setStandalone("true");
    }

    @Test
    void ssoSignupWithForcedUserFieldsIsRejected() throws Exception {
      MockRemoteUserPolicy mockPolicy = (MockRemoteUserPolicy) remoteUserPolicy;
      String originalRemoteUser = mockPolicy.getRemoteUser(null);
      String ssoUsername = getRandomAlphabeticString("sso");
      mockPolicy.setUsername(ssoUsername);
      propertyHolder.setStandalone("false");
      try {
        User existing = createAndSaveUser(getRandomAlphabeticString("existing"));
        Map<String, Object> before = userRow(existing.getId());

        ResultActions result = mockMvc.perform(signupNaming(existing, ssoUsername));

        assertSignupRejected(result, existing, before);
        assertFalse(userMgr.userExists(ssoUsername));
      } finally {
        mockPolicy.setUsername(originalRemoteUser);
      }
    }
  }

  @Nested
  @CommunityTestContext
  class Community extends SignupModeTestBase {

    @Test
    void communitySignupWithForcedUserFieldsIsRejected() throws Exception {
      assertTrue(propertyHolder.isCloud());
      User existing = createAndSaveUser(getRandomAlphabeticString("existing"));
      Map<String, Object> before = userRow(existing.getId());
      String newUsername = getRandomAlphabeticString("newcomer");

      ResultActions result = mockMvc.perform(signupNaming(existing, newUsername));

      assertSignupRejected(result, existing, before);
      assertFalse(userMgr.userExists(newUsername));
      List<String> tokens =
          jdbcTemplate.queryForList(
              "select token from TokenBasedVerification where email = ?",
              String.class,
              newUsername + "@somewhere.com");
      assertEquals(0, tokens.size());
    }
  }

  @Nested
  @TestPropertySource(properties = "authorised.signup=true")
  class AuthorisedSignup extends SignupModeTestBase {

    @Test
    void authorisedSignupWithForcedUserFieldsIsRejected() throws Exception {
      User existing = createAndSaveUser(getRandomAlphabeticString("existing"));
      Map<String, Object> before = userRow(existing.getId());
      String newUsername = getRandomAlphabeticString("newcomer");

      ResultActions result = mockMvc.perform(signupNaming(existing, newUsername));

      assertSignupRejected(result, existing, before);
      assertFalse(userMgr.userExists(newUsername));
    }
  }
}
