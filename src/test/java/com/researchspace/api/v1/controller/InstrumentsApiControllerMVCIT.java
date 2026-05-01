package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

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
    ApiInstrument created = getFromJsonResponseBody(result, ApiInstrument.class);
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
    ApiInstrument retrieved = getFromJsonResponseBody(result, ApiInstrument.class);
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
    ApiInstrument created = getFromJsonResponseBody(result, ApiInstrument.class);
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

    ApiInstrument created = getFromJsonResponseBody(result, ApiInstrument.class);
    assertNotNull(created);
    assertNotNull(created.getId());
    assertEquals("instrument-in-grid", created.getName());

    MvcResult getResult =
        mockMvc
            .perform(getInstrumentById(anyUser, apiKey, created.getId()))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrument retrieved = getFromJsonResponseBody(getResult, ApiInstrument.class);
    assertNotNull(retrieved);
    assertEquals(created.getId(), retrieved.getId());
    assertEquals("instrument-in-grid", retrieved.getName());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      getInstrumentById(User user, String apiKey, Long instrumentId) {
    return createBuilderForGet(API_VERSION.ONE, apiKey, "/instruments/{id}", user, instrumentId);
  }
}
