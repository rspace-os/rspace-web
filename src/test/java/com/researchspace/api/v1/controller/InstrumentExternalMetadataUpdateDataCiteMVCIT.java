package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.BindingResult;

/**
 * The on-save external metadata update against the REAL DataCite test API, which is the only thing
 * that can prove the assumption the DataCite half of RSDEV-1251 rests on: that {@code updateDoi},
 * which sends no {@code event}, rewrites a draft DOI's metadata and leaves it a draft.
 *
 * <p>Everything else about that path is mocked somewhere. {@code
 * InventoryIdentifierExternalUpdateServiceTest} proves the routing, and the B2INST MVCIT proves the
 * transaction boundary; neither sends the real serialized payload anywhere. {@code
 * DataCiteDoiAttributes} is serialized {@code @JsonInclude(NON_NULL)} (see ADR 0008), so null
 * object properties are omitted entirely, but the primitives are always on the wire, server-owned
 * ones included ({@code isActive}, {@code state}, {@code metadataVersion}, the counters). This test
 * is what says DataCite ignores those rather than acting on them.
 *
 * <p>Verified by hand against api.test.datacite.org in August 2026 before being written, so the
 * assertions describe observed behaviour: the DOI stayed {@code draft}, the titles, publisher and
 * publicationYear were applied, {@code metadataVersion} went 0 to 1, and {@code created}, {@code
 * source}, {@code prefix}, {@code suffix} and {@code doi} were untouched.
 *
 * <p>Nightly only, like the other real-connection identifier tests: it needs {@code
 * datacite.realConnectionTest.*} credentials and it mints a real test DOI, which it deletes again.
 */
@WebAppConfiguration
@EnabledIfSystemProperty(named = "nightly", matches = "(|true)")
public class InstrumentExternalMetadataUpdateDataCiteMVCIT extends API_MVC_InventoryTestBase {

  @Autowired private InventoryIdentifierApiManager identifierApiManager;
  @Autowired private SystemSettingsApiController settingsController;

  @Value("${datacite.realConnectionTest.username}")
  private String dataciteUsername;

  @Value("${datacite.realConnectionTest.password}")
  private String datacitePassword;

  @Value("${datacite.realConnectionTest.prefix}")
  private String datacitePrefix;

  private final BindingResult mockBindingResult = mock(BindingResult.class);
  private Object realB2instConnector;
  private ApiInventorySystemSettings.IdentifierSettings originalPidinstDataCiteSettings;
  private ApiInventorySystemSettings.IdentifierSettings originalB2instSettings;

  @BeforeEach
  public void setup() throws Exception {
    /*
     * An instrument takes the B2INST path whenever B2INST is configured AND enabled
     * (createUpdateWithNewDoi), which on a developer machine depends on local deployment settings.
     * Disabling it pins this test to the DataCite branch it is about, on both the manager that
     * registers the identifier and the service that later pushes to it.
     */
    B2instConnector b2instOff = mock(B2instConnector.class);
    when(b2instOff.isConfiguredAndEnabled()).thenReturn(false);
    realB2instConnector = ReflectionTestUtils.getField(identifierApiManager, "b2instConnector");
    ReflectionTestUtils.setField(identifierApiManager, "b2instConnector", b2instOff);
    super.setUp();
    // these are system properties in the shared dev database, so put them back afterwards
    originalPidinstDataCiteSettings =
        captureIdentifierSettings(settingsController, IdentifierType.PIDINST_DATACITE);
    // B2INST too: enabling one PIDINST provider disables the other, so this test turns B2INST off
    // as a side effect and has to turn it back on
    originalB2instSettings =
        captureIdentifierSettings(settingsController, IdentifierType.PIDINST_B2INST);
    setPidinstDataCiteEnabled(true);
  }

  @AfterEach
  public void teardown() throws Exception {
    /*
     * Both providers, and in this order. Enabling a PIDINST provider disables its sibling (see
     * updateInventorySettings), so this test switched B2INST off on the way in and must switch it
     * back on. DataCite goes first because restoring it is what leaves the enabled flag free for
     * B2INST to claim; doing it the other way round would re-disable B2INST.
     *
     * Leaving B2INST off would be its own bug, not a safe default: the developer whose database
     * this is would find their instrument PIDs quietly going to the wrong provider, or nowhere.
     */
    restoreIdentifierSettings(
        settingsController, IdentifierType.PIDINST_DATACITE, originalPidinstDataCiteSettings);
    restoreIdentifierSettings(
        settingsController, IdentifierType.PIDINST_B2INST, originalB2instSettings);
    // singletons in a Spring context cached across test classes
    ReflectionTestUtils.setField(identifierApiManager, "b2instConnector", realB2instConnector);
  }

  private void setPidinstDataCiteEnabled(boolean enabled) throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    IdentifierSettings settings = new IdentifierSettings();
    settings.setProvider(IdentifierType.PIDINST_DATACITE);
    settings.setEnabled(String.valueOf(enabled));
    settings.setServerUrl("https://api.test.datacite.org");
    settings.setUsername(dataciteUsername);
    settings.setPassword(datacitePassword);
    settings.setRepositoryPrefix(datacitePrefix);
    settingsController.updateInventorySettings(
        new MockHttpServletRequest(), settings, mockBindingResult, sysadmin);
  }

  @Test
  public void realConnectionSavingAnInstrumentRewritesItsDraftDoiAndLeavesItADraft()
      throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "before-datacite-update");

    MvcResult registered =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    "/identifiers",
                    anyUser,
                    "{ \"parentGlobalId\": \"" + instrument.getGlobalId() + "\" }"))
            .andReturn();
    assertNull(registered.getResolvedException());
    JsonNode registeredDoi =
        new ObjectMapper().readTree(registered.getResponse().getContentAsString());
    Long identifierId = registeredDoi.path("id").asLong();
    assertEquals("draft", registeredDoi.path("state").asText());
    assertEquals(IdentifierType.PIDINST_DATACITE.name(), registeredDoi.path("doiType").asText());

    MvcResult saved =
        this.mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey,
                    "/instruments/" + instrument.getId(),
                    anyUser,
                    "{ \"name\": \"after-datacite-update\" }"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(saved.getResolvedException());

    JsonNode identifier =
        new ObjectMapper()
            .readTree(saved.getResponse().getContentAsString())
            .path("identifiers")
            .path(0);
    JsonNode update = identifier.path("externalMetadataUpdate");
    assertEquals(
        "UPDATED",
        update.path("outcome").asText(),
        "the real DataCite API rejected the rebuilt payload: " + update);
    assertNotNull(update.path("reason").asText(null));
    /*
     * The heart of it. updateDoi sends no event, so the DOI must still be a draft afterwards: a
     * push that quietly published or registered the DOI would be unrecoverable, since only a draft
     * can be deleted.
     */
    assertEquals(
        "draft",
        identifier.path("state").asText(),
        "the on-save push must not move the DOI out of draft");

    // a still-draft DOI is deletable, which is itself the proof that it never left draft
    MvcResult deleted =
        this.mockMvc
            .perform(createBuilderForDelete(apiKey, "/identifiers/{id}", anyUser, identifierId))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(deleted.getResolvedException());
  }
}
