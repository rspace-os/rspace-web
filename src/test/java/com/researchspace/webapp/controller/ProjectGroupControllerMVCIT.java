package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.Constants;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.raid.UserRaid;
import com.researchspace.service.RaIDServiceManager;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class ProjectGroupControllerMVCIT extends MVCTestBase {

  @Autowired private RaIDServiceManager raIDServiceManager;

  private User pi;
  private ObjectMapper objectMapper = new ObjectMapper();
  private CreateCloudGroup projectGroupCreationObj;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    this.pi = createAndSaveUser("pi_" + getRandomName(10), Constants.PI_ROLE);
    initUser(this.pi);
    logoutAndLoginAs(pi);

    projectGroupCreationObj = new CreateCloudGroup();
    projectGroupCreationObj.setGroupName("ProjectGroupWithRaid");
    projectGroupCreationObj.setSessionUser(pi);
    projectGroupCreationObj.setRaid(new RaIDReferenceDTO("raidServerAlias_X", "raidIdentifier_Y"));
    projectGroupCreationObj.setPiEmail(pi.getEmail());
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
    assertEquals("raidServerAlias_X", newProjectGroup.getRaid().getRaidServerAlias());
    assertEquals("raidIdentifier_Y", newProjectGroup.getRaid().getRaidIdentifier());
    assertEquals(newProjectGroupId, newProjectGroup.getRaid().getGroupAssociated().getId());
    assertEquals(pi.getId(), newProjectGroup.getRaid().getOwner().getId());
    Long raidId = newProjectGroup.getRaid().getId();
    assertNotNull(raidId);

    UserRaid raidSaved = raIDServiceManager.getUserRaid(raidId);
    assertEquals("raidServerAlias_X", raidSaved.getRaidServerAlias());
    assertEquals("raidIdentifier_Y", raidSaved.getRaidIdentifier());
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

    assertExceptionThrown(
        () -> grpMgr.getGroup(newProjectGroupId), ObjectRetrievalFailureException.class);
    assertExceptionThrown(
        () -> raIDServiceManager.getUserRaid(raidId), ObjectRetrievalFailureException.class);
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
