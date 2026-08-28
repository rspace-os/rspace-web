package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.SignupControllerMVCIT.CONFIRM_PASSWORD_PARAM;
import static com.researchspace.webapp.controller.SignupControllerMVCIT.PASSWORD_PARAM;
import static com.researchspace.webapp.controller.SignupControllerMVCIT.VALID_PWD;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.jdbc.JdbcTestUtils.countRowsInTable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.researchspace.model.User;
import com.researchspace.testutils.ProdProfileTestConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/** Runs production-profile signup and content initialisation procedure. */
@ProdProfileTestConfiguration
@EnabledIfSystemProperty(named = "nightly", matches = "(|true)")
public class ProdProfileSignupControllerMVCIT extends MVCTestBase {

  private @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @Disabled
  // TODO This test fails as it use ProdContentInitializerManager, but does not initialise content
  // as the forms created were those from DevContentInitMgr. The problem is mixing different user
  // initialization mechanisms within the same test run.
  public void groupCreatedIfPiGroupCreationEnabled() throws Exception {
    String username = randomAlphabetic(8);
    propertyHolder.setPicreateGroupOnSignupEnabled(true);
    long initialGrpCount = countRowsInTable(jdbcTemplate, "rsGroup");
    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", username)
                .param("email", username + "@xx.com")
                .param(PASSWORD_PARAM, VALID_PWD)
                .param(CONFIRM_PASSWORD_PARAM, VALID_PWD)
                .param("firstName", "first")
                .param("lastName", "last")
                .param("picreateGroupOnSignup", "true")
                .requestAttr("user", new User()))
        .andExpect(MockMvcResultMatchers.status().isOk());
    assertEquals(initialGrpCount + 1, countRowsInTable(jdbcTemplate, "rsGroup"));
  }
}
