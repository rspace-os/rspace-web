package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.SignupControllerMVCIT.CONFIRM_PASSWORD_PARAM;
import static com.researchspace.webapp.controller.SignupControllerMVCIT.PASSWORD_PARAM;
import static com.researchspace.webapp.controller.SignupControllerMVCIT.VALID_PWD;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.jdbc.JdbcTestUtils.countRowsInTable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.researchspace.model.User;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.ProdProfileTestConfiguration;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ProdProfileTestConfiguration
@WebAppConfiguration
@RunWith(ConditionalTestRunner.class)
/** Runs production-profile signup and content initialisation procedure. */
public class ProdProfileSignupControllerMVCIT extends AbstractJUnit4SpringContextTests {

  private @Autowired JdbcTemplate jdbcTemplate;
  private @Autowired IMutablePropertyHolder propertyHolder;
  private @Autowired WebApplicationContext wac;

  private MockMvc mockMvc;

  @Before
  public void setUp() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  @Ignore
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
