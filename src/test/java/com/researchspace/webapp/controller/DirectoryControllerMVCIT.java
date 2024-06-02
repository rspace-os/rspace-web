package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.views.PublicUserList;
import java.security.Principal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class DirectoryControllerMVCIT extends MVCTestBase {

  @Autowired DirectoryController directoryController;

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void getUserListingTest() throws Exception {

    User user = createAndSaveUser(getRandomAlphabeticString("user"));
    initUsers(false, user);
    logoutAndLoginAs(user);

    // basic default listing
    MockPrincipal adminPrincipal = new MockPrincipal(user.getUsername());
    MvcResult result =
        this.mockMvc
            .perform(get("/directory/").principal(adminPrincipal))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("pageReload"))
            .andReturn();
    ISearchResults<PublicUserList> uui = getUserListFromModel(result);
    assertTrue(uui.getTotalHits().intValue() > 0);
  }

  @Test
  public void getDisabledUsersNoListingTest() throws Exception {

    User user2 = createAndSaveUser(getRandomAlphabeticString("user2"));
    initUsers(false, user2);
    logoutAndLoginAs(piUser);

    Principal principal = piUser::getUsername;
    MvcResult result =
        this.mockMvc
            .perform(get("/directory/").principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<PublicUserList> uui = getUserListFromModel(result);
    int userListCount = uui.getTotalHits().intValue();

    user2 = userMgr.get(user2.getId());
    user2.setEnabled(false);
    userMgr.save(user2);

    MvcResult result2 =
        this.mockMvc
            .perform(get("/directory/").principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<PublicUserList> uui2 = getUserListFromModel(result2);
    int userListCount2 = uui2.getTotalHits().intValue();

    assertEquals(userListCount2, (userListCount - 1));
  }

  @Test
  public void orderedUsersListingTest() throws Exception {

    Principal principal = piUser::getUsername;
    logoutAndLoginAs(piUser);
    MvcResult result =
        this.mockMvc
            .perform(
                get("/directory/ajax/userlist")
                    .param("sortOrder", "DESC")
                    .param("orderBy", "lastName")
                    .param("resultsPerPage", "50")
                    .principal(principal))
            .andExpect(status().isOk())
            .andExpect(model().attributeDoesNotExist("pageReload"))
            .andReturn();
    ISearchResults<PublicUserList> uui = getUserListFromModel(result);
    PublicUserList first = uui.getResults().get(0);

    MvcResult result2 =
        this.mockMvc
            .perform(
                get("/directory/ajax/userlist")
                    .param("sortOrder", "ASC")
                    .param("orderBy", "lastName")
                    .param("resultsPerPage", "50")
                    .principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<PublicUserList> uui2 = getUserListFromModel(result2);

    PublicUserList last = uui2.getResults().get((int) (uui2.getTotalHits() - 1));
    assertEquals(first, last);
  }

  @Test
  public void searchedAndOrderedUsersListingTest() throws Exception {

    // create user with last name that will be last on a sorted list
    User zzzUser = createAndSaveUser(getRandomAlphabeticString("user"));
    zzzUser.setLastName("zzzLastName");
    zzzUser = userMgr.save(zzzUser);

    String searchTerm = "user";
    Principal principal = piUser::getUsername;
    logoutAndLoginAs(piUser);
    MvcResult result =
        this.mockMvc
            .perform(
                get("/directory/ajax/userlist")
                    .param("sortOrder", "DESC")
                    .param("orderBy", "lastName")
                    .param("resultsPerPage", "20")
                    .param("allFields", searchTerm)
                    .principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<PublicUserList> uui = getUserListFromModel(result);
    int totalHits = uui.getTotalHits().intValue();
    assertTrue(totalHits > 0);
    PublicUserList first = uui.getFirstResult();
    assertEquals(zzzUser.getLastName(), first.getUserInfo().getLastName());

    MvcResult result2 =
        this.mockMvc
            .perform(
                get("/directory/ajax/userlist")
                    .param("sortOrder", "ASC")
                    .param("orderBy", "lastName")
                    .param("resultsPerPage", "20")
                    .param("allFields", searchTerm)
                    .principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<PublicUserList> uui2 = getUserListFromModel(result2);
    assertEquals(uui2.getTotalHits().intValue(), totalHits);
    PublicUserList last = uui2.getLastResult();
    assertEquals(
        "mismatch: "
            + first.getUserInfo().getLastName()
            + "/"
            + last.getUserInfo().getLastName()
            + "; total hits: "
            + totalHits,
        first,
        last);
  }

  @Test
  public void getGroupListingTest() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("user"), Constants.PI_ROLE);

    initUsers(pi);
    logoutAndLoginAs(pi);
    // create 5 groups
    Group grp = createGroupForUsers(pi, pi.getUsername(), "", pi);

    // basic group listing
    MockPrincipal userPrincipal = new MockPrincipal(piUser.getUsername());
    MvcResult result =
        this.mockMvc
            .perform(get("/directory/ajax/grouplist").principal(userPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<Group> res = getGroupListFromModel(result);
    assertTrue(res.getTotalHits() > 0);
    for (Group group : res.getResults()) {
      assertNotNull(group.getCommunity()); // check is loaded with no LazyLoading exceptions
    }

    // check admin can see groups in directory RSPAC315
    logoutAndLoginAsCommunityAdmin();
    MvcResult adminResults =
        this.mockMvc
            .perform(get("/directory/ajax/grouplist").principal(new MockPrincipal(ADMIN_UNAME)))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<Group> groupsVisibleToAdmin = getGroupListFromModel(result);
    assertTrue(groupsVisibleToAdmin.getTotalHits() > 0);
  }

  @Test
  public void orderedGroupsListingTest() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("user"), Constants.PI_ROLE);

    initUsers(pi);
    logoutAndLoginAs(pi);
    // create 5 groups
    Group grp = createGroupForUsers(pi, pi.getUsername(), "", pi);

    Principal principal = piUser::getUsername;
    MvcResult result =
        this.mockMvc
            .perform(
                get("/directory/ajax/grouplist")
                    .param("sortOrder", "DESC")
                    .param("orderBy", "uniqueName")
                    .param("resultsPerPage", "20")
                    .principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<Group> groupList = getGroupListFromModel(result);
    Group first = groupList.getResults().get(0);

    MvcResult result2 =
        this.mockMvc
            .perform(
                get("/directory/ajax/grouplist")
                    .param("sortOrder", "ASC")
                    .param("orderBy", "uniqueName")
                    .param("resultsPerPage", "20")
                    .principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<Group> groupList2 = getGroupListFromModel(result2);

    Group last = groupList2.getResults().get((int) (groupList2.getTotalHits() - 1));
    assertEquals(first, last);
  }

  @Test
  public void getCommunityListingTest() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("user"), Constants.PI_ROLE);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);

    initUsers(pi, admin);
    logoutAndLoginAs(pi);

    // create 5 groups
    Group grp = createGroupForUsers(pi, pi.getUsername(), "", pi);
    Community comm = createAndSaveCommunity(admin, getRandomAlphabeticString("comm"));
    logoutAndLoginAs(admin);
    communityMgr.addGroupToCommunity(grp.getId(), comm.getId(), admin);

    // basic community listing
    Principal userPrincipal = piUser::getUsername;
    MvcResult result =
        this.mockMvc
            .perform(get("/directory/ajax/communitylist").principal(userPrincipal))
            .andExpect(status().isOk())
            .andReturn();

    ISearchResults<Community> res = getCommunityListFromModel(result);
    assertTrue(res.getTotalHits() > 0);

    // now let's retrieve a single community view
    MvcResult communityView =
        this.mockMvc
            .perform(get("/directory/community/{id}", comm.getId()).principal(userPrincipal))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("view", "canEdit", "community"))
            .andReturn();
  }

  @Test
  public void orderedCommunitiesListingTest() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("user"), Constants.PI_ROLE);
    User firstadmin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    User lastadmin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);

    initUsers(pi);
    logoutAndLoginAs(pi);
    // create 5 groups
    Group grp = createGroupForUsers(pi, pi.getUsername(), "", pi);
    Community commA = createAndSaveCommunity(firstadmin, getRandomAlphabeticString("aaa"));
    Community commZ = createAndSaveCommunity(lastadmin, getRandomAlphabeticString("zzz"));
    logoutAndLoginAs(firstadmin);
    communityMgr.addGroupToCommunity(grp.getId(), commA.getId(), firstadmin);

    Principal principal = piUser::getUsername;
    MvcResult result =
        this.mockMvc
            .perform(
                get("/directory/ajax/communitylist")
                    .param("sortOrder", "DESC")
                    .param("orderBy", "uniqueName")
                    .param("resultsPerPage", "20")
                    .principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<Community> communityList = getCommunityListFromModel(result);
    Community first = communityList.getFirstResult();
    assertEquals(commZ, first);
    MvcResult result2 =
        this.mockMvc
            .perform(
                get("/directory/ajax/communitylist")
                    .param("sortOrder", "ASC")
                    .param("orderBy", "uniqueName")
                    .param("resultsPerPage", "20")
                    .principal(principal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<Community> communityList2 = getCommunityListFromModel(result2);

    first = communityList2.getFirstResult();
    assertEquals(commA, first);
  }

  private ISearchResults<PublicUserList> getUserListFromModel(MvcResult result) {
    @SuppressWarnings("unchecked")
    ISearchResults<PublicUserList> uui =
        (ISearchResults<PublicUserList>) result.getModelAndView().getModel().get("users");
    return uui;
  }

  private ISearchResults<Group> getGroupListFromModel(MvcResult result) {
    @SuppressWarnings("unchecked")
    ISearchResults<Group> groups =
        (ISearchResults<Group>) result.getModelAndView().getModel().get("groups");
    return groups;
  }

  private ISearchResults<Community> getCommunityListFromModel(MvcResult result) {
    @SuppressWarnings("unchecked")
    ISearchResults<Community> communities =
        (ISearchResults<Community>) result.getModelAndView().getModel().get("communities");
    return communities;
  }
}
