package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerInfo.ApiContainerGridLayoutConfig;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiContainerLocationWithContent;
import com.researchspace.api.v1.model.ApiContainerSearchResult;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MvcResult;

public class ContainersApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void retrieveDefaultDevRunContainers() throws Exception {

    User anyUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithInitialisedContent(anyUser);
    String apiKey = createNewApiKeyForUser(anyUser);

    // no pagination
    MvcResult result = retrieveTopContainers(anyUser, apiKey, false);
    assertNull(result.getResolvedException());
    ApiContainerSearchResult topContainers =
        getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertNotNull(topContainers);
    assertEquals(2, topContainers.getTotalHits().intValue());
    assertEquals(2, topContainers.getContainers().size());
    assertEquals(1, topContainers.getLinks().size());

    ApiContainerInfo listContainerInfo = topContainers.getContainers().get(1);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        listContainerInfo.getName());
    assertEquals(3, listContainerInfo.getContentSummary().getTotalCount());
    assertEquals(0, listContainerInfo.getContentSummary().getSubSampleCount());
    assertEquals(3, listContainerInfo.getContentSummary().getContainerCount());
    assertEquals(anyUser.getFullName(), listContainerInfo.getModifiedByFullName());
    assertEquals(1, listContainerInfo.getLinks().size());

    // retrieve list container, with details
    result =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, listContainerInfo.getId(), true))
            .andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    ApiContainer retrievedContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(retrievedContainer);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        retrievedContainer.getName());
    assertEquals(3, retrievedContainer.getContentSummary().getTotalCount());
    assertEquals(3, retrievedContainer.getLocations().size());
    assertEquals(3, retrievedContainer.getStoredContent().size());
    assertEquals(1, retrievedContainer.getLinks().size());
    assertEquals(0, retrievedContainer.getParentContainers().size());
    ApiInventoryRecordInfo retrievedSubcontainerInfo =
        retrievedContainer.getLocations().get(0).getContent();
    assertEquals("box #1 (list container)", retrievedSubcontainerInfo.getName());
    assertEquals(
        "24-well plate (6x4 grid)",
        retrievedContainer.getLocations().get(1).getContent().getName());
    assertEquals(
        "96-well plate (12x8 grid)",
        retrievedContainer.getLocations().get(2).getContent().getName());

    // without content
    result =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, listContainerInfo.getId(), false))
            .andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    retrievedContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(retrievedContainer);
    assertNull(retrievedContainer.getLocations());
    assertNull(retrievedContainer.getStoredContent());

    // retrieve subcontainer, with details
    result =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, retrievedSubcontainerInfo.getId(), true))
            .andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    ApiContainer retrievedSubContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(retrievedSubContainer);
    assertEquals("box #1 (list container)", retrievedSubContainer.getName());
    assertEquals(4, retrievedSubContainer.getContentSummary().getTotalCount());
    assertEquals(3, retrievedSubContainer.getContentSummary().getContainerCount());
    assertEquals(1, retrievedSubContainer.getContentSummary().getSubSampleCount());
    assertEquals(4, retrievedSubContainer.getLocations().size());
    assertNotNull(retrievedSubContainer.getParentContainer());
    assertEquals(1, retrievedSubContainer.getParentContainers().size());
    assertEquals(anyUser.getFullName(), retrievedSubContainer.getModifiedByFullName());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        retrievedSubContainer.getParentContainer().getName());
    assertNotNull(retrievedSubContainer.getParentLocation());
    assertNotNull(retrievedSubContainer.getParentLocation().getId());

    ApiInventoryRecordInfo retrievedSubSubContainerInfo =
        retrievedSubContainer.getLocations().get(0).getContent();
    assertEquals("box A (list container)", retrievedSubSubContainerInfo.getName());
    assertEquals(anyUser.getFullName(), retrievedSubSubContainerInfo.getModifiedByFullName());
    ApiInventoryRecordInfo retrievedSubSampleInfo =
        retrievedSubContainer.getLocations().get(3).getContent();
    assertEquals("Basic Sample.01", retrievedSubSampleInfo.getName());
    assertEquals(anyUser.getFullName(), retrievedSubSampleInfo.getModifiedByFullName());

    // retrieve subsubcontainer, with details
    result =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, retrievedSubSubContainerInfo.getId(), true))
            .andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    ApiContainer retrievedSubSubContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(retrievedSubSubContainer);
    assertEquals("box A (list container)", retrievedSubSubContainer.getName());
    assertEquals(2, retrievedSubSubContainer.getParentContainers().size());
    assertEquals(
        "box #1 (list container)", retrievedSubSubContainer.getParentContainers().get(0).getName());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        retrievedSubSubContainer.getParentContainers().get(1).getName());

    // retrieve image container, with details
    ApiContainerInfo imageContainer = topContainers.getContainers().get(0);
    result =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, imageContainer.getId(), true))
            .andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    ApiContainer retrievedSecondContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(retrievedSecondContainer);
    assertEquals(
        4, retrievedSecondContainer.getLinks().size()); // self/image/thumbnail/locationsImg links
    assertEquals(4, retrievedSecondContainer.getLocations().size()); // 4 pre-defined locations
    assertNull(retrievedSecondContainer.getLocations().get(0).getContent()); // locations empty
    assertEquals(1, retrievedSecondContainer.getAttachments().size());
    ApiInventoryFile imageContainerAttachment = retrievedSecondContainer.getAttachments().get(0);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_ATTACHMENT_NAME,
        imageContainerAttachment.getName());

    // retrieve images saved/created for image container
    result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/{id}/image/any",
                    anyUser,
                    imageContainer.getId()))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(102469, result.getResponse().getContentAsByteArray().length);

    result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/{id}/thumbnail/any",
                    anyUser,
                    imageContainer.getId()))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(3177, result.getResponse().getContentAsByteArray().length);

    result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/{id}/locationsImage/any",
                    anyUser,
                    imageContainer.getId()))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(168434, result.getResponse().getContentAsByteArray().length);

    // retrieve attachment
    result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/files/{id}/file",
                    anyUser,
                    imageContainerAttachment.getId()))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(47, result.getResponse().getContentAsByteArray().length);
  }

  @Test
  public void createContainerWithImageAndTags() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    String post =
        "{ \"name\": \"Simplest"
            + " Container\",\"tags\":[{\"value\":\"aTagValue\",\"uri\":\"uriValue\",\"ontologyName\":\"ontName\",\"ontologyVersion\":1}],\"cType\":"
            + " \"LIST\", \"newBase64Image\":\""
            + BASE_64
            + "\" }";
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/containers", anyUser, post))
            .andExpect(status().isCreated())
            .andReturn();
    ApiContainer defaultContainer = getFromJsonResponseBody(result, ApiContainer.class);
    Container entity = containerApiMgr.getContainerById(defaultContainer.getId(), anyUser);
    assertEquals("aTagValue", entity.getTags());
    assertEquals(
        "aTagValue__RSP_EXTONT_URL_DELIM__uriValue__RSP_EXTONT_NAME_DELIM__ontName__RSP_EXTONT_VERSION_DELIM__1",
        entity.getTagMetaData());
    assertNotNull(entity.getImageFileProperty());
    assertNotNull(entity.getThumbnailFileProperty());
    assertNull(entity.getLocationsImageFileProperty());
  }

  @Test
  public void createRetrieveEditContainer() throws Exception {
    Mockito.reset(auditer);

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    String simplestContainerJSON = "{ \"name\": \"Simplest Container\", \"cType\": \"LIST\" }";
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/containers", anyUser, simplestContainerJSON))
            .andExpect(status().isCreated())
            .andReturn();
    ApiContainer defaultContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(defaultContainer.getId());
    assertEquals("Simplest Container", defaultContainer.getName());
    assertNull(defaultContainer.getGridLayout());
    assertEquals(0, defaultContainer.getLocations().size());
    assertEquals(ContainerType.LIST.name(), defaultContainer.getCType());
    verifyAuditAction(AuditAction.CREATE, 1);

    // retrieve created container by separate GET
    result =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, defaultContainer.getId(), true))
            .andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    ApiContainer retrievedContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertEquals(defaultContainer, retrievedContainer);
    verifyAuditAction(AuditAction.READ, 1);

    // create image container, with extra field and two locations
    String customContainerJSON =
        "{ \"name\": \"My Container\",  \"cType\": \"IMAGE\","
            + " \"newBase64LocationsImage\":\"data:image/jpeg;base64,dummy123\", \"extraFields\":"
            + " [{ \"name\": \"extraFieldName\", \"type\" : \"text\", \"content\" : \"extra text"
            + " content\" }],  \"locations\": [ { \"coordX\": 1, \"coordY\": 1 }, { \"coordX\": 1,"
            + " \"coordY\": 2} ] }";
    result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/containers", anyUser, customContainerJSON))
            .andExpect(status().isCreated())
            .andReturn();
    ApiContainer customContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(customContainer.getId());
    assertEquals("My Container", customContainer.getName());
    assertEquals(1, customContainer.getExtraFields().size());
    assertEquals(2, customContainer.getLocations().size());
    assertEquals(ContainerType.IMAGE.name(), customContainer.getCType());
    ApiContainerLocation firstLocation = customContainer.getLocations().get(0);
    ApiContainerLocation secondLocation = customContainer.getLocations().get(1);
    verifyAuditAction(AuditAction.CREATE, 2);

    // update container name and add/edit/remove locations
    String updateJson =
        String.format(
            "{ \"name\" : \"newContainerName\", \"locations\": [ "
                + "{ \"coordX\": 1, \"coordY\": 4, \"newLocationRequest\": true }, "
                + "{ \"id\": "
                + firstLocation.getId()
                + ", \"deleteLocationRequest\" : true }, "
                + "{ \"id\": "
                + secondLocation.getId()
                + ", \"coordX\": 1, \"coordY\" : 3} ] }");
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/containers/" + customContainer.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiContainer editedContainer = getFromJsonResponseBody(editResult, ApiContainer.class);
    assertNotNull(editedContainer);
    assertEquals("newContainerName", editedContainer.getName());
    assertEquals(2, editedContainer.getLocations().size());
    assertEquals(3, editedContainer.getLocations().get(0).getCoordY()); // edited one
    assertEquals(4, editedContainer.getLocations().get(1).getCoordY()); // newly created one
    verifyAuditAction(AuditAction.WRITE, 1);

    result =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, customContainer.getId(), true))
            .andReturn();
    retrievedContainer = getFromJsonResponseBody(editResult, ApiContainer.class);
    assertEquals(editedContainer, retrievedContainer);
    verifyAuditAction(AuditAction.READ, 2);

    Mockito.verifyNoMoreInteractions(auditer);
  }

  @Test
  public void createEditGridContainer() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    String simplestContainerJSON =
        "{ \"name\": \"Grid Container\", \"cType\": \"GRID\",\"gridLayout\": { \"columnsNumber\":"
            + " 3, \"rowsNumber\": 2 } }";
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/containers", anyUser, simplestContainerJSON))
            .andExpect(status().isCreated())
            .andReturn();
    ApiContainer gridContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(gridContainer.getId());
    assertEquals(ContainerType.GRID.name(), gridContainer.getCType());
    assertEquals("Grid Container", gridContainer.getName());
    assertNotNull(gridContainer.getGridLayout());
    assertEquals(3, gridContainer.getGridLayout().getColumnsNumber());
    assertEquals(2, gridContainer.getGridLayout().getRowsNumber());
    assertEquals(0, gridContainer.getLocations().size()); // no locations created by default
  }

  @Test
  public void containerCreationErrors() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    // no name
    String emptyNameJSON = "{ }";
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/containers", anyUser, emptyNameJSON))
            .andExpect(status().isBadRequest())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "name is a required field");

    // set as grid, but no grid configuration
    String wrongType = "{\"name\": \"Container\",\"cType\": \"GRID\" }";
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/containers", anyUser, wrongType))
            .andExpect(status().isBadRequest())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "Grid layout must be set");

    // container that cannot store anything
    String noValidContentContainerJSON =
        "{ \"name\": \"Container\",\"cType\": \"LIST\", \"canStoreContainers\": false,"
            + " \"canStoreSamples\": false }";
    result = postCreateContainerExpecting4xx(anyUser, apiKey, noValidContentContainerJSON);
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(
        error, "'canStoreSamples' and 'canStoreContainers' flags cannot be both set to 'false'");

    // incomplete grid layout params
    String gridContainerJSON =
        "{ \"name\": \"Grid Container\",\"cType\": \"GRID\", \"gridLayout\": { \"rowsNumber\": 2 }"
            + " }";
    result = postCreateContainerExpecting4xx(anyUser, apiKey, gridContainerJSON);
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(
        error, "Grid layout has to specify both columnsNumber and rowsNumber");

    String gridContainerJSONFtm =
        "{\"cType\": \"GRID\", \"name\": \"Grid Container\", \"gridLayout\": { \"columnsNumber\":"
            + " %d, \"rowsNumber\": %d } }";
    String gridContainerWrongSizeFtm =
        "Provided grid size %dx%d is incorrect, must be between 1x1 and 24x24.";
    // incorrect grid layout params - too large
    gridContainerJSON = String.format(gridContainerJSONFtm, 25, 2);
    result = postCreateContainerExpecting4xx(anyUser, apiKey, gridContainerJSON);
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, String.format(gridContainerWrongSizeFtm, 25, 2));

    // incorrect grid layout params - too large
    gridContainerJSON = String.format(gridContainerJSONFtm, 0, 0);
    result = postCreateContainerExpecting4xx(anyUser, apiKey, gridContainerJSON);
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, String.format(gridContainerWrongSizeFtm, 0, 0));

    // location outside of grid dimensions
    gridContainerJSON =
        "{ \"name\": \"Grid Container\", \"gridLayout\": { \"columnsNumber\": 3, \"rowsNumber\": 2"
            + " }, \"locations\": [ { \"coordX\": 1, \"coordY\": 5 } ],\"cType\": \"GRID\" }";
    result = postCreateContainerExpecting4xx(anyUser, apiKey, gridContainerJSON);
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(
        error, "Location (1,5) is outside container grid dimensions (columns: 3, rows: 2)");

    // create container with a subcontainer
    ApiContainer imageContainer = createBasicImageContainerForUser(anyUser);
    ApiContainerLocation imageLocation = imageContainer.getLocations().get(0);
    ApiContainer subContainer = new ApiContainer();
    subContainer.setName("mySubContainer");
    subContainer.setParentLocation(imageLocation);
    ApiContainer createdSubContainer = containerApiMgr.createNewApiContainer(subContainer, anyUser);
    Long locationId = createdSubContainer.getParentLocation().getId();

    // try creating another container and put it into the same parent location
    String simpleContainerJSON =
        "{\"cType\": \"IMAGE\", \"name\": \"Simplest Container\", \"parentLocation\": "
            + "{ \"id\": "
            + locationId
            + " } }";
    result = postCreateContainerExpecting4xx(anyUser, apiKey, simpleContainerJSON);
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(
        error, "Location: " + locationId + " is already taken by the record: IC");
  }

  private MvcResult postCreateContainerExpecting4xx(
      User anyUser, String apiKey, String containerJSON) throws Exception {
    MvcResult result;
    result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/containers", anyUser, containerJSON))
            .andExpect(status().is4xxClientError())
            .andReturn();
    return result;
  }

  @Test
  public void containerUpdateErrors() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    // create grid container
    ApiContainer gridContainer = createBasicContainerForUser(anyUser);
    gridContainer.setGridLayout(new ApiContainerGridLayoutConfig(2, 3));
    containerApiMgr.updateApiContainer(gridContainer, anyUser);

    // try renaming with too long name
    String updateJson = String.format("{ \"name\": \"" + StringUtils.repeat("*", 256) + "\" }");
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/containers/" + gridContainer.getId(), anyUser, updateJson))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(error, "Name cannot be longer than 255 characters");

    // create basic subsample located in the container
    ApiSampleWithFullSubSamples basicSample = new ApiSampleWithFullSubSamples();
    ApiSubSample basicSubSample1 = new ApiSubSample();
    basicSubSample1.setParentContainer(gridContainer);
    basicSubSample1.setParentLocation(new ApiContainerLocation(2, 2));
    basicSample.getSubSamples().add(basicSubSample1);
    ApiSubSample basicSubSample2 = new ApiSubSample();
    basicSample.getSubSamples().add(basicSubSample2);
    sampleApiMgr.createNewApiSample(basicSample, anyUser);

    // retrieve container and find location
    MvcResult retrieveResult =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, gridContainer.getId(), true))
            .andReturn();
    ApiContainer apiContainer = getFromJsonResponseBody(retrieveResult, ApiContainer.class);
    assertNotNull(apiContainer);
    assertEquals(1, apiContainer.getContentSummary().getTotalCount());
    assertEquals(1, apiContainer.getLocations().size());
    ApiContainerLocationWithContent locationWithSubSample = apiContainer.getLocations().get(0);
    assertNotNull(locationWithSubSample.getContent());

    // try deleting container's location that has a content
    updateJson =
        String.format(
            "{ \"locations\": [ { \"id\": "
                + locationWithSubSample.getId()
                + ", \"deleteLocationRequest\" : true } ] }");
    editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/containers/" + gridContainer.getId(), anyUser, updateJson))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(error, "Move the record before trying to delete the location");

    // try modify location coordinates to be outside container's grid dimensions
    updateJson =
        String.format(
            "{ \"locations\": [ { \"id\": "
                + locationWithSubSample.getId()
                + ", \"coordX\": 3, \"coordY\": 3 } ] }");
    editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/containers/" + gridContainer.getId(), anyUser, updateJson))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(
        error, "Location (3,3) is outside container grid dimensions (columns: 2, rows: 3)");
  }

  @Test
  public void moveContainerToAnotherContainer() throws Exception {
    Mockito.reset(auditer);

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    // create top containers, explicitly rename two of them
    ApiContainer containerA = createBasicContainerForUser(anyUser);
    containerA.setName("container A");
    containerA.setRemoveFromParentContainerRequest(true);
    containerApiMgr.updateApiContainer(containerA, anyUser);
    ApiContainer containerB = createBasicContainerForUser(anyUser);
    containerB.setName("container B");
    containerB.setRemoveFromParentContainerRequest(true);
    containerApiMgr.updateApiContainer(containerB, anyUser);
    ApiContainer movingContainer = createBasicContainerForUser(anyUser);
    verifyAuditAction(AuditAction.CREATE, 3);
    verifyAuditAction(AuditAction.WRITE, 2);

    // check top containers, should two top containers
    MvcResult result = retrieveTopContainers(anyUser, apiKey, false);
    assertNull(result.getResolvedException());
    ApiContainerSearchResult topContainers =
        getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertNotNull(topContainers);
    assertEquals(2, topContainers.getTotalHits().intValue());

    // put movingContainer into containerA
    String updateJson =
        String.format("{ \"parentContainers\": [ { \"id\": " + containerA.getId() + " } ] }");
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/containers/" + movingContainer.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    movingContainer = getFromJsonResponseBody(editResult, ApiContainer.class);
    assertNotNull(movingContainer);
    assertEquals(1, movingContainer.getParentContainers().size());
    assertEquals(containerA.getId(), movingContainer.getParentContainer().getId());
    verifyAuditAction(AuditAction.MOVE, 3);

    // check top containers, should be just two
    result = retrieveTopContainers(anyUser, apiKey, false);
    topContainers = getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertEquals(2, topContainers.getTotalHits().intValue());
    assertEquals("container A", topContainers.getContainers().get(0).getName());
    assertEquals(1, topContainers.getContainers().get(0).getContentSummary().getTotalCount());
    assertEquals("container B", topContainers.getContainers().get(1).getName());
    assertEquals(0, topContainers.getContainers().get(1).getContentSummary().getTotalCount());

    // move into container B
    updateJson =
        String.format("{ \"parentContainers\": [ { \"id\": " + containerB.getId() + " } ] }");
    editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/containers/" + movingContainer.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    movingContainer = getFromJsonResponseBody(editResult, ApiContainer.class);
    assertEquals(containerB.getId(), movingContainer.getParentContainer().getId());
    verifyAuditAction(AuditAction.MOVE, 4);

    // check top containers again, content counts should be updated
    result = retrieveTopContainers(anyUser, apiKey, false);
    topContainers = getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertEquals(2, topContainers.getTotalHits().intValue());
    assertEquals("container A", topContainers.getContainers().get(0).getName());
    assertEquals(0, topContainers.getContainers().get(0).getContentSummary().getTotalCount());
    assertEquals("container B", topContainers.getContainers().get(1).getName());
    assertEquals(1, topContainers.getContainers().get(1).getContentSummary().getTotalCount());

    verifyNoMoreInteractions(auditer);
  }

  @Test
  public void deleteContainers() throws Exception {

    // create user with a container and subcontainer
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiContainer workbench = getWorkbenchForUser(anyUser);
    ApiContainer basicContainer = createBasicContainerForUser(anyUser);
    moveContainerToTopLevel(basicContainer, anyUser);

    // move subcontainers inside a container
    ApiContainer basicSubContainer = createBasicContainerForUser(anyUser);
    ApiContainer basicSubContainer2 = createBasicContainerForUser(anyUser);
    ApiContainer containerMoveUpdate = new ApiContainer();
    containerMoveUpdate.setId(basicSubContainer.getId());
    containerMoveUpdate.setParentContainer(basicContainer);
    containerApiMgr.updateApiContainer(containerMoveUpdate, anyUser);
    containerMoveUpdate.setId(basicSubContainer2.getId());
    containerApiMgr.updateApiContainer(containerMoveUpdate, anyUser);

    // container should be returned by top-level container listing
    MvcResult result = retrieveTopContainers(anyUser, apiKey, false);
    ApiContainerSearchResult topContainers =
        getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertNotNull(topContainers);
    assertEquals(1, topContainers.getTotalHits().intValue());
    assertEquals(basicContainer.getId(), topContainers.getContainers().get(0).getId());
    assertEquals(2, topContainers.getContainers().get(0).getContentSummary().getTotalCount());

    // try deleting top container
    result =
        mockMvc
            .perform(
                createBuilderForDelete(apiKey, "/containers/{id}", anyUser, basicContainer.getId()))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "is not empty and cannot be deleted");

    // delete first subcontainer
    mockMvc
        .perform(
            createBuilderForDelete(apiKey, "/containers/{id}", anyUser, basicSubContainer.getId()))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
    result =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, basicSubContainer.getId(), true))
            .andReturn();
    ApiContainer retrievedSubContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(retrievedSubContainer);
    assertTrue(retrievedSubContainer.isDeleted());
    assertNotNull(retrievedSubContainer.getDeletedDate());

    // when subcontainer is deleted, it should be removed from parent container
    assertNull(retrievedSubContainer.getParentContainer());
    // ... and parent container shouldn't list it anymore
    MvcResult retrieveResult =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, basicContainer.getId(), true))
            .andReturn();
    ApiContainer apiContainer = getFromJsonResponseBody(retrieveResult, ApiContainer.class);
    assertNotNull(apiContainer);
    assertEquals(1, apiContainer.getContentSummary().getTotalCount());

    // now move 2nd subcontainer out of the parent container
    String updateJson = String.format("{ \"removeFromParentContainerRequest\": true }");
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/containers/" + basicSubContainer2.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    basicSubContainer2 = getFromJsonResponseBody(editResult, ApiContainer.class);
    assertNotNull(basicSubContainer2);
    assertNull(basicSubContainer2.getParentContainer());

    // verify parent container now empty
    retrieveResult =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, basicContainer.getId(), true))
            .andReturn();
    apiContainer = getFromJsonResponseBody(retrieveResult, ApiContainer.class);
    assertEquals(0, apiContainer.getContentSummary().getTotalCount());
    // verify both containers now listed as top ones
    result = retrieveTopContainers(anyUser, apiKey, false);
    topContainers = getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertNotNull(topContainers);
    assertEquals(2, topContainers.getTotalHits().intValue());

    // try deleting now-empty parent container
    mockMvc
        .perform(
            createBuilderForDelete(apiKey, "/containers/{id}", anyUser, basicContainer.getId()))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    // deleted containers shouldn't be listed on top containers listing
    result = retrieveTopContainers(anyUser, apiKey, false);
    topContainers = getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertNotNull(topContainers);
    assertEquals(1, topContainers.getTotalHits().intValue());
    assertEquals(
        basicSubContainer2.getGlobalId(), topContainers.getContainers().get(0).getGlobalId());

    // but both are listed when 'include deleted' option is used
    result = retrieveTopContainers(anyUser, apiKey, true);
    topContainers = getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertNotNull(topContainers);
    assertEquals(3, topContainers.getTotalHits().intValue());

    // deleted container can be retrieved
    retrieveResult =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, basicContainer.getId(), false))
            .andReturn();
    apiContainer = getFromJsonResponseBody(retrieveResult, ApiContainer.class);
    assertNotNull(apiContainer);
    assertTrue(apiContainer.isDeleted());
    assertNotNull(retrievedSubContainer.getDeletedDate());
    assertEquals(0, apiContainer.getContentSummary().getTotalCount());

    // deleted container cannot be updated
    updateJson =
        "{ \"tags\":[{\"value\":\"woooo\",\"uri\":null,\"ontologyName\":null,\"ontologyVersion\":null}]"
            + " }";
    editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/containers/" + basicContainer.getId(), anyUser, updateJson))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(error, "is deleted");

    // restore the container
    result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/containers/" + basicContainer.getId() + "/restore", anyUser, null))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    apiContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(apiContainer);
    assertFalse(apiContainer.isDeleted());
    assertEquals(workbench.getId(), apiContainer.getParentContainer().getId());
  }

  @Test
  public void duplicate() throws Exception {

    // create user with container
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiContainer container = createBasicContainerForUser(anyUser);

    // copy container
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    apiKey,
                    String.format("/containers/%d/actions/duplicate", container.getId()),
                    anyUser))
            .andExpect(status().isCreated())
            .andReturn();
    ApiContainer copy = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(copy.getId());
  }

  @Test
  public void containerVisibilityWithinGroup() throws Exception {

    // create users and a group
    User pi = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    User user = createAndSaveUser(getRandomName(10));
    User secondUser = createAndSaveUser(getRandomName(10));
    initUsers(pi, user, secondUser);
    createGroupForUsersWithDefaultPi(pi, user);

    String userApiKey = createNewApiKeyForUser(user);
    String secondUserApiKey = createNewApiKeyForUser(secondUser);

    // create a container for each user
    ApiContainer piContainer = createBasicContainerForUser(pi, "pi's container");
    moveContainerToTopLevel(piContainer, pi);
    ApiContainer userContainer = createBasicContainerForUser(user, "user's container");
    moveContainerToTopLevel(userContainer, user);
    ApiContainer secondUserContainer =
        createBasicContainerForUser(secondUser, "second user's container");
    moveContainerToTopLevel(secondUserContainer, secondUser);

    // create sample for a pi, with a subsample inside a container
    ApiSampleWithFullSubSamples newApiSample = new ApiSampleWithFullSubSamples("pi's sample");
    ApiSubSample newSubSample = new ApiSubSample();
    newSubSample.setParentContainer(piContainer);
    newApiSample.getSubSamples().add(newSubSample);
    sampleApiMgr.createNewApiSample(newApiSample, pi);

    // check visibility within a group
    MvcResult result = retrieveTopContainers(user, userApiKey, false);
    assertNull(result.getResolvedException());
    ApiContainerSearchResult topContainers =
        getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertNotNull(topContainers);
    assertEquals(2, topContainers.getTotalHits().intValue());
    assertEquals(2, topContainers.getContainers().size());
    assertEquals("pi's container", topContainers.getContainers().get(0).getName());
    assertEquals("user's container", topContainers.getContainers().get(1).getName());

    // user in group should be able to see details of pi's container, including content
    result =
        this.mockMvc
            .perform(getContainerById(user, userApiKey, piContainer.getId(), true))
            .andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    ApiContainer retrievedPiContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(retrievedPiContainer);
    assertEquals(
        "pi's sample.01", retrievedPiContainer.getLocations().get(0).getContent().getName());

    // user in group can retrieve pi's sample and subsample details
    ApiInventoryRecordInfo subsampleInfo = retrievedPiContainer.getLocations().get(0).getContent();
    result =
        this.mockMvc.perform(getSubSampleById(user, userApiKey, subsampleInfo.getId())).andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    ApiSubSample retrievedPiSubSample = getFromJsonResponseBody(result, ApiSubSample.class);
    assertEquals("pi's sample.01", retrievedPiSubSample.getName());
    ApiSampleInfo piSampleInfo = retrievedPiSubSample.getSampleInfo();
    result =
        this.mockMvc.perform(getSampleById(user, userApiKey, piSampleInfo.getId())).andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    ApiSample retrievedPiSample = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals("pi's sample", retrievedPiSample.getName());

    // user outside the group see only their container
    result = retrieveTopContainers(secondUser, secondUserApiKey, false);
    assertNull(result.getResolvedException());
    topContainers = getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertNotNull(topContainers);
    assertEquals(1, topContainers.getTotalHits().intValue());
    assertEquals("second user's container", topContainers.getContainers().get(0).getName());
    // user outside the group can only see global details of pi's sample
    result =
        this.mockMvc
            .perform(getSampleById(secondUser, secondUserApiKey, piSampleInfo.getId()))
            .andReturn();
    assertNull(result.getResolvedException());
    retrievedPiSample = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals("pi's sample", retrievedPiSample.getName());
    assertTrue(retrievedPiSample.isClearedForPublicView());
  }

  private MvcResult retrieveTopContainers(User anyUser, String apiKey, boolean includeDeleted)
      throws Exception {
    return this.mockMvc
        .perform(
            createBuilderForGet(API_VERSION.ONE, apiKey, "/containers", anyUser)
                .param("deletedItems", includeDeleted ? "INCLUDE" : null))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  public void changeContainerOwner() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    // create basic sample
    ApiContainer createdContainer = createBasicContainerForUser(anyUser);
    assertNotNull(createdContainer);
    assertEquals(anyUser.getUsername(), createdContainer.getOwner().getUsername());

    // create another user
    User anotherUser = doCreateAndInitUser(getRandomAlphabeticString("another"));

    // container owner transfers container to another user
    String sampleUpdateJson =
        "{ \"owner\": { \"username\": \"" + anotherUser.getUsername() + "\" } } ";
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey,
                    "/containers/" + createdContainer.getId() + "/actions/changeOwner",
                    anyUser,
                    sampleUpdateJson))
            .andReturn();
    assertNull(editResult.getResolvedException());
    ApiContainer editedContainer = mvcUtils.getFromJsonResponseBody(editResult, ApiContainer.class);
    assertNotNull(editedContainer);
    assertEquals(anotherUser.getUsername(), editedContainer.getOwner().getUsername());
  }
}
