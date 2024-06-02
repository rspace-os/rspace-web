package com.researchspace.service.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.User;
import com.researchspace.model.views.PublicUserList;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.security.Principal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
@CommunityTestContext
public class CloudDirectoryControllerMVCIT extends RealTransactionSpringTestBase {

  @Autowired private WebApplicationContext wac;

  private MockMvc mockMvc;

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Before
  public void setup() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    super.setUp();
  }

  @Test
  public void communityUserListing() throws Exception {
    User newPi = createAndSaveUser("newPi", Constants.PI_ROLE);
    String userName = getRandomAlphabeticString("anyUser");
    User otherUser = createAndSaveUser(userName);
    initUsers(newPi, otherUser);
    logoutAndLoginAs(newPi);
    Principal principal = () -> newPi.getUsername();
    MvcResult result =
        this.mockMvc
            .perform(get("/directory").principal(principal))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("pageReload"))
            .andReturn();
    ISearchResults<PublicUserList> uui = getUserListFromModel(result);
    // no records by default on initial load
    assertEquals(0, uui.getTotalHits().intValue());

    MvcResult result5 =
        this.mockMvc
            .perform(
                get("/directory/ajax/userlist").param("pageReload", "true").principal(principal))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("pageReload"))
            .andReturn();

    MvcResult result4 =
        this.mockMvc
            .perform(get("/directory/ajax/userlist").principal(principal))
            .andExpect(status().isOk())
            .andExpect(model().attributeDoesNotExist("pageReload"))
            .andReturn();
    ISearchResults<PublicUserList> uui4 = getUserListFromModel(result4);
    // no records by default
    assertEquals(0, uui4.getTotalHits().intValue());
    // 2char search term - no results
    MvcResult result2 =
        this.mockMvc
            .perform(
                get("/directory/ajax/userlist")
                    .param("sortOrder", "DESC")
                    .param("orderBy", "lastName")
                    .param("resultsPerPage", "50")
                    .param("allFields", userName.substring(0, 2))
                    .principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<PublicUserList> uui2 = getUserListFromModel(result2);
    assertEquals(0, uui2.getTotalHits().intValue());

    // 3 char search term - get results
    MvcResult result3 =
        this.mockMvc
            .perform(
                get("/directory/ajax/userlist")
                    .param("sortOrder", "DESC")
                    .param("orderBy", "lastName")
                    .param("resultsPerPage", "50")
                    .param("allFields", userName.substring(0, 3))
                    .principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<PublicUserList> uui3 = getUserListFromModel(result3);
    assertTrue(uui3.getTotalHits().intValue() > 0);
  }

  private ISearchResults<PublicUserList> getUserListFromModel(MvcResult result) {
    @SuppressWarnings("unchecked")
    ISearchResults<PublicUserList> uui =
        (ISearchResults<PublicUserList>) result.getModelAndView().getModel().get("users");
    return uui;
  }
}
