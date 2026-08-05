package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.testutils.RSpaceTestUtils.logout;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import com.researchspace.Constants;
import com.researchspace.admin.service.GroupUsageInfo;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.ui.ModelMap;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SysadminGroupsControllerMVCIT extends MVCTestBase {

  private static final String SYSTEM_GROUPS_AJAX_LIST = "/system/groups/ajax/list";
  private static final String SYSTEM_GROUPS_LIST = "/system/groups/list";

  @After
  public void teardown() throws Exception {
    super.tearDown();
  }

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void groupListByUserDenied() throws Exception {
    logoutAndLoginAs(piUser);
    MvcResult result = basicGroupList();
    assertAuthorizationException(result);
  }

  @Test
  public void groupAjaxListByUserDenied() throws Exception {
    logoutAndLoginAs(piUser);
    MvcResult result = basicAjaxGroupList();
    assertAuthorizationException(result);
  }

  @Test
  public void groupListByCommunityAdminPermitted() throws Exception {
    logoutAndLoginAsCommunityAdmin();
    MvcResult result = basicGroupList();
    assertNull(result.getResolvedException());
  }

  @Test
  public void listGroupsByCommunityAdmin() throws Exception {
    Group largest = setUp12groupsandReturnLargest();
    // now test listing by community admin, who is admin of 1 group.
    User newadmin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    // new admin has no community and should see no groups
    logoutAndLoginAs(newadmin);
    MvcResult result = basicGroupList();
    List<GroupUsageInfo> grps = getListOfGroupInfo(result);
    assertEquals(0, grps.size());
    User sysadmin = logoutAndLoginAsSysAdmin();
    Community comm = createAndSaveCommunity(newadmin, getRandomName(10));
    addGroupToCommunity(largest, comm, sysadmin);
    logoutAndLoginAs(newadmin);
    result = basicGroupList();
    grps = getListOfGroupInfo(result);
    assertEquals(1, grps.size());
    assertEquals(largest, grps.get(0).getGroup());
    grps = getListOfGroupInfo(basicAjaxGroupList());
    assertEquals(1, grps.size());
    assertEquals(largest, grps.get(0).getGroup());
    result = orderByGroupSize(SortOrder.ASC);
    grps = getListOfGroupInfo(result);
    assertEquals(1, grps.size());
    assertEquals(largest, grps.get(0).getGroup());

    result = orderByUsage(SortOrder.ASC, 0);
    grps = getListOfGroupInfo(result);
    assertEquals(1, grps.size());
  }

  @Test
  public void listGroupsBySysadmin() throws Exception {
    Group largest = setUp12groupsandReturnLargest();
    logoutAndLoginAsSysAdmin();
    MvcResult result = basicGroupList();

    List<GroupUsageInfo> grps = getListOfGroupInfo(result);
    assertEquals(10, grps.size());
    // check all data fields are returned
    assertResultFieldsArePopulated(grps);
    // now add search parameter
    result =
        mockMvc
            .perform(
                get(SYSTEM_GROUPS_AJAX_LIST)
                    .param(Group.DEFAULT_ORDERBY_FIELD, largest.getDisplayName()))
            .andReturn();
    grps = getListOfGroupInfo(result);
    assertEquals(1, grps.size());
    // file usage should not be 0
    assertTrue("fileusage is 0", grps.get(0).getFileUsage() > 0);
    assertTrue("usage percent is 0", grps.get(0).getPercent() > 0);

    MvcResult result2 =
        mockMvc
            .perform(get(SYSTEM_GROUPS_AJAX_LIST).param("resultsPerPage", "2"))
            .andExpect(model().attributeHasNoErrors())
            .andReturn();
    assertEquals(2, getListOfGroupInfo(result2).size());

    // now sort by group size, this needs to be in  transactional test as executes some SQL that
    // requires DB commits to test
    result2 = orderByGroupSize(SortOrder.DESC);
    grps = getListOfGroupInfo(result2);
    assertIsOrderedByGroupSizeDesc(grps);
    assertResultFieldsArePopulated(grps);
    result2 = orderByGroupSize(SortOrder.ASC);
    grps = getListOfGroupInfo(result2);
    assertIsOrderedByGroupSizeAsc(grps);
    assertResultFieldsArePopulated(grps);
    // check pagination working
    result2 = orderByUsage(SortOrder.DESC, 2);
    grps = getListOfGroupInfo(result2);
    assertEquals(4, grps.size()); // 12 grps in total.

    // now order by usage
    result2 = orderByUsage(SortOrder.DESC, 0);
    grps = getListOfGroupInfo(result2);
    assertIsOrderedByUsageDesc(grps);
    assertResultFieldsArePopulated(grps);
    result2 = orderByUsage(SortOrder.ASC, 0);
    grps = getListOfGroupInfo(result2);
    assertIsOrderedByUsageAsc(grps);
    result2 = orderByUsage(SortOrder.DESC, 2);
    grps = getListOfGroupInfo(result2);
    assertEquals(4, grps.size()); // 24 grps in total.

    // now order by piname
    result2 = orderByPiLastName(SortOrder.DESC, 0);
    grps = getListOfGroupInfo(result2);
    assertIsOrderedByPIDesc(grps);
    assertResultFieldsArePopulated(grps);
    result2 = orderByPiLastName(SortOrder.ASC, 0);
    grps = getListOfGroupInfo(result2);
    assertIsOrderedByPIAsc(grps);

    // now login as sysadmin and test the JSON list all methof
    logoutAndLoginAsSysAdmin();
    MvcResult result3 =
        mockMvc
            .perform(
                get("/groups/ajax/admin/listAll").principal(new MockPrincipal(SYS_ADMIN_UNAME)))
            .andReturn();
    Map data = parseJSONObjectFromResponseStream(result3);
    List results = (List) data.get("data");
    // i.e., all groups are listed
    assertTrue(results.size() > 10);
  }

  private Group setUp12groupsandReturnLargest() throws IOException {
    setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomName(10));
    initUser(other);
    User extra = createInitAndLoginAnyUser();
    // create some file content so extra's group fileusage > 0
    addImageToGallery(extra);
    logout();

    // create 11 groups
    for (int i = 0; i < 11; i++) {
      createGroupForUsersWithDefaultPi(piUser, other);
    }
    // and one larger group to check sort order by group size.
    Group largest = createGroupForUsersWithDefaultPi(piUser, other, extra);
    return largest;
  }

  private MvcResult basicGroupList() throws Exception {
    return _basicList(SYSTEM_GROUPS_LIST);
  }

  private MvcResult basicAjaxGroupList() throws Exception {
    return _basicList(SYSTEM_GROUPS_AJAX_LIST);
  }

  private MvcResult _basicList(String url) throws Exception {
    MvcResult result =
        mockMvc.perform(get(url)).andExpect(model().attributeHasNoErrors()).andReturn();
    return result;
  }

  private void assertResultFieldsArePopulated(List<GroupUsageInfo> grps) {
    for (GroupUsageInfo info : grps) {
      assertNotNull(info.getGroup());
      assertNotNull(info.getGroup().getCommunity());
      assertNotNull(info.getFileUsage());
    }
  }

  private MvcResult orderByUsage(SortOrder order, int pageNum) throws Exception {
    return list(order, "usage", pageNum);
  }

  private MvcResult orderByPiLastName(SortOrder order, int i) throws Exception {
    return list(order, "owner.lastName", i);
  }

  List<GroupUsageInfo> getListOfGroupInfo(MvcResult result) {
    ModelMap modelMap = result.getModelAndView().getModelMap();
    List<GroupUsageInfo> rgs = (List) modelMap.get("groupInfo");
    return rgs;
  }

  private void assertIsOrderedByGroupSizeDesc(List<GroupUsageInfo> grps) {
    for (int i = 0, j = 1; j < grps.size(); i++, j++) {
      assertTrue(
          grps.get(i).getGroup().getMemberCount() >= grps.get(j).getGroup().getMemberCount());
    }
  }

  private void assertIsOrderedByPIDesc(List<GroupUsageInfo> grps) {
    for (int i = 0, j = 1; j < grps.size(); i++, j++) {
      assertTrue(
          grps.get(i)
                  .getGroup()
                  .getOwner()
                  .getFullName()
                  .toUpperCase()
                  .compareTo(grps.get(i).getGroup().getOwner().getFullName().toUpperCase())
              >= 0);
    }
  }

  private void assertIsOrderedByPIAsc(List<GroupUsageInfo> grps) {
    for (int i = 0, j = 1; j < grps.size(); i++, j++) {
      assertTrue(
          grps.get(i)
                  .getGroup()
                  .getOwner()
                  .getFullName()
                  .toUpperCase()
                  .compareTo(grps.get(i).getGroup().getOwner().getFullName().toUpperCase())
              <= 0);
    }
  }

  private void assertIsOrderedByUsageDesc(List<GroupUsageInfo> grps) {
    for (int i = 0, j = 1; j < grps.size(); i++, j++) {
      assertTrue(grps.get(i).getFileUsage() >= grps.get(j).getFileUsage());
    }
  }

  private void assertIsOrderedByUsageAsc(List<GroupUsageInfo> grps) {
    for (int i = 0, j = 1; j < grps.size(); i++, j++) {
      assertTrue(grps.get(i).getFileUsage() <= grps.get(j).getFileUsage());
    }
  }

  private void assertIsOrderedByGroupSizeAsc(List<GroupUsageInfo> grps) {
    for (int i = 0, j = 1; j < grps.size(); i++, j++) {
      assertTrue(
          grps.get(i).getGroup().getMemberCount() <= grps.get(j).getGroup().getMemberCount());
    }
  }

  private MvcResult orderByGroupSize(SortOrder order) throws Exception {
    return list(order, "memberCount", 0);
  }

  private MvcResult list(SortOrder order, String orderBy, int pageNumber) throws Exception {
    return mockMvc
        .perform(
            get(SYSTEM_GROUPS_LIST)
                .param("orderBy", orderBy)
                .param("sortOrder", order.name())
                .param("pageNumber", pageNumber + "")
                .param("resultsPerPage", "10"))
        .andExpect(model().attributeHasNoErrors())
        .andReturn();
  }
}
