package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebAppConfiguration
@TestPropertySource(properties = {"inventory.instrument.enabled=true"})
public class InstrumentsApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void createBasicInstrument() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    String instrumentJson = "{\"name\": \"instrument-1\"}";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/instruments", anyUser, instrumentJson))
            .andExpect(status().isCreated())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument created = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertNotNull(created);
    assertNotNull(created.getId());
    assertEquals("instrument-1", created.getName());
    assertEquals(anyUser.getUsername(), created.getCreatedBy());
    assertEquals(anyUser.getUsername(), created.getModifiedBy());
    assertNotNull(created.getOwner());
    assertEquals(anyUser.getUsername(), created.getOwner().getUsername());
    assertFalse(created.getLinks().isEmpty());
    assertTrue(created.getLinkOfType(ApiLinkItem.SELF_REL).isPresent());
    assertTrue(
        created
            .getLinkOfType(ApiLinkItem.SELF_REL)
            .orElseThrow(() -> new IllegalStateException("missing self link"))
            .getLink()
            .endsWith("/api/inventory/v1/instruments/" + created.getId()));
  }

  @Test
  public void getInstrumentById() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser);

    MvcResult result =
        mockMvc
            .perform(getInstrumentById(anyUser, apiKey, instrument.getId()))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument retrieved = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertNotNull(retrieved);
    assertEquals(instrument.getId(), retrieved.getId());
    assertEquals("myInstrument", retrieved.getName());
    assertEquals(0, retrieved.getFields().size());
    assertEquals(0, retrieved.getExtraFields().size());
    assertFalse(retrieved.isStoredInContainer());
  }

  @Test
  public void getInstrumentErrorMessages() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    User otherUser = createInitAndLoginAnyUser();
    ApiInstrument instrument = createBasicInstrumentForUser(otherUser);

    mockMvc
        .perform(getInstrumentById(anyUser, apiKey, 12345L))
        .andExpect(status().isNotFound())
        .andReturn();

    mockMvc
        .perform(getInstrumentById(anyUser, "WRONG KEY", instrument.getId()))
        .andExpect(status().isUnauthorized())
        .andReturn();
  }

  @Test
  public void verifyNonOwnerCannotModifyInstrument() throws Exception {
    User owner = createInitAndLoginAnyUser();
    String ownerApiKey = createNewApiKeyForUser(owner);
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "owner-instrument");

    User otherUser = createInitAndLoginAnyUser();
    String otherApiKey = createNewApiKeyForUser(otherUser);

    String updateJson = "{ \"name\": \"new hacked name\" }";

    // Update, Delete, duplicate, change-ownership cannot be done by non-owner → 404
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                otherApiKey, "/instruments/" + instrument.getId(), otherUser, updateJson))
        .andExpect(status().isNotFound())
        .andReturn();

    mockMvc
        .perform(
            createBuilderForDelete(otherApiKey, "/instruments/" + instrument.getId(), otherUser))
        .andExpect(status().isNotFound())
        .andReturn();

    String changeOwnerJson = "{ \"owner\": { \"username\": \"" + otherUser.getUsername() + "\" } }";
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                otherApiKey,
                "/instruments/" + instrument.getId() + "/actions/changeOwner",
                otherUser,
                changeOwnerJson))
        .andExpect(status().isNotFound())
        .andReturn();

    mockMvc
        .perform(
            createBuilderForPost(
                API_VERSION.ONE,
                otherApiKey,
                "/instruments/" + instrument.getId() + "/actions/duplicate",
                otherUser))
        .andExpect(status().isNotFound())
        .andReturn();

    // verify the instrument is untouched for the owner
    MvcResult ownerResult =
        mockMvc
            .perform(getInstrumentById(owner, ownerApiKey, instrument.getId()))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrument unchanged = mvcUtils.getFromJsonResponseBody(ownerResult, ApiInstrument.class);
    assertEquals("owner-instrument", unchanged.getName());
    assertFalse(unchanged.isDeleted());
  }

  @Test
  public void createInstrumentErrors() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/instruments", anyUser, "{}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "name is a required field");

    String tooLongName = StringUtils.leftPad("test", 256, '*');
    String invalidJson = "{ \"name\": \"" + tooLongName + "\" }";
    result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/instruments", anyUser, invalidJson))
            .andExpect(status().isBadRequest())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "Name cannot be longer than");

    result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instruments", anyUser, "{\"name\": \"instrument-ok\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    ApiInstrument created = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertEquals("instrument-ok", created.getName());
  }

  @Test
  public void createInstrumentWithNewTargetLocationRequest() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiContainer gridContainer = createBasicGridContainerForUser(anyUser, 2, 2);

    String instrumentJson =
        "{ \"name\": \"instrument-in-grid\", \"newTargetLocation\": { \"containerId\": "
            + gridContainer.getId()
            + ", \"location\": { \"coordX\": 1, \"coordY\": 2 } } }";

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/instruments", anyUser, instrumentJson))
            .andExpect(status().isCreated())
            .andReturn();

    ApiInstrument created = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertNotNull(created);
    assertNotNull(created.getId());
    assertEquals("instrument-in-grid", created.getName());

    MvcResult getResult =
        mockMvc
            .perform(getInstrumentById(anyUser, apiKey, created.getId()))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrument retrieved = mvcUtils.getFromJsonResponseBody(getResult, ApiInstrument.class);
    assertNotNull(retrieved);
    assertEquals(created.getId(), retrieved.getId());
    assertEquals("instrument-in-grid", retrieved.getName());
  }

  @Test
  public void listInstruments() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    createBasicInstrumentForUser(anyUser, "instr-a");
    createBasicInstrumentForUser(anyUser, "instr-b");
    createBasicInstrumentForUser(anyUser, "instr-c");

    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/instruments", anyUser))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrumentSearchResult allInstruments =
        getFromJsonResponseBody(result, ApiInstrumentSearchResult.class);
    assertNotNull(allInstruments);
    assertEquals(3, allInstruments.getTotalHits().intValue());
    assertEquals(3, allInstruments.getInstruments().size());

    // pagination
    result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/instruments", anyUser)
                    .param("pageSize", "2")
                    .param("pageNumber", "0"))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrumentSearchResult page =
        getFromJsonResponseBody(result, ApiInstrumentSearchResult.class);
    assertEquals(3, page.getTotalHits().intValue());
    assertEquals(2, page.getInstruments().size());
    assertEquals(2, page.getLinks().size()); // self, next
  }

  @Test
  public void validateNameForNewInstrument() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    createBasicInstrumentForUser(anyUser, "existing-instrument");

    // too long name
    String tooLong = StringUtils.leftPad("test", 256, '*');
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                        API_VERSION.ONE,
                        apiKey,
                        "/instruments/validateNameForNewInstrument",
                        anyUser)
                    .param("name", tooLong))
            .andExpect(status().isOk())
            .andReturn();
    Map<?, ?> data = parseJSONObjectFromResponseStream(result);
    assertEquals(false, data.get("valid"));
    assertEquals("Name is too long (max 255 chars)", data.get("message"));

    // already-existing name
    result =
        mockMvc
            .perform(
                createBuilderForGet(
                        API_VERSION.ONE,
                        apiKey,
                        "/instruments/validateNameForNewInstrument",
                        anyUser)
                    .param("name", "existing-instrument"))
            .andExpect(status().isOk())
            .andReturn();
    data = parseJSONObjectFromResponseStream(result);
    assertEquals(false, data.get("valid"));
    assertEquals("There is already an instrument named [existing-instrument]", data.get("message"));

    // valid unique name
    result =
        mockMvc
            .perform(
                createBuilderForGet(
                        API_VERSION.ONE,
                        apiKey,
                        "/instruments/validateNameForNewInstrument",
                        anyUser)
                    .param("name", "brand-new-instrument"))
            .andExpect(status().isOk())
            .andReturn();
    data = parseJSONObjectFromResponseStream(result);
    assertEquals(true, data.get("valid"));
  }

  @Test
  public void updateInstrument() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "before-update");

    String updateJson = "{ \"name\": \"after-update\", \"description\": \"updated desc\" }";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/instruments/" + instrument.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument updated = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertNotNull(updated);
    assertEquals("after-update", updated.getName());
    assertEquals("updated desc", updated.getDescription());
    assertEquals(instrument.getId(), updated.getId());
  }

  @Test
  public void deleteAndRestoreInstrument() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser);

    // delete
    MvcResult result =
        mockMvc
            .perform(createBuilderForDelete(apiKey, "/instruments/" + instrument.getId(), anyUser))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument deleted = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertTrue(deleted.isDeleted());

    // restore
    result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/instruments/" + instrument.getId() + "/restore", anyUser, "{}"))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument restored = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertFalse(restored.isDeleted());
    assertEquals(instrument.getId(), restored.getId());
  }

  @Test
  public void duplicateInstrument() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "original");

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    apiKey,
                    "/instruments/" + instrument.getId() + "/actions/duplicate",
                    anyUser))
            .andExpect(status().isCreated())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument copy = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertNotNull(copy);
    assertNotNull(copy.getId());
    assertFalse(copy.getId().equals(instrument.getId()));
    assertTrue(copy.getName().contains("original"));
    assertEquals(anyUser.getUsername(), copy.getOwner().getUsername());
  }

  @Test
  public void getInstrumentRevisionsAndRevisionById() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "rev-test");

    // update the instrument to generate a second revision
    String updateJson = "{ \"name\": \"rev-test-updated\" }";
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/instruments/" + instrument.getId(), anyUser, updateJson))
        .andExpect(status().isOk());

    // list revisions
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/instruments/" + instrument.getId() + "/revisions",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInventoryRecordRevisionList history =
        getFromJsonResponseBody(result, ApiInventoryRecordRevisionList.class);
    assertEquals(2, history.getRevisions().size());

    Long firstRevisionId = history.getRevisions().get(0).getRevisionId();
    assertEquals("rev-test", history.getRevisions().get(0).getRecord().getName());

    // get specific revision
    result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/instruments/" + instrument.getId() + "/revisions/" + firstRevisionId,
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument rev1 = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertNotNull(rev1);
    assertEquals("rev-test", rev1.getName());
    assertEquals(firstRevisionId, rev1.getRevisionId());
    assertTrue(
        rev1.getLinkOfType(ApiLinkItem.SELF_REL)
            .get()
            .getLink()
            .endsWith("/revisions/" + firstRevisionId));
  }

  @Test
  public void changeInstrumentOwner() throws Exception {
    User owner = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(owner);
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "transfer-me");

    User newOwner = doCreateAndInitUser(getRandomName(8));

    String changeOwnerJson = "{ \"owner\": { \"username\": \"" + newOwner.getUsername() + "\" } }";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey,
                    "/instruments/" + instrument.getId() + "/actions/changeOwner",
                    owner,
                    changeOwnerJson))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument transferred = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertNotNull(transferred);
    assertEquals(newOwner.getUsername(), transferred.getOwner().getUsername());
  }

  @Test
  public void getInstrumentImageAndThumbnail() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    String post = "{ \"name\": \"image-instrument\", \"newBase64Image\": \"" + BASE_64 + "\" }";
    MvcResult createResult =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/instruments", anyUser, post))
            .andExpect(status().isCreated())
            .andReturn();

    ApiInstrument created = mvcUtils.getFromJsonResponseBody(createResult, ApiInstrument.class);
    assertNotNull(created);
    assertTrue(created.getLinkOfType(ApiLinkItem.IMAGE_REL).isPresent());

    // GET image
    MvcResult imageResult =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/instruments/" + created.getId() + "/image/0",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(imageResult.getResponse().getContentAsByteArray().length > 0);

    // GET thumbnail
    MvcResult thumbResult =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/instruments/" + created.getId() + "/thumbnail/0",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(thumbResult.getResponse().getContentAsByteArray().length > 0);
  }

  @Test
  public void createInstrumentFromTemplateCopiesFieldsAndLinksTemplate() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    com.researchspace.api.v1.model.ApiInstrumentTemplate template =
        createBasicInstrumentTemplateForUser(anyUser);

    String instrumentJson =
        "{ \"name\": \"from-template-MVC\", \"templateId\": " + template.getId() + " }";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/instruments", anyUser, instrumentJson))
            .andExpect(status().isCreated())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument created = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertNotNull(created);
    assertEquals("from-template-MVC", created.getName());
    assertEquals(template.getId(), created.getTemplateId());
    assertEquals(template.getVersion(), created.getTemplateVersion());
    // basic template has one field — should be copied onto the instrument
    assertEquals(1, created.getFields().size());
    assertEquals(template.getFields().get(0).getName(), created.getFields().get(0).getName());
  }

  @Test
  public void createInstrumentWithUnknownTemplateIdReturnsBadRequest() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    String instrumentJson = "{ \"name\": \"orphan\", \"templateId\": 999999 }";
    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/instruments", anyUser, instrumentJson))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  public void updateInstrumentMovesIntoListContainerViaParentContainers() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiContainer listContainer = createBasicContainerForUser(anyUser, "target-list");
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "to-move-list");

    String updateJson = "{ \"parentContainers\": [ { \"id\": " + listContainer.getId() + " } ] }";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/instruments/" + instrument.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    // NB: `storedInContainer` is JSON read-only, so it doesn't round-trip onto the deserialised
    // test object — assert on parentContainers (writable) which does.
    ApiInstrument updated = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertEquals(listContainer.getId(), updated.getParentContainers().get(0).getId());
  }

  @Test
  public void updateInstrumentMovesIntoGridLocationViaParentContainersAndLocation()
      throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiContainer gridContainer = createBasicGridContainerForUser(anyUser, 3, 3);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "to-move-grid");

    String updateJson =
        "{ \"parentContainers\": [ { \"id\": "
            + gridContainer.getId()
            + " } ], \"parentLocation\": { \"coordX\": 2, \"coordY\": 3 } }";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/instruments/" + instrument.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument updated = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertEquals(gridContainer.getId(), updated.getParentContainers().get(0).getId());
    assertNotNull(updated.getParentLocation());
    assertEquals(Integer.valueOf(2), updated.getParentLocation().getCoordX());
    assertEquals(Integer.valueOf(3), updated.getParentLocation().getCoordY());
  }

  @Test
  public void updateInstrumentMovesIntoLocationByIdViaParentLocation() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiContainer imageContainer = createBasicImageContainerForUser(anyUser);
    Long targetLocationId = imageContainer.getLocations().get(0).getId();
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "to-move-by-loc");

    String updateJson = "{ \"parentLocation\": { \"id\": " + targetLocationId + " } }";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/instruments/" + instrument.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInstrument updated = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertEquals(imageContainer.getId(), updated.getParentContainers().get(0).getId());
  }

  @Test
  public void updateInstrumentWithoutParentFieldsDoesNotMove() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiContainer listContainer = createBasicContainerForUser(anyUser, "home-list");
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "stays-put");

    // first move it into the list container
    String moveJson = "{ \"parentContainers\": [ { \"id\": " + listContainer.getId() + " } ] }";
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/instruments/" + instrument.getId(), anyUser, moveJson))
        .andExpect(status().isOk())
        .andReturn();

    // a content-only update with no parent fields must not dislodge it
    String renameJson = "{ \"name\": \"stays-put-renamed\" }";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/instruments/" + instrument.getId(), anyUser, renameJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrument updated = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertEquals("stays-put-renamed", updated.getName());
    assertEquals(listContainer.getId(), updated.getParentContainers().get(0).getId());
  }

  private MockHttpServletRequestBuilder getInstrumentById(
      User user, String apiKey, Long instrumentId) {
    return createBuilderForGet(API_VERSION.ONE, apiKey, "/instruments/{id}", user, instrumentId);
  }
}
