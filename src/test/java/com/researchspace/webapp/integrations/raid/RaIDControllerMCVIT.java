package com.researchspace.webapp.integrations.raid;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.service.IntegrationsHandler.RAID_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.Constants;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.dtos.RaidGroupAssociationDTO;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.raid.UserRaid;
import com.researchspace.model.record.Folder;
import com.researchspace.raid.model.RaID;
import com.researchspace.service.RaIDServiceManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.raid.RaIDServerConfigurationDTO;
import com.researchspace.service.raid.RaIDServiceClientAdapter;
import com.researchspace.webapp.controller.MVCTestBase;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class RaIDControllerMCVIT extends MVCTestBase {

  private static final String AUTH_BASE_URL_1 =
      "https://demo1.raid.org/realms/raid/protocol/openid-connect";
  private static final String AUTH_BASE_URL_2 =
      "https://demo2.raid.org/realms/raid/protocol/openid-connect";
  private static final String API_BASE_URL_1 = "https://demo1.raid.au";
  private static final String API_BASE_URL_2 = "https://demo2.raid.au";
  private static final String SERVER_ALIAS_1 = "alias1";
  private static final String SERVER_ALIAS_2 = "alias2";
  private static final String RAID_TITLE_1 = "Raid Title 1";
  private static final String RAID_TITLE_2 = "Raid Title 2";
  private static final String IDENTIFIER_ASSOCIATED_1 =
      "https://static.demo.raid.org.au/10.83334/c74980b1";
  private static final String IDENTIFIER_ASSOCIATED_2 =
      "https://static.demo.raid.org.au/10.83334/5b94e1cf";
  private static final RaIDReferenceDTO EXPECTED_RAID_ASSOCIATED_1 =
      new RaIDReferenceDTO(SERVER_ALIAS_1, RAID_TITLE_1, IDENTIFIER_ASSOCIATED_1);
  private static final RaIDReferenceDTO RAID_ASSOCIATED_2 =
      new RaIDReferenceDTO(SERVER_ALIAS_1, RAID_TITLE_2, IDENTIFIER_ASSOCIATED_2);

  @Autowired private RaIDController raidController;

  @Mock private RaIDServiceClientAdapter mockedRaidClientAdapter;
  @Mock private UserConnectionManager mockedUserConnectionManager;

  @Autowired private RaIDServiceManager raidServiceManager;

  private ObjectMapper mapper = new ObjectMapper();
  private User piUser;
  private Set<RaIDReferenceDTO> configuredRaidList1;
  private Set<RaIDReferenceDTO> configuredRaidList2;
  private RaIDReferenceDTO alreadyAssociatedRaidForServerAlias1;
  private CreateCloudGroup projectGroupCreationWithRaid;
  private CreateCloudGroup projectGroupCreationWithoutRaid;
  private Long newProjectGroupId;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.openMocks(this);
    this.piUser = createAndSaveUser("pi" + getRandomName(10), Constants.PI_ROLE);
    initUser(this.piUser);
    logoutAndLoginAs(piUser);
    raidController.setRaidServiceClientAdapter(mockedRaidClientAdapter);
    raidController.setUserConnection(mockedUserConnectionManager);

    projectGroupCreationWithRaid = new CreateCloudGroup();
    projectGroupCreationWithRaid.setGroupName("ProjectGroupWithRaid");
    projectGroupCreationWithRaid.setSessionUser(piUser);
    projectGroupCreationWithRaid.setRaid(EXPECTED_RAID_ASSOCIATED_1);
    projectGroupCreationWithRaid.setPiEmail(piUser.getEmail());

    projectGroupCreationWithoutRaid = new CreateCloudGroup();
    projectGroupCreationWithoutRaid.setGroupName("ProjectGroupWithoutRaid");
    projectGroupCreationWithoutRaid.setSessionUser(piUser);
    projectGroupCreationWithoutRaid.setRaid(null);
    projectGroupCreationWithoutRaid.setPiEmail(piUser.getEmail());
  }

  @After
  public void teardown() throws Exception {
    grpMgr.removeGroup(newProjectGroupId, piUser);
  }

  @Test
  public void testGetRaidListByUser() throws Exception {
    // GIVEN
    Map<String, RaIDServerConfigurationDTO> serverByAlias =
        Map.of(
            SERVER_ALIAS_1,
            new RaIDServerConfigurationDTO(SERVER_ALIAS_1, API_BASE_URL_1, AUTH_BASE_URL_1),
            SERVER_ALIAS_2,
            new RaIDServerConfigurationDTO(SERVER_ALIAS_2, API_BASE_URL_2, AUTH_BASE_URL_2));
    when(mockedRaidClientAdapter.getServerMapByAlias()).thenReturn(serverByAlias);

    alreadyAssociatedRaidForServerAlias1 =
        new RaIDReferenceDTO(
            SERVER_ALIAS_1,
            RAID_TITLE_1,
            mapper
                .readValue(
                    IOUtils.resourceToString(
                        "/TestResources/raid/json/raid-test-1.json", Charset.defaultCharset()),
                    RaID.class)
                .getIdentifier()
                .getId());

    MvcResult result =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationWithRaid))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    newProjectGroupId = extractProjectGroupId(result);

    configuredRaidList1 =
        Arrays.stream(
                mapper.readValue(
                    IOUtils.resourceToString(
                        "/TestResources/raid/json/raid-list.json", Charset.defaultCharset()),
                    RaID[].class))
            .map(
                r ->
                    new RaIDReferenceDTO(
                        SERVER_ALIAS_1, r.getTitle().get(0).getText(), r.getIdentifier().getId()))
            .collect(Collectors.toSet());
    when(mockedRaidClientAdapter.getRaIDList(piUser.getUsername(), SERVER_ALIAS_1))
        .thenReturn(configuredRaidList1);

    configuredRaidList2 =
        Arrays.stream(
                mapper.readValue(
                    IOUtils.resourceToString(
                        "/TestResources/raid/json/raid-list-2.json", Charset.defaultCharset()),
                    RaID[].class))
            .map(
                r ->
                    new RaIDReferenceDTO(
                        SERVER_ALIAS_2, r.getTitle().get(0).getText(), r.getIdentifier().getId()))
            .collect(Collectors.toSet());
    when(mockedRaidClientAdapter.getRaIDList(piUser.getUsername(), SERVER_ALIAS_2))
        .thenReturn(configuredRaidList2);

    // though only SERVER_ALIAS_1 has been connected to the account
    when(mockedUserConnectionManager.findByUserNameProviderName(
            piUser.getUsername(), RAID_APP_NAME, SERVER_ALIAS_1))
        .thenReturn(Optional.of(new UserConnection()));
    when(mockedUserConnectionManager.findByUserNameProviderName(
            piUser.getUsername(), RAID_APP_NAME, SERVER_ALIAS_2))
        .thenReturn(Optional.empty());

    // WHEN
    result =
        mockMvc
            .perform(get("/apps/raid").principal(piUser::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    Set<RaIDReferenceDTO> actualResult = extractRaidListByUser(result);

    // THEN
    Set<RaIDReferenceDTO> expectedResult = new HashSet<>();
    expectedResult.addAll(configuredRaidList1);
    expectedResult.remove(alreadyAssociatedRaidForServerAlias1);

    assertEquals(expectedResult, actualResult);
  }

  @Test
  public void testGetAssociateRaid() throws Exception {
    // GIVEN a RaID already associated to a project group
    MvcResult result =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationWithRaid))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    newProjectGroupId = extractProjectGroupId(result);

    // WHEN get the associated raid to this specific project
    result =
        mockMvc
            .perform(
                get("/apps/raid/" + SERVER_ALIAS_1 + "/projects/" + newProjectGroupId)
                    .principal(piUser::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    RaidGroupAssociationDTO actualResult =
        extractRaid(projectGroupCreationWithRaid.getGroupName(), result);

    assertNotNull(actualResult);
    assertEquals(newProjectGroupId, actualResult.getProjectGroupId());
    assertEquals(EXPECTED_RAID_ASSOCIATED_1, actualResult.getRaid());
  }

  @Test
  public void testGetRaidByFolderId() throws Exception {
    // GIVEN a RaID already associated to a project group
    MvcResult result =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationWithRaid))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    newProjectGroupId = extractProjectGroupId(result);

    Group newGroup = grpMgr.getGroup(newProjectGroupId);
    Folder projectGroupFolder = folderMgr.getFolder(newGroup.getCommunalGroupFolderId(), piUser);
    Folder workspaceRootFolder = piUser.getRootFolder();

    // WHEN get the associated raid to the project group folder ID
    result =
        mockMvc
            .perform(
                get("/apps/raid/byFolder/" + projectGroupFolder.getId())
                    .principal(piUser::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    RaidGroupAssociationDTO actualResult =
        extractRaid(projectGroupCreationWithRaid.getGroupName(), result);

    // THEN a RaID is returned
    assertNotNull(actualResult);
    assertEquals(newProjectGroupId, actualResult.getProjectGroupId());
    assertEquals(EXPECTED_RAID_ASSOCIATED_1, actualResult.getRaid());

    // WHEN get the associated raid to the workspace root folder ID
    result =
        mockMvc
            .perform(
                get("/apps/raid/byFolder/" + workspaceRootFolder.getId())
                    .principal(piUser::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    // THEN no RaID is returned
    assertTrue(
        result
            .getResponse()
            .getContentAsString()
            .contains("Not able to get RaID associated to the project group with folder ID"));
  }

  @Test
  public void testSuccessfullyAssociateRaidToGroup() throws Exception {
    // GIVEN
    Map<String, RaIDServerConfigurationDTO> serverByAlias =
        Map.of(
            SERVER_ALIAS_1,
            new RaIDServerConfigurationDTO(SERVER_ALIAS_1, API_BASE_URL_1, AUTH_BASE_URL_1));
    when(mockedRaidClientAdapter.getServerMapByAlias()).thenReturn(serverByAlias);
    when(mockedUserConnectionManager.findByUserNameProviderName(
            piUser.getUsername(), RAID_APP_NAME, SERVER_ALIAS_1))
        .thenReturn(Optional.of(new UserConnection()));
    when(mockedRaidClientAdapter.getRaIDList(piUser.getUsername(), SERVER_ALIAS_1))
        .thenReturn(new HashSet<>(Set.of(EXPECTED_RAID_ASSOCIATED_1)));

    // GIVEN a Raid was NOT associated to a group
    MvcResult result =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationWithoutRaid))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    newProjectGroupId = extractProjectGroupId(result);

    Group actualProjectGroup = grpMgr.getGroup(newProjectGroupId);
    assertNull(actualProjectGroup.getRaid());

    // WHEN
    RaidGroupAssociationDTO raidToGroupAssociation =
        new RaidGroupAssociationDTO(
            newProjectGroupId,
            projectGroupCreationWithoutRaid.getGroupName(),
            new RaIDReferenceDTO(
                EXPECTED_RAID_ASSOCIATED_1.getRaidServerAlias(),
                EXPECTED_RAID_ASSOCIATED_1.getRaidTitle(),
                EXPECTED_RAID_ASSOCIATED_1.getRaidIdentifier()));
    raidToGroupAssociation.getRaid().setRaidTitle(null);

    result =
        mockMvc
            .perform(
                post("/apps/raid/associate", newProjectGroupId)
                    .principal(piUser::getUsername)
                    .content(JacksonUtil.toJson(raidToGroupAssociation))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    // THEN
    actualProjectGroup = grpMgr.getGroup(newProjectGroupId);
    assertNotNull(actualProjectGroup.getRaid());

    UserRaid expectedCreatedUserRaid =
        raidServiceManager.getUserRaid(actualProjectGroup.getRaid().getId());
    assertEquals(expectedCreatedUserRaid, actualProjectGroup.getRaid());
    assertEquals(EXPECTED_RAID_ASSOCIATED_1.getRaidTitle(), expectedCreatedUserRaid.getRaidTitle());
  }

  @Test
  public void testFailureAssociatingRaidToGroup() throws Exception {
    // GIVEN
    Map<String, RaIDServerConfigurationDTO> serverByAlias =
        Map.of(
            SERVER_ALIAS_1,
            new RaIDServerConfigurationDTO(SERVER_ALIAS_1, API_BASE_URL_1, AUTH_BASE_URL_1));
    when(mockedRaidClientAdapter.getServerMapByAlias()).thenReturn(serverByAlias);
    when(mockedUserConnectionManager.findByUserNameProviderName(
            piUser.getUsername(), RAID_APP_NAME, SERVER_ALIAS_1))
        .thenReturn(Optional.of(new UserConnection()));
    when(mockedRaidClientAdapter.getRaIDList(piUser.getUsername(), SERVER_ALIAS_1))
        .thenReturn(new HashSet<>(Set.of(EXPECTED_RAID_ASSOCIATED_1)));

    // GIVEN a Raid was NOT associated to a group
    MvcResult result =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationWithoutRaid))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    newProjectGroupId = extractProjectGroupId(result);

    Group expectedProjectGroup = grpMgr.getGroup(newProjectGroupId);
    assertNull(expectedProjectGroup.getRaid());

    // WHEN
    RaidGroupAssociationDTO raidToGroupAssociation =
        new RaidGroupAssociationDTO(
            newProjectGroupId, projectGroupCreationWithoutRaid.getGroupName(), RAID_ASSOCIATED_2);
    result =
        mockMvc
            .perform(
                post("/apps/raid/associate", newProjectGroupId)
                    .principal(piUser::getUsername)
                    .content(JacksonUtil.toJson(raidToGroupAssociation))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    // THEN
    assertTrue(
        extractErrorMessage(result)
            .contains(
                "Not able to associate RaID to group: "
                    + "The RaID \""
                    + IDENTIFIER_ASSOCIATED_2
                    + "\" is not currently available "
                    + "on the system to be associated"));
    expectedProjectGroup = grpMgr.getGroup(newProjectGroupId);
    assertNull(expectedProjectGroup.getRaid());
  }

  @Test
  public void testDisassociateRaidFromGroup() throws Exception {
    // GIVEN a Raid was associated to a group
    MvcResult result =
        mockMvc
            .perform(
                post("/projectGroup/createProjectGroup")
                    .content(JacksonUtil.toJson(projectGroupCreationWithRaid))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    newProjectGroupId = extractProjectGroupId(result);

    Group expectedProjectGroup = grpMgr.getGroup(newProjectGroupId);
    assertNotNull(expectedProjectGroup.getRaid());
    UserRaid expectedCreatedUserRaid =
        raidServiceManager.getUserRaid(expectedProjectGroup.getRaid().getId());
    assertEquals(expectedCreatedUserRaid, expectedProjectGroup.getRaid());

    // WHEN the user disassociates the Raid
    result =
        mockMvc
            .perform(post("/apps/raid/disassociate/{projectGroupId}", newProjectGroupId))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    // THEN
    expectedProjectGroup.setRaid(null);
    assertEquals(expectedProjectGroup, grpMgr.getGroup(newProjectGroupId));
    assertExceptionThrown(
        () -> raidServiceManager.getUserRaid(expectedCreatedUserRaid.getId()),
        ObjectRetrievalFailureException.class);
  }

  @NotNull
  private Set<RaIDReferenceDTO> extractRaidListByUser(MvcResult result)
      throws JsonProcessingException, UnsupportedEncodingException {
    Map mapResult = mapper.readValue(result.getResponse().getContentAsString(), Map.class);
    assertNull((mapResult.get("error")));
    assertTrue((boolean) mapResult.get("success"));
    return ((List<Map<String, String>>) mapResult.get("data"))
        .stream()
            .map(
                el ->
                    new RaIDReferenceDTO(
                        el.get("raidServerAlias"), el.get("raidTitle"), el.get("raidIdentifier")))
            .collect(Collectors.toSet());
  }

  @NotNull
  private String extractErrorMessage(MvcResult result)
      throws JsonProcessingException, UnsupportedEncodingException {
    Map mapResult = mapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return ((Map<String, List<String>>) mapResult.get("error")).get("errorMessages").get(0);
  }

  @NotNull
  private RaidGroupAssociationDTO extractRaid(String groupName, MvcResult result)
      throws JsonProcessingException, UnsupportedEncodingException {
    Map mapResult = mapper.readValue(result.getResponse().getContentAsString(), Map.class);
    assertNull((mapResult.get("error")));
    assertTrue((boolean) mapResult.get("success"));
    Map<String, Map<String, String>> groupAssociation =
        (Map<String, Map<String, String>>) mapResult.get("data");
    return new RaidGroupAssociationDTO(
        Long.valueOf(((Map<String, Integer>) mapResult.get("data")).get("projectGroupId")),
        groupName,
        new RaIDReferenceDTO(
            groupAssociation.get("raid").get("raidServerAlias"),
            groupAssociation.get("raid").get("raidTitle"),
            groupAssociation.get("raid").get("raidIdentifier")));
  }

  @NotNull
  private Long extractProjectGroupId(MvcResult result)
      throws JsonProcessingException, UnsupportedEncodingException {
    Map mapResult = mapper.readValue(result.getResponse().getContentAsString(), Map.class);
    assertNull((mapResult.get("error")));
    assertTrue((boolean) mapResult.get("success"));
    return Long.valueOf(((Map<String, String>) mapResult.get("data")).get("newGroup"));
  }
}
