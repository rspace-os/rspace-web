package com.researchspace.service.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import com.researchspace.service.impl.license.NoCheckLicenseService;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.webapp.controller.MVCTestBase;
import com.researchspace.webapp.controller.SignupController;
import java.security.Principal;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
@CommunityTestContext
public class CloudSignupControllerMVCIT extends MVCTestBase {

  private static final String VALID_PWD = "pwd12345";

  class PrincipalSetter implements RequestPostProcessor {
    Principal principal;

    public PrincipalSetter(Principal principal) {
      super();
      this.principal = principal;
    }

    @Override
    public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
      request.setUserPrincipal(principal);
      return request;
    }
  }

  @Autowired SignupController signupController;

  @Autowired private WebApplicationContext wac;

  @Autowired SessionFactory sf;

  private MockMvc mockMvc;

  @Before
  public void setup() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    signupController.setLicenseService(new NoCheckLicenseService());
  }

  @Test
  public void postSignupCloudFormTest() throws Exception {
    assertTrue(propertyHolder.isCloud());
    final String uname = getRandomAlphabeticString("newUser");
    final String EXPECTED_AFFILIATION = "Uni of RSpace";

    // needs affiliation
    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", uname)
                .param("email", "xxx")
                .param("password", VALID_PWD)
                .param("confirmPassword", VALID_PWD)
                .param("firstName", "pwd")
                .param("lastName", "pwd")
                .param("affiliation", ""))
        .andExpect(model().attributeHasFieldErrors("user", "affiliation"));
    // affiliation  now included
    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", uname)
                .param("email", "xxx")
                .param("password", VALID_PWD)
                .param("confirmPassword", VALID_PWD)
                .param("firstName", "pwd")
                .param("lastName", "pwd")
                .param("affiliation", EXPECTED_AFFILIATION))
        .andExpect(model().hasNoErrors());

    // check is persisted OK
    assertEquals(EXPECTED_AFFILIATION, userMgr.getUserByUsername(uname).getAffiliation());
  }
}
