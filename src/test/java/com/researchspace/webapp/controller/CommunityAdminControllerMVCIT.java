package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.researchspace.Constants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.dao.GroupDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import java.security.Principal;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MvcResult;

public class CommunityAdminControllerMVCIT extends MVCTestBase {

  @Autowired MockServletContext servletContext;

  @Autowired GroupDao grpdao;

  private Principal sysAdminPrincipal;
  private User syasadmin;

  @Before
  public void setup() throws Exception {
    super.setUp();
    Role role = roleMgr.getRole("ROLE_SYSADMIN");
    syasadmin = createAndSaveUser(CoreTestUtils.getRandomName(10), Constants.SYSADMIN_ROLE);
    initUser(syasadmin);
    logoutAndLoginAs(syasadmin);
    sysAdminPrincipal = syasadmin::getUsername;
  }

  @Test
  public void testRemoveGroupFromCommunity() throws Exception {
    User admin = logoutAndLoginAsSysAdmin();
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUsers(pi);
    // added to default community
    Group grp = createGroupForUsers(pi, pi.getUsername(), "", pi);
    // try to remove from default community; this is not allowed
    MvcResult result =
        mockMvc
            .perform(
                post("/community/admin/ajax/remove")
                    .principal(sysAdminPrincipal)
                    .param("ids[]", grp.getId() + "")
                    .param("communityId", Community.DEFAULT_COMMUNITY_ID + ""))
            .andReturn();
    Map json = parseJSONObjectFromResponseStream(result);
    assertNull(json.get("data"));
    assertNotNull(json.get("errorMsg"));
    // now we create a new community and move group to it:
    Community comm = createAndSaveCommunity(syasadmin, getRandomAlphabeticString("comm"));
    communityMgr.addGroupToCommunity(grp.getId(), comm.getId(), admin);

    // now we remove group from new comunity; it will be added back to the
    // default community
    MvcResult result2 =
        mockMvc
            .perform(
                post("/community/admin/ajax/remove")
                    .principal(sysAdminPrincipal)
                    .param("ids[]", grp.getId() + "")
                    .param("communityId", comm.getId() + ""))
            .andReturn();
    Map json2 = parseJSONObjectFromResponseStream(result2);
    assertNotNull(json2.get("data"));
    assertNull(json2.get("errorMsg"));
    assertEquals(comm.getId().longValue(), Long.parseLong(json2.get("data").toString()));
    // assert group has moved
    assertTrue(
        communityMgr
            .getCommunityWithAdminsAndGroups(Community.DEFAULT_COMMUNITY_ID)
            .getLabGroups()
            .contains(grp));
  }
}
