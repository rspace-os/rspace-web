package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.Constants;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.raid.UserRaid;
import com.researchspace.service.RaIDServiceManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class ProjectGroupControllerMVCIT extends MVCTestBase {

  private static final String RAID_SERVER_ALIAS = "raidServerAlias_X";
  private static final String RAID_IDENTIFIER = "https://raid.org/10.12345/FKHJ78";
  private static final String RAID_TITLE = "Raid Title 1";
  @Autowired private RaIDServiceManager raIDServiceManager;

  private User pi;
  private ObjectMapper objectMapper = new ObjectMapper();
  private CreateCloudGroup projectGroupCreationObj;
  private String previousSetting;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    previousSetting = sysPropMgr.findByName(SystemPropertyName.ALLOW_PROJECT_GROUPS).getValue();
    setProjectGroups(HierarchicalPermission.ALLOWED);
    this.pi = createAndSaveUser("pi" + getRandomName(10), Constants.PI_ROLE);
    initUser(this.pi);
    logoutAndLoginAs(pi);

    projectGroupCreationObj = new CreateCloudGroup();
    projectGroupCreationObj.setGroupName("ProjectGroupWithRaid");
    projectGroupCreationObj.setSessionUser(pi);
    projectGroupCreationObj.setRaid(new RaIDReferenceDTO(RAID_SERVER_ALIAS, RAID_IDENTIFIER));
    projectGroupCreationObj.setPiEmail(pi.getEmail());
  }

  @AfterEach
  public void restoreSetting() {
    if (previousSetting != null) {
      sysPropMgr.save(SystemPropertyName.ALLOW_PROJECT_GROUPS, previousSetting, getSysAdminUser());
    }
  }

  @Test
  public void createProjectGroupWithRaID() throws Exception {

    MvcResult result =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationObj))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    Long newProjectGroupId = extractProjectGroupId(result);

    assertNotNull(newProjectGroupId);
    Group newProjectGroup = grpMgr.getGroup(newProjectGroupId);
    assertNotNull(newProjectGroup.getRaid());
    assertEquals(RAID_SERVER_ALIAS, newProjectGroup.getRaid().getRaidServerAlias());
    assertEquals(RAID_IDENTIFIER, newProjectGroup.getRaid().getRaidIdentifier());
    assertEquals(newProjectGroupId, newProjectGroup.getRaid().getGroupAssociated().getId());
    assertEquals(pi.getId(), newProjectGroup.getRaid().getOwner().getId());
    Long raidId = newProjectGroup.getRaid().getId();
    assertNotNull(raidId);

    UserRaid raidSaved = raIDServiceManager.getUserRaid(raidId);
    assertEquals(RAID_SERVER_ALIAS, raidSaved.getRaidServerAlias());
    assertEquals(RAID_IDENTIFIER, raidSaved.getRaidIdentifier());
    assertEquals(newProjectGroupId, raidSaved.getGroupAssociated().getId());
    assertEquals(pi.getId(), raidSaved.getOwner().getId());

    // teardown
    grpMgr.removeGroup(newProjectGroupId, piUser);
  }

  @Test
  public void removeProjectGroupWhileHavingRaIDAssociated() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationObj))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    Long newProjectGroupId = extractProjectGroupId(result);
    Group newProjectGroup = grpMgr.getGroup(newProjectGroupId);
    Long raidId = newProjectGroup.getRaid().getId();
    assertNotNull(raidId);
    UserRaid raidSaved = raIDServiceManager.getUserRaid(raidId);
    assertNotNull(raidSaved);

    mockMvc
        .perform(post("/projectGroup/deleteGroup/" + newProjectGroupId))
        .andExpect(status().is3xxRedirection())
        .andReturn();

    assertThrows(ObjectRetrievalFailureException.class, () -> grpMgr.getGroup(newProjectGroupId));
    assertThrows(
        ObjectRetrievalFailureException.class, () -> raIDServiceManager.getUserRaid(raidId));
  }

  @Test
  public void formIsRefusedWhileDenied() throws Exception {
    setProjectGroups(HierarchicalPermission.DENIED);
    MvcResult result = mockMvc.perform(get("/projectGroup/newGroupForm")).andReturn();
    assertInstanceOf(AuthorizationException.class, result.getResolvedException());
  }

  @Test
  public void creationIsRefusedWhileDenied() throws Exception {
    setProjectGroups(HierarchicalPermission.DENIED);
    MvcResult result =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationObj))
                    .contentType(MediaType.APPLICATION_JSON))
            .andReturn();
    assertInstanceOf(AuthorizationException.class, result.getResolvedException());
    assertTrue(
        grpMgr.listGroupsForOwner(pi).stream()
            .noneMatch(g -> "ProjectGroupWithRaid".equals(g.getDisplayName())));
  }

  @Test
  public void deletionStillWorksWhileDenied() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationObj))
                    .contentType(MediaType.APPLICATION_JSON))
            .andReturn();
    Long groupId = extractProjectGroupId(created);

    setProjectGroups(HierarchicalPermission.DENIED);
    mockMvc
        .perform(post("/projectGroup/deleteGroup/" + groupId))
        .andExpect(status().is3xxRedirection());
    assertThrows(ObjectRetrievalFailureException.class, () -> grpMgr.getGroup(groupId));
  }

  @Test
  public void promotedGroupOwnerCanDeleteGroup() throws Exception {
    Long groupId = createProjectGroup();
    User promoted = addMemberWithUserRole(groupId);
    grpMgr.setRoleForUser(groupId, promoted.getId(), RoleInGroup.GROUP_OWNER.name(), pi);

    logoutAndLoginAs(promoted);
    mockMvc
        .perform(post("/projectGroup/deleteGroup/" + groupId))
        .andExpect(status().is3xxRedirection());
    assertThrows(ObjectRetrievalFailureException.class, () -> grpMgr.getGroup(groupId));
  }

  @Test
  public void memberWithUserRoleCannotDeleteGroup() throws Exception {
    Long groupId = createProjectGroup();
    User member = addMemberWithUserRole(groupId);

    logoutAndLoginAs(member);
    MvcResult result = mockMvc.perform(post("/projectGroup/deleteGroup/" + groupId)).andReturn();
    assertInstanceOf(AuthorizationException.class, result.getResolvedException());
    assertNotNull(grpMgr.getGroup(groupId));

    // teardown: the group holds the RaID every test in this class binds
    logoutAndLoginAs(pi);
    grpMgr.removeGroup(groupId, pi);
  }

  private Long createProjectGroup() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationObj))
                    .contentType(MediaType.APPLICATION_JSON))
            .andReturn();
    return extractProjectGroupId(created);
  }

  private User addMemberWithUserRole(Long groupId) throws Exception {
    User member = createAndSaveUser("member" + getRandomName(8));
    initUser(member);
    grpMgr.addUserToGroup(member.getUsername(), groupId, RoleInGroup.DEFAULT);
    return member;
  }

  private void setProjectGroups(HierarchicalPermission value) {
    sysPropMgr.save(SystemPropertyName.ALLOW_PROJECT_GROUPS, value, getSysAdminUser());
  }

  @NotNull
  private Long extractProjectGroupId(MvcResult result)
      throws JsonProcessingException, UnsupportedEncodingException {
    Map mapResult = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    assertNull((mapResult.get("error")));
    assertTrue((boolean) mapResult.get("success"));
    return Long.valueOf(((Map<String, String>) mapResult.get("data")).get("newGroup"));
  }
}
