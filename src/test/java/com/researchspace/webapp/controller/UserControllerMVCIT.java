package com.researchspace.webapp.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import org.apache.shiro.authz.AuthorizationException;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
public class UserControllerMVCIT extends MVCTestBase {
  private MockMvc mockMvc;

  @Autowired private WebApplicationContext wac;
  @Autowired SessionFactory sf;

  @Before
  public void setup() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    super.setUp();
  }

  @Test
  public void testAuthoriseAccountRequiresAdminRole() throws Exception {
    User authoriser = createAndSaveUser(CoreTestUtils.getRandomName(8));
    User signedup = createAndSaveUser(CoreTestUtils.getRandomName(8));
    signedup.setAccountLocked(true); // mimic signup with authorisation
    userMgr.save(signedup);
    logoutAndLoginAs(authoriser);
    MvcResult result =
        this.mockMvc
            .perform(
                get("/admin/users/authorise/{id}", signedup.getId())
                    .principal(new MockPrincipal(piUser.getUsername())))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(result.getResolvedException() instanceof AuthorizationException);
    assertTrue(userMgr.get(signedup.getId()).isAccountLocked());

    logoutAndLoginAsSysAdmin();
    this.mockMvc
        .perform(
            get("/admin/users/authorise/{id}", signedup.getId())
                .principal(new MockPrincipal(piUser.getUsername())))
        .andExpect(status().isOk())
        .andExpect(view().name("accountActivated"))
        .andReturn();
    assertFalse(userMgr.get(signedup.getId()).isAccountLocked());
  }

  @Test
  public void testDenyAccountRequiresAdminRole() throws Exception {
    User authoriser = createAndSaveUser(CoreTestUtils.getRandomName(8));
    User signedup = createAndSaveUser(CoreTestUtils.getRandomName(8));
    signedup.setAccountLocked(true);
    userMgr.save(signedup);
    logoutAndLoginAs(authoriser);
    MvcResult result =
        this.mockMvc
            .perform(
                get("/admin/users/deny/{id}", signedup.getId())
                    .principal(new MockPrincipal(piUser.getUsername())))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(result.getResolvedException() instanceof AuthorizationException);

    logoutAndLoginAsSysAdmin();
    this.mockMvc
        .perform(
            get("/admin/users/deny/{id}", signedup.getId())
                .principal(new MockPrincipal(piUser.getUsername())))
        .andExpect(status().isOk())
        .andExpect(view().name("accountDenied"))
        .andReturn();
    assertTrue(userMgr.get(signedup.getId()).isAccountLocked());
  }
}
