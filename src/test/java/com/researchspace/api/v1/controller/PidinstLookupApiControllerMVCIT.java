package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.api.v1.model.ApiPidinstImportPost;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.dao.customliquibaseupdates.CreateDefaultInstrumentTemplate_RSDEV1219;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.PidinstLookupManager;
import com.researchspace.webapp.integrations.b2inst.B2instConnectorDummy;
import java.io.InputStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.validation.BindingResult;

/**
 * Search, import, the 409 on a second import, and the linked identifier's gates, end to end over
 * HTTP against a {@link B2instConnectorDummy} holding one published record. No B2INST is contacted.
 */
@WebAppConfiguration
public class PidinstLookupApiControllerMVCIT extends API_MVC_InventoryTestBase {

  /*
   * A fresh PID per test method. An import commits, and a PID that is already linked is refused
   * deployment-wide (decision 7), so a shared constant would make every method after the first
   * fail with 409 against this shared dev database - and again on the next run of the suite. The
   * record id must equal the Handle suffix, because that is how B2INST resolves a Handle to its
   * record (B2instConnector.getRecordByHandle).
   */
  private static final String HANDLE_PREFIX = "21.T11975/";

  private String recordId;
  private String handle;

  private static String publishedRecordJson(String recordId, String handle) {
    return "{\"id\":\""
        + recordId
        + "\",\"is_published\":true,\"status\":\"published\","
        + "\"created\":\"2026-08-01T10:00:00+00:00\",\"updated\":\"2026-08-02T10:00:00+00:00\","
        + "\"links\":{\"self_html\":\"https://b2inst-test.example.org/records/"
        + recordId
        + "\"},\"metadata\":{\"Name\":\"Test microscope\",\"SchemaVersion\":\"1.0\","
        + "\"Identifier\":{\"identifierType\":\"Handle\",\"identifierValue\":\""
        + handle
        + "\"},\"Owner\":[{\"ownerName\":\"Lab"
        + " A\"}],\"Manufacturer\":[{\"manufacturerName\":\"Zeiss\"}],\"Model\":{\"modelName\":\"LSM"
        + " 900\"},\"InstrumentType\":[{\"instrumentTypeName\":\"Confocal"
        + " microscope\"}],\"MeasuredVariable\":[\"Fluorescent light\"],"
        + "\"LandingPage\":\"https://lab.example.org/instruments/lsm900\"}}";
  }

  @Autowired private InventoryIdentifierApiManager identifierApiManager;
  @Autowired private PidinstLookupManager pidinstLookupManager;
  @Autowired private SystemSettingsApiController settingsController;
  @Autowired private InstrumentEntityApiManager instrumentApiMgr;
  @Autowired private InstrumentTemplateDao instrumentTemplateDao;

  private final B2instConnectorDummy b2instDummy = new B2instConnectorDummy();
  private final BindingResult mockBindingResult = mock(BindingResult.class);
  private Object realIdentifierConnector;
  private Object realLookupConnector;
  private IdentifierSettings originalB2instSettings;
  private IdentifierSettings originalPidinstDataCiteSettings;

  @BeforeEach
  public void setup() throws Exception {
    recordId = RandomStringUtils.randomAlphanumeric(10).toLowerCase();
    handle = HANDLE_PREFIX + recordId;
    b2instDummy.setPublishedRecord(
        new ObjectMapper()
            .readValue(publishedRecordJson(recordId, handle), B2instDraftRecord.class));
    realIdentifierConnector = ReflectionTestUtils.getField(identifierApiManager, "b2instConnector");
    ReflectionTestUtils.setField(identifierApiManager, "b2instConnector", b2instDummy);
    realLookupConnector = ReflectionTestUtils.getField(pidinstLookupManager, "b2instConnector");
    ReflectionTestUtils.setField(pidinstLookupManager, "b2instConnector", b2instDummy);
    super.setUp();
    ensureLockedDefaultTemplateExists();
    // system properties in the shared dev database: captured here, put back in teardown
    originalB2instSettings =
        captureIdentifierSettings(settingsController, IdentifierType.PIDINST_B2INST);
    originalPidinstDataCiteSettings =
        captureIdentifierSettings(settingsController, IdentifierType.PIDINST_DATACITE);
    setB2instEnabled("true");
  }

  @AfterEach
  public void teardown() throws Exception {
    // B2INST first: restoring it frees the enabled flag for DataCite to reclaim (enabling one
    // PIDINST provider disables the other)
    restoreIdentifierSettings(
        settingsController, IdentifierType.PIDINST_B2INST, originalB2instSettings);
    restoreIdentifierSettings(
        settingsController, IdentifierType.PIDINST_DATACITE, originalPidinstDataCiteSettings);
    ReflectionTestUtils.setField(identifierApiManager, "b2instConnector", realIdentifierConnector);
    ReflectionTestUtils.setField(pidinstLookupManager, "b2instConnector", realLookupConnector);
  }

  /**
   * Seeds the locked default PIDINST template when this database has none.
   *
   * <p>{@code DatabaseCleaner.cleanUp} deletes every {@code InstrumentEntity} row after an
   * integration-test class, which takes the row the RSDEV-1219 Liquibase changeset created with it,
   * and a changeset already recorded in DATABASECHANGELOG never runs again. The import has no
   * meaning without that template, so this test creates it the way the seeder does - from the same
   * JSON resource, created editable and then locked - rather than depending on a row an unrelated
   * test class may have removed.
   */
  private void ensureLockedDefaultTemplateExists() throws Exception {
    boolean alreadySeeded =
        doInTransaction(
            () ->
                instrumentTemplateDao
                    .findLockedTemplateByName(
                        CreateDefaultInstrumentTemplate_RSDEV1219.TEMPLATE_NAME)
                    .isPresent());
    if (alreadySeeded) {
      return;
    }
    User sysadmin = logoutAndLoginAsSysAdmin();
    ApiInstrumentTemplatePost post;
    try (InputStream in =
        new ClassPathResource("inventory/defaultInstrumentTemplate-PIDINST-1.0.json")
            .getInputStream()) {
      post =
          new ObjectMapper()
              .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
              .readValue(in, ApiInstrumentTemplatePost.class);
    }
    ApiInstrumentTemplate created = instrumentApiMgr.createInstrumentTemplate(post, sysadmin);
    doInTransaction(
        () -> {
          InstrumentTemplate template = instrumentTemplateDao.get(created.getId());
          template.setEditable(false);
          instrumentTemplateDao.save(template);
          instrumentTemplateDao.resetDefaultTemplateOwner();
        });
  }

  /*
   * The availability gate consults the REAL B2instConnectorImpl bean, whose isConfiguredAndEnabled()
   * needs enabled + server URL + token; the fake URL is safe because the dummies intercept every call.
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

  private JsonNode json(MvcResult result) throws Exception {
    return new ObjectMapper().readTree(result.getResponse().getContentAsString());
  }

  private MvcResult importPid(User user, String apiKey, String pid, int expectedStatus)
      throws Exception {
    ApiPidinstImportPost post = new ApiPidinstImportPost();
    post.setPid(pid);
    return mockMvc
        .perform(
            createBuilderForInventoryPostWithJSONBody(
                apiKey, "/instruments/importPidinst", user, post))
        .andExpect(status().is(expectedStatus))
        .andReturn();
  }

  @Test
  public void searchReturnsTheRecordAndFlagsItOnceImported() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    MvcResult searchResult =
        mockMvc
            .perform(
                createBuilderForInventoryGet(
                    API_VERSION.ONE, apiKey, "/pidinst/search?query=microscope", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiPidinstSearchResult search =
        mvcUtils.getFromJsonResponseBody(searchResult, ApiPidinstSearchResult.class);
    assertEquals("PIDINST_B2INST", search.getProvider());
    assertEquals(1, search.getTotal());
    assertEquals(handle, search.getHits().get(0).getPid());
    assertEquals("Test microscope", search.getHits().get(0).getName());
    assertNull(search.getHits().get(0).getLinkedInstrumentGlobalId());

    ApiInstrument created =
        mvcUtils.getFromJsonResponseBody(
            importPid(anyUser, apiKey, handle, 201), ApiInstrument.class);

    MvcResult afterImport =
        mockMvc
            .perform(
                createBuilderForInventoryGet(
                    API_VERSION.ONE, apiKey, "/pidinst/search?query=" + handle, anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        created.getGlobalId(),
        json(afterImport).get("hits").get(0).get("linkedInstrumentGlobalId").asText());
  }

  @Test
  public void importCreatesTheInstrumentFromTheDefaultTemplateWithALinkedIdentifier()
      throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    MvcResult result = importPid(anyUser, apiKey, "https://hdl.handle.net/" + handle, 201);
    assertNull(result.getResolvedException());
    ApiInstrument created = mvcUtils.getFromJsonResponseBody(result, ApiInstrument.class);
    assertNotNull(created.getTemplateId(), "created from the default template");
    assertEquals("Test microscope", created.getName());
    assertEquals(12, created.getFields().size());
    assertEquals("Lab A", created.getFields().get(0).getContent());
    assertEquals("Zeiss", created.getFields().get(1).getContent());
    assertEquals("LSM 900", created.getFields().get(2).getContent());
    assertEquals("Confocal microscope", created.getFields().get(3).getContent());
    assertEquals(
        "https://lab.example.org/instruments/lsm900", created.getFields().get(10).getContent());

    // state, publicUrl, providerUrl and linked are READ_ONLY on the DTO, so read the raw JSON
    JsonNode identifier = json(result).get("identifiers").get(0);
    assertEquals(handle, identifier.get("doi").asText());
    assertEquals("PIDINST_B2INST", identifier.get("doiType").asText());
    assertEquals("accepted", identifier.get("state").asText());
    assertTrue(identifier.get("linked").asBoolean());
    assertEquals("https://hdl.handle.net/" + handle, identifier.get("publicUrl").asText());
    assertEquals(
        "https://b2inst-test.example.org/records/" + recordId,
        identifier.get("providerUrl").asText());
  }

  @Test
  public void aSecondImportOfTheSamePidIsA409NamingTheInstrument() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument first =
        mvcUtils.getFromJsonResponseBody(
            importPid(anyUser, apiKey, handle, 201), ApiInstrument.class);

    MvcResult conflict = importPid(anyUser, apiKey, handle, 409);

    assertTrue(json(conflict).get("message").asText().contains(first.getGlobalId()));
  }

  @Test
  public void anUnknownPidIs404() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    importPid(anyUser, apiKey, "21.T11975/zzzzz-99999", 404);
  }

  @Test
  public void aLinkedIdentifierCannotBePublishedButCanBeDeletedInAnyState() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    MvcResult imported = importPid(anyUser, apiKey, handle, 201);
    long identifierId = json(imported).get("identifiers").get(0).get("id").asLong();

    mockMvc
        .perform(
            createBuilderForInventoryPostWithJSONBody(
                apiKey, "/identifiers/" + identifierId + "/publish", anyUser, "{}"))
        .andExpect(status().isUnprocessableEntity());

    MvcResult deleted =
        mockMvc
            .perform(
                MockMvcRequestBuilders.delete(
                        createInventoryUrl(API_VERSION.ONE, "/identifiers/" + identifierId))
                    .principal(createPrincipal(anyUser))
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals("true", deleted.getResponse().getContentAsString());
  }

  @Test
  public void bothEndpointsAreRefusedWhenNoPidinstProviderIsEnabled() throws Exception {
    setB2instEnabled("false");
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    mockMvc
        .perform(
            createBuilderForInventoryGet(
                API_VERSION.ONE, apiKey, "/pidinst/search?query=microscope", anyUser))
        .andExpect(status().isNotFound());
    importPid(anyUser, apiKey, handle, 404);
  }
}
