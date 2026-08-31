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
import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.webapp.integrations.b2inst.B2instConnectorDummy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * <p>The two link fields ("Measurement technique", "Calibration") are deliberately absent here.
 * They stopped being documentation-only in RSDEV-1253 and now map to RelatedIdentifier entries, but
 * their value comes from a linked record rather than the field's own content, so exercising them
 * needs a second record and relation setup that this template-copy flow does not otherwise use.
 * They are covered against real persistence by {@code
 * InventoryIdentifierApiManagerRelatedIdentifierTest}, which resolves the link through Hibernate
 * for both providers, and by {@code RspaceToExternalProviderAdapterImplTest} for the mapping rules.
 */
@WebAppConfiguration
public class InventoryIdentifiersB2instApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Autowired private InventoryIdentifierApiManager identifierApiManager;
  @Autowired private SystemSettingsApiController settingsController;

  private final B2instConnectorDummy b2instDummy = new B2instConnectorDummy();
  private final BindingResult mockBindingResult = mock(BindingResult.class);
  private Object realB2instConnector;
  private ApiInventorySystemSettings.IdentifierSettings originalB2instSettings;

  @BeforeEach
  public void setup() throws Exception {
    realB2instConnector = ReflectionTestUtils.getField(identifierApiManager, "b2instConnector");
    ReflectionTestUtils.setField(identifierApiManager, "b2instConnector", b2instDummy);
    super.setUp();
    // these are system properties in the shared dev database, so put them back afterwards
    originalB2instSettings =
        captureIdentifierSettings(settingsController, IdentifierType.PIDINST_B2INST);
    setB2instEnabled("true");
  }

  @AfterEach
  public void teardown() throws Exception {
    restoreIdentifierSettings(
        settingsController, IdentifierType.PIDINST_B2INST, originalB2instSettings);
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
   * rather than a deserialized DTO. state, providerUrl and publicUrl are @JsonProperty READ_ONLY on
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
    /*
     * state and the two URLs are asserted on the response JSON, not the DTO: state is READ_ONLY
     * since the parallel review (a record update carrying "state" could otherwise open the public
     * page), so it too would deserialize to null. See registerNewIdentifier.
     *
     * The B2INST record page is captured from the create-draft response and persisted, so the UI
     * can link a PIDINST identifier from registration onwards, not only once it is published.
     */
    JsonNode doiJson = new ObjectMapper().readTree(doiResult.getResponse().getContentAsString());
    assertEquals("draft", doiJson.path("state").asText());
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
     * one physical instrument, so a landing page inherited from the template must never be
     * registered for the instrument created from it (RSDEV-1307). Nothing refills it either, since
     * the auto-fill that used to write a /globalId/ address is retired (ADR 0006 item 3), so at this
     * point the field is blank and what reaches B2INST is the identifier's own public landing page.
     *
     * This is the one assertion that composes the whole RSDEV-1254 invariant end to end: the suffix
     * registered with B2INST is the same suffix the entity's publicLink adopted. The two halves are
     * pinned separately by unit tests (InventoryIdentifierApiManagerImplUnitTest for DTO->payload,
     * ApiIdentifiersHelperTest for DTO->entity), but only a full request exercises the seam between
     * them - and that seam is fragile, because publicLinkSuffix is @JsonIgnore, so the DTO has to
     * survive from createNewB2instDoi to the entity by object identity. Any serialisation
     * round-trip introduced on that path would silently drop the suffix, the entity would
     * self-generate a different one, and a "contains /public/inventory/" assertion would still
     * pass while the registered address 404s forever.
     */
    assertNotNull(registeredDoi.getRsPublicId(), "the identifier must expose its public link");
    assertTrue(
        sent.getLandingPage().endsWith("/public/inventory/" + registeredDoi.getRsPublicId()),
        "the address registered with B2INST must name the page RSpace will serve; registered: "
            + sent.getLandingPage()
            + ", identifier publicLink: "
            + registeredDoi.getRsPublicId());
    assertEquals("Other", sent.getAlternateIdentifier().get(0).getAlternateIdentifierType());
    assertEquals(
        "INV-2025-0042", sent.getAlternateIdentifier().get(0).getAlternateIdentifierValue());
  }
}
