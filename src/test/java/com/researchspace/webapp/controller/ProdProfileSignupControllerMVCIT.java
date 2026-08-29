package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.SignupControllerMVCIT.CONFIRM_PASSWORD_PARAM;
import static com.researchspace.webapp.controller.SignupControllerMVCIT.PASSWORD_PARAM;
import static com.researchspace.webapp.controller.SignupControllerMVCIT.VALID_PWD;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.jdbc.JdbcTestUtils.countRowsInTable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.researchspace.model.User;
import com.researchspace.service.impl.ProdContentInitializerManager;
import com.researchspace.testutils.ProdProfileTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/** Runs production-profile signup and content initialisation procedure. */
@ProdProfileTestConfiguration
public class ProdProfileSignupControllerMVCIT extends MVCTestBase {

  private @Autowired JdbcTemplate jdbcTemplate;
  private Object originalBuiltins;
  private Object originalSampleBuiltins;

  @BeforeEach
  public void clearProductionContentFixtures() {
    originalBuiltins =
        ReflectionTestUtils.getField(ProdContentInitializerManager.class, "builtins");
    originalSampleBuiltins =
        ReflectionTestUtils.getField(ProdContentInitializerManager.class, "sampleBuiltins");
    ReflectionTestUtils.setField(ProdContentInitializerManager.class, "builtins", null);
    ReflectionTestUtils.setField(ProdContentInitializerManager.class, "sampleBuiltins", null);
  }

  @AfterEach
  public void restoreProductionContentFixtures() {
    ReflectionTestUtils.setField(ProdContentInitializerManager.class, "builtins", originalBuiltins);
    ReflectionTestUtils.setField(
        ProdContentInitializerManager.class, "sampleBuiltins", originalSampleBuiltins);
  }

  @Test
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
