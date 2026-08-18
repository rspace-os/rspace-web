package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.webapp.integrations.b2inst.B2instConnectorDummy;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.BindingResult;

/**
 * End-to-end check that registering a PIDINST identifier for an Instrument sends the instrument's
 * PIDINST-mapped field content to B2INST. A {@link B2instConnectorDummy} captures the payload, so
 * no B2INST instance is contacted.
 *
 * <p>Link-field narratives ("Measurement technique", "Calibration") are covered by {@code
 * RspaceToExternalProviderAdapterImplTest}; building an {@code InventoryLink} target through the
 * API would need a second record and relation setup, which adds nothing to the plumbing this test
 * exercises.
 */
@WebAppConfiguration
public class InventoryIdentifiersB2instApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Autowired private InventoryIdentifierApiManager identifierApiManager;
  @Autowired private SystemSettingsApiController settingsController;

  private final B2instConnectorDummy b2instDummy = new B2instConnectorDummy();
  private final BindingResult mockBindingResult = mock(BindingResult.class);
  private Object realB2instConnector;

  @Before
  public void setup() throws Exception {
    realB2instConnector = ReflectionTestUtils.getField(identifierApiManager, "b2instConnector");
    ReflectionTestUtils.setField(identifierApiManager, "b2instConnector", b2instDummy);
    super.setUp();
    setB2instEnabled("true");
  }

  @After
  public void teardown() throws Exception {
    setB2instEnabled("false");
    // identifierApiManager is a singleton in a Spring context cached across test classes, so the
    // dummy has to be swapped back out or later MVC tests silently run against it.
    ReflectionTestUtils.setField(identifierApiManager, "b2instConnector", realB2instConnector);
  }

  /*
   * The /identifiers availability gate (ApiAvailabilityHandlerImpl) consults the REAL
   * B2instConnectorImpl bean, NOT the dummy injected into the manager, and its
   * isConfiguredAndEnabled() requires enabled AND server URL AND token (seeded DB defaults are
   * disabled/blank). serverUrl maps to pidinst.b2inst.server.url and password carries the B2INST
   * token. The fake URL is safe: the manager's dummy intercepts registerDoi before any HTTP call.
   */
  private void setB2instEnabled(String enabled) throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    IdentifierSettings settings = new IdentifierSettings();
    settings.setProvider(IdentifierType.PIDINST_B2INST);
    settings.setEnabled(enabled);
    settings.setServerUrl("https://b2inst-test.example.org");
    settings.setPassword("dummy-token");
    settingsController.updateInventorySettings(
        new MockHttpServletRequest(), settings, mockBindingResult, sysadmin);
  }

  /*
   * Mirrors the private helper in InventoryIdentifiersApiControllerMVCIT, but returns the raw result
   * rather than a deserialized DTO. providerUrl and publicUrl are @JsonProperty READ_ONLY on
   * ApiInventoryDOI, so Jackson emits them but ignores them on the way in; getFromJsonResponseBody is
   * a plain readValue, so reading them off a deserialized DTO would silently yield null and the
   * assertion would pass vacuously. The response JSON is the only place they can be observed.
   */
  private MvcResult registerNewIdentifier(User anyUser, String apiKey, String parentGlobalId)
      throws Exception {
    String post = "{ \"parentGlobalId\": \"" + parentGlobalId + "\" }";
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/identifiers", anyUser, post))
            .andReturn();
    assertNull(result.getResolvedException());
    return result;
  }

  @Test
  public void registerB2instIdentifierCarriesMappedTemplateMetadata() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    // Step A: a template carrying the filled PIDINST-shaped fields. POST /instruments cannot
    // create fields from a fields[] payload, so the instrument is created FROM a template: the
    // copy path clones each field including its content, bar the landing page (see below).
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("PIDINST template copy");
    templatePost
        .getFields()
        .add(createBasicApiSampleField("Owner", ApiFieldType.STRING, "Arctic Research Institute"));
    templatePost
        .getFields()
        .add(createBasicApiSampleField("Manufacturer", ApiFieldType.STRING, "Acme Instruments"));
    templatePost.getFields().add(createBasicApiSampleField("Model", ApiFieldType.STRING, "AWS-42"));
    templatePost
        .getFields()
        .add(createBasicApiSampleField("Instrument type", ApiFieldType.STRING, "Weather station"));
    templatePost
        .getFields()
        .add(createBasicApiSampleField("Commissioned", ApiFieldType.DATE, "2024-02-21"));
    templatePost
        .getFields()
        .add(
            createBasicApiSampleField("Measured quantity", ApiFieldType.STRING, "Air temperature"));
    templatePost
        .getFields()
        .add(createBasicApiSampleField("Last calibrated", ApiFieldType.DATE, "2026-01-15"));
    templatePost
        .getFields()
        .add(
            createBasicApiSampleField(
                "Landing page", ApiFieldType.URI, "https://lab.example.org/aws-42"));
    templatePost
        .getFields()
        .add(
            createBasicApiSampleField(
                "Alternate Identifier", ApiFieldType.STRING, "INV-2025-0042"));

    MvcResult templateResult =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instrumentTemplates", anyUser, templatePost))
            .andExpect(status().isCreated())
            .andReturn();
    assertNull(templateResult.getResolvedException());
    Long templateId = getFromJsonResponseBody(templateResult, ApiInstrumentTemplate.class).getId();

    // Step B: create the instrument FROM the template; field content copies across.
    String instrumentJson =
        "{\"name\": \"Microscope X\","
            + " \"description\": \"An automatic weather station.\","
            + " \"templateId\": "
            + templateId
            + "}";
    MvcResult instrumentResult =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/instruments", anyUser, instrumentJson))
            .andExpect(status().isCreated())
            .andReturn();
    assertNull(instrumentResult.getResolvedException());
    String instrumentGlobalId =
        getFromJsonResponseBody(instrumentResult, ApiInstrument.class).getGlobalId();

    MvcResult doiResult = registerNewIdentifier(anyUser, apiKey, instrumentGlobalId);
    ApiInventoryDOI registeredDoi = getFromJsonResponseBody(doiResult, ApiInventoryDOI.class);

    assertNotNull(registeredDoi);
    assertEquals(B2instConnectorDummy.DUMMY_RID, registeredDoi.getDoi());
    assertEquals("draft", registeredDoi.getState());
    /*
     * The B2INST record page is captured from the create-draft response and persisted, so the UI can
     * link a PIDINST identifier from registration onwards, not only once it is published. Asserted on
     * the response JSON, not the DTO: see registerNewIdentifier for why the DTO cannot show these two.
     */
    JsonNode doiJson = new ObjectMapper().readTree(doiResult.getResponse().getContentAsString());
    assertEquals(B2instConnectorDummy.DUMMY_SELF_HTML, doiJson.path("providerUrl").asText());
    assertTrue(
        doiJson.path("publicUrl").isNull() || doiJson.path("publicUrl").isMissingNode(),
        "a draft PIDINST identifier has no citable public URL yet");

    B2instInstrumentMetadata sent = b2instDummy.getDoiSentToB2inst().getMetadata();
    assertEquals("Microscope X", sent.getName());
    assertEquals("1.0", sent.getSchemaVersion());
    assertEquals("An automatic weather station.", sent.getDescription());
    assertEquals("Arctic Research Institute", sent.getOwner().get(0).getOwnerName());
    assertEquals(anyUser.getEmail(), sent.getOwner().get(0).getOwnerContact());
    assertEquals("Acme Instruments", sent.getManufacturer().get(0).getManufacturerName());
    assertEquals("AWS-42", sent.getModel().getModelName());
    assertEquals("Weather station", sent.getInstrumentType().get(0).getInstrumentTypeName());
    assertEquals(1, sent.getDate().size());
    assertEquals("2024-02-21", sent.getDate().get(0).getDate());
    assertEquals("Commissioned", sent.getDate().get(0).getDateType());
    // "Last calibrated" is not mapped; MeasuredVariable carries the measured quantity verbatim
    assertEquals(List.of("Air temperature"), sent.getMeasuredVariable());
    /*
     * The one template field that deliberately does NOT copy across: a landing page names exactly
     * one physical instrument, so the new instrument gets its own address instead of the template's
     * (RSDEV-1307). That own address is what gets registered.
     *
     * Note this assertion no longer discriminates the landing page mapping itself: the mapped-field
     * branch and the GlobalIdUrls fallback both produce this same "/globalId/..." string, so it
     * would still pass if the field mapping were dropped. The mapping is pinned discriminatingly by
     * the unit tests in RspaceToExternalProviderAdapterImplTest (see
     * landingPageFromTheFieldSurvivesAnUnsetServerUrl); what this MVCIT pins is that a landing page
     * reaches B2INST at all on the create-from-template path.
     */
    assertTrue(
        sent.getLandingPage().endsWith("/globalId/" + instrumentGlobalId),
        "expected the instrument's own landing page, got: " + sent.getLandingPage());
    assertEquals("Other", sent.getAlternateIdentifier().get(0).getAlternateIdentifierType());
    assertEquals(
        "INV-2025-0042", sent.getAlternateIdentifier().get(0).getAlternateIdentifierValue());
  }
}
