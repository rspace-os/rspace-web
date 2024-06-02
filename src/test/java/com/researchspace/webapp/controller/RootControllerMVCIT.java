package com.researchspace.webapp.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.testutils.RSpaceTestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
public class RootControllerMVCIT extends MVCTestBase {

  @Autowired private WebApplicationContext wac;

  private MockMvc mockMvc;

  @Before
  public void setup() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    RSpaceTestUtils.logout();
  }

  @Test
  public void testRootURLRedirectsToWorkspace() throws Exception {
    // login so we don't get redirected to login page
    RSpaceTestUtils.login(piUser.getUsername(), TESTPASSWD);

    this.mockMvc
        .perform(get("/"))
        .andExpect(status().isFound())
        . // 302 redirect code
        // check we redirect to workspace
        andExpect(redirectedUrl("/workspace"));
  }
}
