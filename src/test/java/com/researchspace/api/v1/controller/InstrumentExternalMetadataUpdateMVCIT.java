package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.InventoryIdentifierExternalUpdateService;
import com.researchspace.webapp.integrations.b2inst.B2instConnectionException;
import com.researchspace.webapp.integrations.b2inst.B2instConnectorDummy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.BindingResult;

/**
 * End-to-end check that saving an instrument pushes its remapped PIDINST metadata to the B2INST
 * draft registered for it, and that a failed push still leaves the instrument saved (RSDEV-1251,
 * ADR 0008). A {@link B2instConnectorDummy} stands in for B2INST at both seams: the identifier
 * manager (which registers the draft) and the update service (which pushes to it).
 *
 * <p>The "identifier mutated by this same save" exclusion is deliberately not exercised here: the
 * register, assign and delete markers on {@code ApiInventoryDOI} are {@code @JsonIgnore}, so no
 * request body can set them. They are only ever set in Java, by the identifier manager, which
 * re-enters {@code updateApiInstrument} below this controller seam - which is why those operations
 * cannot push at all. The filter itself is covered by {@code
 * InventoryIdentifierExternalUpdateServiceTest}.
 */
@WebAppConfiguration
public class InstrumentExternalMetadataUpdateMVCIT extends API_MVC_InventoryTestBase {

  @Autowired private InventoryIdentifierApiManager identifierApiManager;
  @Autowired private InventoryIdentifierExternalUpdateService externalUpdateService;
  @Autowired private SystemSettingsApiController settingsController;

  private final B2instConnectorDummy b2instDummy = new B2instConnectorDummy();
  private final BindingResult mockBindingResult = mock(BindingResult.class);
  private Object realConnectorOnManager;
  private Object realConnectorOnUpdateService;
  private ApiInventorySystemSettings.IdentifierSettings originalB2instSettings;
  private ApiInventorySystemSettings.IdentifierSettings originalPidinstDataCiteSettings;

  @Before
  public void setup() throws Exception {
    realConnectorOnManager = ReflectionTestUtils.getField(identifierApiManager, "b2instConnector");
    realConnectorOnUpdateService =
        ReflectionTestUtils.getField(externalUpdateService, "b2instConnector");
    ReflectionTestUtils.setField(identifierApiManager, "b2instConnector", b2instDummy);
    ReflectionTestUtils.setField(externalUpdateService, "b2instConnector", b2instDummy);
    super.setUp();
    // these are system properties in the shared dev database, so put them back afterwards
    originalB2instSettings =
        captureIdentifierSettings(settingsController, IdentifierType.PIDINST_B2INST);
    // DataCite too: enabling one PIDINST provider disables the other, so turning B2INST on below
    // switches the DataCite PIDINST provider off as a side effect and it has to be put back
    originalPidinstDataCiteSettings =
        captureIdentifierSettings(settingsController, IdentifierType.PIDINST_DATACITE);
    setB2instEnabled("true");
  }

  @After
  public void teardown() throws Exception {
    /*
     * Both providers, and in this order. Enabling a PIDINST provider disables its sibling (see
     * updateInventorySettings), so this test switched the DataCite PIDINST provider off on the way
     * in and must switch it back. B2INST goes first because restoring it is what leaves the enabled
     * flag free for DataCite to reclaim.
     *
     * Leaving DataCite off is not a harmless default: this is the shared dev database, and the
     * developer who owns it would find their instrument PIDs quietly going to the wrong provider,
     * or nowhere, with nothing in the diff to explain it.
     */
    restoreIdentifierSettings(
        settingsController, IdentifierType.PIDINST_B2INST, originalB2instSettings);
    restoreIdentifierSettings(
        settingsController, IdentifierType.PIDINST_DATACITE, originalPidinstDataCiteSettings);
    // both beans are singletons in a Spring context cached across test classes, so the dummy has to
    // be swapped back out or later MVC tests silently run against it
    ReflectionTestUtils.setField(identifierApiManager, "b2instConnector", realConnectorOnManager);
    ReflectionTestUtils.setField(
        externalUpdateService, "b2instConnector", realConnectorOnUpdateService);
  }

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

  private void registerB2instIdentifier(User user, String apiKey, String parentGlobalId)
      throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    "/identifiers",
                    user,
                    "{ \"parentGlobalId\": \"" + parentGlobalId + "\" }"))
            .andReturn();
    assertNull(result.getResolvedException());
  }

  private MvcResult renameInstrument(User user, String apiKey, Long instrumentId, String newName)
      throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey,
                    "/instruments/" + instrumentId,
                    user,
                    "{ \"name\": \"" + newName + "\" }"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    return result;
  }

  /*
   * externalMetadataUpdate is @JsonProperty READ_ONLY, so reading it off a deserialized DTO would
   * silently yield null and every assertion below would pass vacuously. The response JSON is the
   * only place it can be observed.
   */
  private JsonNode firstIdentifierOf(MvcResult result) throws Exception {
    JsonNode body = new ObjectMapper().readTree(result.getResponse().getContentAsString());
    JsonNode identifiers = body.path("identifiers");
    assertTrue(identifiers.isArray(), "expected an identifiers array");
    return identifiers.path(0);
  }

  @Test
  public void savingAnInstrumentPushesRemappedMetadataToItsB2instDraft() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "before-update");
    registerB2instIdentifier(anyUser, apiKey, instrument.getGlobalId());

    MvcResult result = renameInstrument(anyUser, apiKey, instrument.getId(), "after-update");

    JsonNode update = firstIdentifierOf(result).path("externalMetadataUpdate");
    assertTrue(update.path("succeeded").asBoolean(), update.toString());
    assertNotNull(update.path("reason").asText(null));
    // the push carries the instrument as it now is, not as it was when the draft was registered
    B2instDoi pushed = b2instDummy.getDoiUpdateSentToB2inst();
    assertNotNull(pushed, "no metadata was pushed to the B2INST draft");
    assertEquals("after-update", pushed.getMetadata().getName());
  }

  /** The instrument edit has already committed, so a provider failure is reported, not raised. */
  @Test
  public void aFailedPushStillLeavesTheInstrumentSaved() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "before-update");
    registerB2instIdentifier(anyUser, apiKey, instrument.getGlobalId());
    ReflectionTestUtils.setField(
        externalUpdateService,
        "b2instConnector",
        new B2instConnectorDummy() {
          @Override
          public B2instDraftRecord updateDraftDoi(String rid, B2instDoi doi) {
            throw new B2instConnectionException(
                "Error updating B2INST draft record " + rid, "Record is not editable.");
          }
        });

    MvcResult result = renameInstrument(anyUser, apiKey, instrument.getId(), "saved-anyway");

    JsonNode identifier = firstIdentifierOf(result);
    JsonNode update = identifier.path("externalMetadataUpdate");
    assertFalse(update.path("succeeded").asBoolean(), update.toString());
    assertTrue(
        update.path("reason").asText().contains("Record is not editable."), update.toString());
    // the save itself stands, and the identifier's own state is untouched
    assertEquals(
        "saved-anyway",
        new ObjectMapper()
            .readTree(result.getResponse().getContentAsString())
            .path("name")
            .asText());
    assertEquals("draft", identifier.path("state").asText());
  }

  @Test
  public void savingAnInstrumentWithNoIdentifierPushesNothing() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "no-identifier");

    MvcResult result = renameInstrument(anyUser, apiKey, instrument.getId(), "still-no-identifier");

    JsonNode body = new ObjectMapper().readTree(result.getResponse().getContentAsString());
    assertTrue(body.path("identifiers").isEmpty(), body.path("identifiers").toString());
    assertNull(b2instDummy.getDoiUpdateSentToB2inst());
  }

  /**
   * The reentrancy guarantee the whole design rests on (ADR 0008 item 4). Registering an identifier
   * updates the instrument too, but it does so by calling {@code updateApiInstrument} on the
   * MANAGER, below the controller seam, so it cannot push - and must not, since the metadata it
   * would resend is the metadata B2INST accepted a moment earlier.
   */
  @Test
  public void registeringAnIdentifierDoesNotPush() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "register-only");

    registerB2instIdentifier(anyUser, apiKey, instrument.getGlobalId());

    assertNotNull(b2instDummy.getDoiSentToB2inst(), "the draft should have been registered");
    assertNull(
        b2instDummy.getDoiUpdateSentToB2inst(),
        "registering must not also push an external metadata update");
  }

  /** The other half of the same guarantee: publish re-enters the manager, not this controller. */
  @Test
  public void publishingAnIdentifierDoesNotPush() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "publish-only");
    registerB2instIdentifier(anyUser, apiKey, instrument.getGlobalId());
    Long identifierId = identifierIdOf(anyUser, apiKey, instrument.getId());

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/identifiers/" + identifierId + "/publish", anyUser, "{}"))
            .andReturn();
    assertNull(result.getResolvedException());

    assertNull(b2instDummy.getDoiUpdateSentToB2inst());
  }

  /**
   * An owner transfer <em>should</em> push, and the controller calls the push on that endpoint:
   * {@code RspaceToExternalProviderAdapterImpl.ownerOf} maps {@code ownerContact} from the record
   * owner's email unconditionally, so an unpushed transfer leaves the registered record naming the
   * previous owner - one of the three examples RSDEV-1251 opens with.
   *
   * <p>It does not push today, and the reason is worth stating precisely because it looks like data
   * loss and is not. The identifier survives a transfer untouched: the row is still there, still
   * attached to the instrument, still {@code deleted = false}, with a single Envers revision (the
   * original insert), and the NEW owner sees it through the API. What changes is the caller's view.
   * Transferring drops the previous owner to {@code LIMITED_READ}, and {@code
   * InventoryApiManagerImpl.setOtherFieldsForOutgoingApiInventoryRecord} then applies {@code
   * clearPropertiesForLimitedView}, which blanks the DTO's lists - identifiers included.
   *
   * <p>So the transfer response, built for the user giving the instrument away, carries no
   * identifiers, and the push reads its candidates from that response. The push therefore finds
   * nothing to do. The fix is for the push to take the record's own identifiers rather than the
   * caller's permission-filtered view of them; until it does, this test pins the behaviour and the
   * reason, so nobody re-diagnoses it as a deletion.
   */
  @Test
  public void aTransferLeavesTheIdentifierIntactButOutOfTheTransferringUsersView()
      throws Exception {
    User owner = createInitAndLoginAnyUser();
    String ownerKey = createNewApiKeyForUser(owner);
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "owner-change");
    registerB2instIdentifier(owner, ownerKey, instrument.getGlobalId());
    assertEquals(
        1,
        identifierCountOf(ownerKey, owner, instrument.getId()),
        "precondition: the instrument starts with a registered identifier");
    User newOwner = createInitAndLoginAnyUser();
    String newOwnerKey = createNewApiKeyForUser(newOwner);
    logoutAndLoginAs(owner);

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    ownerKey,
                    "/instruments/" + instrument.getId() + "/actions/changeOwner",
                    owner,
                    "{ \"owner\": { \"username\": \"" + newOwner.getUsername() + "\" } }"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());

    JsonNode transferred = new ObjectMapper().readTree(result.getResponse().getContentAsString());
    assertEquals(
        newOwner.getUsername(),
        transferred.path("owner").path("username").asText(),
        "precondition: the transfer itself must have happened");

    // the identifier is NOT lost: the new owner still sees it
    logoutAndLoginAs(newOwner);
    assertEquals(
        1,
        identifierCountOf(newOwnerKey, newOwner, instrument.getId()),
        "the identifier survives the transfer and belongs to the new owner's view");

    // it is only the transferring user's own view that no longer carries it, because a transfer
    // leaves them with LIMITED_READ and the limited view blanks the DTO's lists
    logoutAndLoginAs(owner);
    assertEquals(
        0,
        transferred.path("identifiers").size(),
        "the transfer response is the previous owner's limited view, so it carries no identifiers");
    assertEquals(
        0,
        identifierCountOf(ownerKey, owner, instrument.getId()),
        "and the same limited view applies to their later reads");

    // and that is why nothing was pushed: the push reads the caller's view of the identifiers
    assertNull(
        b2instDummy.getDoiUpdateSentToB2inst(),
        "no push yet: the candidates come from the transfer response, which is limited");
  }

  private int identifierCountOf(String apiKey, User user, Long instrumentId) throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForInventoryGet(
                    API_VERSION.ONE, apiKey, "/instruments/" + instrumentId, user))
            .andReturn();
    assertNull(result.getResolvedException());
    return new ObjectMapper()
        .readTree(result.getResponse().getContentAsString())
        .path("identifiers")
        .size();
  }

  private Long identifierIdOf(User user, String apiKey, Long instrumentId) throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForInventoryGet(
                    API_VERSION.ONE, apiKey, "/instruments/" + instrumentId, user))
            .andReturn();
    assertNull(result.getResolvedException());
    return new ObjectMapper()
        .readTree(result.getResponse().getContentAsString())
        .path("identifiers")
        .path(0)
        .path("id")
        .asLong();
  }

  /**
   * The central promise of ADR 0008 item 2, and the one thing no other test would notice breaking:
   * the provider call must happen with no transaction open. The payload rebuild needs one (the
   * mapping adapter is {@code Propagation.MANDATORY}, so the B2INST success test above already
   * proves it is provided), but the HTTP exchange must not hold a pooled JDBC connection for its
   * duration. Moving the seam down into the manager, or annotating the service
   * {@code @Transactional}, would leave every other test here green and fail this one.
   */
  @Test
  public void theProviderCallRunsWithNoTransactionOpen() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "tx-boundary");
    registerB2instIdentifier(anyUser, apiKey, instrument.getGlobalId());
    AtomicBoolean transactionWasActive = new AtomicBoolean(true);
    ReflectionTestUtils.setField(
        externalUpdateService,
        "b2instConnector",
        new B2instConnectorDummy() {
          @Override
          public B2instDraftRecord updateDraftDoi(String rid, B2instDoi doi) {
            transactionWasActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return super.updateDraftDoi(rid, doi);
          }
        });

    MvcResult result = renameInstrument(anyUser, apiKey, instrument.getId(), "tx-boundary-renamed");

    assertTrue(
        firstIdentifierOf(result).path("externalMetadataUpdate").path("succeeded").asBoolean(),
        "the push should have happened at all");
    assertFalse(
        transactionWasActive.get(),
        "the B2INST call must run outside any transaction, so it cannot hold a JDBC connection");
  }
}
