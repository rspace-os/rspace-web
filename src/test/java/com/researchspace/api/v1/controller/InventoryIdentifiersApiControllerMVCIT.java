package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.model.User;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

@WebAppConfiguration
@RunWith(ConditionalTestRunner.class)
public class InventoryIdentifiersApiControllerMVCIT extends API_MVC_InventoryTestBase {

  private @Autowired SystemSettingsApiController settingsController;

  @Value("${datacite.realConnectionTest.username}")
  private String testDataciteUsername;

  @Value("${datacite.realConnectionTest.password}")
  private String testDatacitePassword;

  @Value("${datacite.realConnectionTest.prefix}")
  private String testDatacitePrefix;

  private BindingResult mockBindingResult = mock(BindingResult.class);

  @Before
  public void setup() throws Exception {
    super.setUp();
    enableDataCiteRealConnectionSettings();
  }

  private void enableDataCiteRealConnectionSettings() throws BindException {
    ApiInventorySystemSettings update = new ApiInventorySystemSettings();
    update.getDatacite().setEnabled("true");
    update.getDatacite().setServerUrl("https://api.test.datacite.org");
    update.getDatacite().setUsername(testDataciteUsername);
    update.getDatacite().setPassword(testDatacitePassword);
    update.getDatacite().setRepositoryPrefix(testDatacitePrefix);
    settingsController.updateInventorySettings(
        new MockHttpServletRequest(), update, mockBindingResult, getSysAdminUser());
  }

  @After
  public void disableDataCiteConnection() throws BindException {
    ApiInventorySystemSettings update = new ApiInventorySystemSettings();
    update.getDatacite().setEnabled("false");
    settingsController.updateInventorySettings(
        new MockHttpServletRequest(), update, mockBindingResult, getSysAdminUser());
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void realConnectionRegisterUpdateDeleteDataciteIdentifier() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    ApiContainer draftContainer =
        createBasicContainerForUser(anyUser, "container for draft identifier test");
    assertEquals(0, draftContainer.getIdentifiers().size());

    ApiInventoryDOI registeredDoi =
        registerNewIdentifier(anyUser, apiKey, draftContainer.getGlobalId());
    assertNotNull(registeredDoi);
    assertNotNull(registeredDoi.getDoi());
    assertEquals("draft", registeredDoi.getState());

    draftContainer = containerApiMgr.getApiContainerById(draftContainer.getId(), anyUser);
    assertEquals(1, draftContainer.getIdentifiers().size());
    assertEquals(registeredDoi.getId(), draftContainer.getIdentifiers().get(0).getId());

    deleteDraftDataCiteDoiForItem(anyUser, apiKey, registeredDoi.getId());
    draftContainer = containerApiMgr.getApiContainerById(draftContainer.getId(), anyUser);
    assertEquals(0, draftContainer.getIdentifiers().size());
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void realConnectionRegisterPublishRetractDataciteIdentifier() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    ApiContainer apiContainer = createBasicContainerForUser(anyUser);
    assertEquals(0, apiContainer.getAttachments().size());

    ApiInventoryDOI registeredDoi =
        registerNewIdentifier(anyUser, apiKey, apiContainer.getGlobalId());
    assertNotNull(registeredDoi);
    assertNotNull(registeredDoi.getDoi());
    assertEquals("draft", registeredDoi.getState());

    // update identifier with recommended fields
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setId(apiContainer.getId());
    addRecommendedFieldsToDOi(registeredDoi);
    containerUpdate.setIdentifiers(List.of(registeredDoi));
    containerApiMgr.updateApiContainer(containerUpdate, anyUser);

    ApiInventoryDOI publishedDoi = publishDraftIdentifier(anyUser, apiKey, registeredDoi.getId());
    assertNotNull(publishedDoi);
    assertEquals("findable", publishedDoi.getState());
    assertEquals(1, publishedDoi.getSubjects().size());
    assertEquals(1, publishedDoi.getDescriptions().size());
    assertEquals(1, publishedDoi.getAlternateIdentifiers().size());
    assertEquals(1, publishedDoi.getDates().size());

    ApiInventoryDOI retractedDoi =
        retractPublishedIdentifier(anyUser, apiKey, registeredDoi.getId());
    assertNotNull(retractedDoi);
    assertEquals("registered", retractedDoi.getState());
  }

  private void addRecommendedFieldsToDOi(ApiInventoryDOI registeredDoi) {
    ApiInventoryDOI.ApiInventoryDOISubject testSubject =
        new ApiInventoryDOI.ApiInventoryDOISubject("test subject", "", "", "", "");
    registeredDoi.setSubjects(List.of(testSubject));
    ApiInventoryDOI.ApiInventoryDOIDescription testDescription =
        new ApiInventoryDOI.ApiInventoryDOIDescription(
            "test description",
            ApiInventoryDOI.ApiInventoryDOIDescription.DoiDescriptionType.ABSTRACT);
    registeredDoi.setDescriptions(List.of(testDescription));
    ApiInventoryDOI.ApiInventoryDOIAlternateIdentifier testAlternateIdentifier =
        new ApiInventoryDOI.ApiInventoryDOIAlternateIdentifier("test id", "test type");
    registeredDoi.setAlternateIdentifiers(List.of(testAlternateIdentifier));
    ApiInventoryDOI.ApiInventoryDOIDate testDate =
        new ApiInventoryDOI.ApiInventoryDOIDate(
            "2023-08-01", ApiInventoryDOI.ApiInventoryDOIDate.DoiDateType.OTHER);
    registeredDoi.setDates(List.of(testDate));
  }

  private ApiInventoryDOI registerNewIdentifier(User anyUser, String apiKey, String parentGlobalId)
      throws Exception {
    String post = "{ \"parentGlobalId\": \"" + parentGlobalId + "\" }";
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/identifiers", anyUser, post))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventoryDOI registeredDoi = getFromJsonResponseBody(result, ApiInventoryDOI.class);
    return registeredDoi;
  }

  private void deleteDraftDataCiteDoiForItem(User anyUser, String apiKey, Long identifierId)
      throws Exception {
    mockMvc
        .perform(createBuilderForDelete(apiKey, "/identifiers/{id}", anyUser, identifierId))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
  }

  private ApiInventoryDOI publishDraftIdentifier(User anyUser, String apiKey, Long identifierId)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    apiKey,
                    String.format("/identifiers/%d/publish", identifierId),
                    anyUser))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    return getFromJsonResponseBody(result, ApiInventoryDOI.class);
  }

  private ApiInventoryDOI retractPublishedIdentifier(User anyUser, String apiKey, Long identifierId)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    apiKey,
                    String.format("/identifiers/%d/retract", identifierId),
                    anyUser))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    return getFromJsonResponseBody(result, ApiInventoryDOI.class);
  }
}
