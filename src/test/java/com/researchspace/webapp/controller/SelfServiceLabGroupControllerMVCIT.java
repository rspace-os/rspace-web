package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.service.SystemPropertyName;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class SelfServiceLabGroupControllerMVCIT extends MVCTestBase {

  private User pi;
  private String previousSetting;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    previousSetting = sysPropMgr.findByName(SystemPropertyName.SELF_SERVICE_LABGROUPS).getValue();
    pi = createAndSaveUser("pi" + getRandomName(10), Constants.PI_ROLE);
    initUser(pi);
    logoutAndLoginAs(pi);
  }

  @AfterEach
  public void restoreSetting() {
    if (previousSetting != null) {
      sysPropMgr.save(
          SystemPropertyName.SELF_SERVICE_LABGROUPS, previousSetting, getSysAdminUser());
    }
  }

  @Test
  public void formIsRefusedWhileDenied() throws Exception {
    setSelfServiceLabGroups(HierarchicalPermission.DENIED);
    MvcResult result = mockMvc.perform(get("/selfServiceLabGroup/group/new")).andReturn();
    assertInstanceOf(AuthorizationException.class, result.getResolvedException());
  }

  @Test
  public void creationIsRefusedWhileDeniedAndWorksOnceAllowed() throws Exception {
    setSelfServiceLabGroups(HierarchicalPermission.DENIED);
    MvcResult denied = postCreate("DeniedGroup");
    assertInstanceOf(AuthorizationException.class, denied.getResolvedException());
    assertFalse(ownsGroupNamed("DeniedGroup"));

    setSelfServiceLabGroups(HierarchicalPermission.ALLOWED);
    MvcResult allowed = postCreate("AllowedGroup");
    assertNull(allowed.getResolvedException());
    Long groupId = newGroupId(allowed);
    Group group = grpMgr.getGroup(groupId);
    assertTrue(group.isSelfService());
    assertEquals(pi.getId(), group.getOwner().getId());
    grpMgr.removeGroup(groupId, pi);
  }

  @Test
  public void deletionIsRefusedWhileDenied() throws Exception {
    setSelfServiceLabGroups(HierarchicalPermission.ALLOWED);
    Long groupId = newGroupId(postCreate("ToDelete"));

    setSelfServiceLabGroups(HierarchicalPermission.DENIED);
    MvcResult denied =
        mockMvc.perform(post("/selfServiceLabGroup/deleteGroup/" + groupId)).andReturn();
    assertInstanceOf(AuthorizationException.class, denied.getResolvedException());
    assertNotNull(grpMgr.getGroup(groupId));

    setSelfServiceLabGroups(HierarchicalPermission.ALLOWED);
    mockMvc
        .perform(post("/selfServiceLabGroup/deleteGroup/" + groupId))
        .andExpect(status().is3xxRedirection());
    assertThrows(ObjectRetrievalFailureException.class, () -> grpMgr.getGroup(groupId));
  }

  private void setSelfServiceLabGroups(HierarchicalPermission value) {
    sysPropMgr.save(SystemPropertyName.SELF_SERVICE_LABGROUPS, value, getSysAdminUser());
  }

  private MvcResult postCreate(String groupName) throws Exception {
    CreateCloudGroup toPost = new CreateCloudGroup();
    toPost.setGroupName(groupName);
    toPost.setPiEmail(pi.getEmail());
    return mockMvc
        .perform(
            post("/selfServiceLabGroup/createSelfServiceLabGroup")
                .content(JacksonUtil.toJson(toPost))
                .contentType(MediaType.APPLICATION_JSON))
        .andReturn();
  }

  private boolean ownsGroupNamed(String name) {
    return grpMgr.listGroupsForOwner(pi).stream().anyMatch(g -> name.equals(g.getDisplayName()));
  }

  private Long newGroupId(MvcResult result) throws Exception {
    Map<?, ?> data = getFromJsonAjaxReturnObject(result, Map.class);
    return Long.valueOf((String) data.get("newGroup"));
  }
}
