package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryDOI.ApiExternalMetadataUpdate;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.webapp.integrations.b2inst.B2instConnectionException;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Unit tests for the on-save external PIDINST metadata update (RSDEV-1251, ADR 0008): which
 * identifiers qualify, what is sent, and that a provider failure is reported rather than thrown.
 */
@ExtendWith(MockitoExtension.class)
class InventoryIdentifierExternalUpdateServiceTest {

  private static final Long INSTRUMENT_ID = 42L;
  private static final String RID = "k2j9p-7yh21";
  private static final String DOI = "10.82316/n1c0-t35t";
  private static final String SERVER_URL = "https://rspace.example.org";

  @Mock private InstrumentEntityApiManager instrumentApiMgr;
  @Mock private RspaceToExternalProviderAdapter rspaceToExternalProviderAdapter;
  @Mock private B2instConnector b2instConnector;
  @Mock private DataCiteConnector dataCiteConnector;
  @Mock private IPropertyHolder properties;
  @Mock private MessageSourceUtils messages;
  @Mock private AuditTrailService auditer;

  @InjectMocks private InventoryIdentifierExternalUpdateService service;

  private final User user = new User("jbloggs");
  private final Instrument instrument = mock(Instrument.class);

  /**
   * A no-op boundary. These tests are about what the service does inside the transaction and after
   * it, not about the transaction manager, and a real one would need a data source.
   */
  private static class NoOpTransactionManager implements PlatformTransactionManager {
    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {}

    @Override
    public void rollback(TransactionStatus status) {}
  }

  @BeforeEach
  void setUp() {
    service.setTransactionManager(new NoOpTransactionManager());
  }

  /**
   * Stubbed only where a push is expected. Left out of {@code setUp} on purpose: a test asserting
   * that nothing is pushed would then carry an unused stub, and strict stubbing would report that
   * rather than the test's own claim. Same reason the server URL sits in {@link
   * #expectServerUrl()}: only the B2INST path resolves a landing page.
   */
  private void expectPayloadBuildAndMessages() {
    when(instrumentApiMgr.getIfExists(INSTRUMENT_ID)).thenReturn(instrument);
    when(messages.getMessage(anyString(), any(Object[].class)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0)
                    + " "
                    + Arrays.toString(invocation.getArgument(1, Object[].class)));
  }

  private void expectServerUrl() {
    when(properties.getServerUrl()).thenReturn(SERVER_URL);
  }

  private ApiInstrument savedInstrumentWith(ApiInventoryDOI... identifiers) {
    ApiInstrument saved = new ApiInstrument();
    saved.setId(INSTRUMENT_ID);
    saved.setIdentifiers(List.of(identifiers));
    return saved;
  }

  private ApiInventoryDOI identifier(IdentifierType type, String state, String providerRecordId) {
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.setId(7L);
    doi.setDoiType(type.name());
    doi.setState(state);
    doi.setDoi(providerRecordId);
    doi.setRsPublicId("aPublicSuffix");
    return doi;
  }

  private ApiInstrument requestMarking(ApiInventoryDOI marker) {
    ApiInstrument incoming = new ApiInstrument();
    incoming.setId(INSTRUMENT_ID);
    incoming.setIdentifiers(List.of(marker));
    return incoming;
  }

  @Test
  void pushesADraftB2instRecordAndReportsSuccess() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    B2instDoi rebuilt = new B2instDoi();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(rebuilt);
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    verify(b2instConnector).updateDraftDoi(RID, rebuilt);
    ApiExternalMetadataUpdate outcome = doi.getExternalMetadataUpdate();
    assertNotNull(outcome);
    assertTrue(outcome.isSucceeded());
    assertTrue(outcome.getReason().contains("externalUpdated"), outcome.getReason());
  }

  /** An InvenioRDM draft stays writable while its community review is open. */
  @Test
  void pushesASubmittedB2instRecord() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "submitted", RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    verify(b2instConnector).updateDraftDoi(eq(RID), any(B2instDoi.class));
    assertTrue(doi.getExternalMetadataUpdate().isSucceeded());
  }

  @Test
  void pushesADraftDataCiteDoiThroughThePidinstClient() {
    expectPayloadBuildAndMessages();
    DataCiteDoi rebuilt = new DataCiteDoi();
    when(rspaceToExternalProviderAdapter.buildDataCiteDoi(
            any(ApiInventoryDOI.class), eq(instrument)))
        .thenReturn(rebuilt);
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_DATACITE, "draft", DOI);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    verify(dataCiteConnector).updateDoi(rebuilt, InventorySettingType.PIDINST);
    verifyNoInteractions(b2instConnector);
    assertTrue(doi.getExternalMetadataUpdate().isSucceeded());
  }

  /**
   * The whole type x state matrix that must NOT be pushed. A published record cannot be rewritten
   * in place at either provider, and an IGSN has no business on an instrument at all, so it is
   * skipped defensively rather than mapped through the instrument adapter.
   *
   * <p>The B2INST review states that are NOT here - created, cancelled, declined, expired - all
   * still have a writable draft, and are covered as positive cases below.
   */
  @ParameterizedTest
  @CsvSource({
    "PIDINST_B2INST,accepted",
    "PIDINST_B2INST,ACCEPTED",
    "PIDINST_DATACITE,submitted",
    "PIDINST_DATACITE,findable",
    "PIDINST_DATACITE,registered",
    "IGSN_DATACITE,draft",
    "IGSN_DATACITE,findable"
  })
  void skipsAnIdentifierThatIsNotInAnUpdatableState(String type, String state) {
    ApiInventoryDOI doi = identifier(IdentifierType.valueOf(type), state, RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    verifyNoInteractions(b2instConnector, dataCiteConnector, rspaceToExternalProviderAdapter);
    assertNull(doi.getExternalMetadataUpdate());
  }

  /**
   * Every B2INST state that still has a writable draft behind it, which is all of them bar {@code
   * accepted}. {@code refreshIdentifier} stores the community review's status verbatim, so these
   * are states an identifier really can sit in, and each was confirmed to accept a full-replace
   * draft PUT against b2inst-test.gwdg.de (August 2026): the narrower draft-and-submitted rule
   * would have let a cancelled or never-submitted record drift for good.
   */
  @ParameterizedTest
  @ValueSource(strings = {"draft", "created", "submitted", "cancelled", "declined", "expired"})
  void pushesEveryB2instStateThatStillHasAWritableDraft(String state) {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, state, RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    verify(b2instConnector).updateDraftDoi(eq(RID), any(B2instDoi.class));
    assertTrue(doi.getExternalMetadataUpdate().isSucceeded());
  }

  /**
   * The state column is free-form text, written by RSpace for some transitions and copied verbatim
   * from a provider for others, so a differently-cased value is worth updating rather than silently
   * skipping.
   */
  @ParameterizedTest
  @ValueSource(strings = {"DRAFT", "Draft", " draft "})
  void stateComparisonIgnoresCaseAndSurroundingSpace(String state) {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());

    service.pushMetadataUpdates(
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, state, RID)),
        new ApiInstrument(),
        user);

    verify(b2instConnector).updateDraftDoi(eq(RID), any(B2instDoi.class));
  }

  @Test
  void skipsAnIdentifierTheSameSaveAssigned() {
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);
    ApiInventoryDOI marker = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);
    marker.setAssignIdentifierRequest(true);

    service.pushMetadataUpdates(savedInstrumentWith(doi), requestMarking(marker), user);

    verifyNoInteractions(b2instConnector, rspaceToExternalProviderAdapter);
    assertNull(doi.getExternalMetadataUpdate());
  }

  @Test
  void skipsAnIdentifierTheSameSaveRequestedTheDeletionOf() {
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);
    ApiInventoryDOI marker = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);
    marker.setDeleteIdentifierRequest(true);

    service.pushMetadataUpdates(savedInstrumentWith(doi), requestMarking(marker), user);

    verifyNoInteractions(b2instConnector, rspaceToExternalProviderAdapter);
  }

  @Test
  void skipsAnIdentifierTheSameSaveRegistered() {
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);
    ApiInventoryDOI marker = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);
    marker.setRegisterIdentifierRequest(true);

    service.pushMetadataUpdates(savedInstrumentWith(doi), requestMarking(marker), user);

    verifyNoInteractions(b2instConnector, rspaceToExternalProviderAdapter);
  }

  @Test
  void skipsAnIdentifierWithNoProviderRecordId() {
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", null);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    verifyNoInteractions(b2instConnector, rspaceToExternalProviderAdapter);
  }

  @Test
  void doesNothingWhenTheInstrumentIsNoLongerReadable() {
    when(instrumentApiMgr.getIfExists(INSTRUMENT_ID)).thenReturn(null);

    service.pushMetadataUpdates(
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "draft", RID)),
        new ApiInstrument(),
        user);

    verifyNoInteractions(b2instConnector, rspaceToExternalProviderAdapter);
  }

  /**
   * Registers the identifier's own public landing page, read from {@code rsPublicId}: that is where
   * a DTO built from a persisted identifier carries the public link suffix, while {@code
   * publicLinkSuffix} is only ever set for a brand-new one.
   */
  @Test
  void offersTheIdentifiersOwnPublicLandingPageToTheMapping() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());

    service.pushMetadataUpdates(
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "draft", RID)),
        new ApiInstrument(),
        user);

    ArgumentCaptor<String> landingPage = ArgumentCaptor.forClass(String.class);
    verify(rspaceToExternalProviderAdapter).buildB2instDoi(eq(instrument), landingPage.capture());
    assertEquals(SERVER_URL + "/public/inventory/aPublicSuffix", landingPage.getValue());
  }

  /**
   * The save has already committed, so a provider failure is reported on the identifier and never
   * rethrown. The provider's own words come through, because only B2INST can say whether the record
   * is locked, gone, or was rejected outright.
   */
  @Test
  void reportsAB2instFailureWithTheProvidersOwnWordsAndDoesNotThrow() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());
    when(b2instConnector.updateDraftDoi(anyString(), any(B2instDoi.class)))
        .thenThrow(
            new B2instConnectionException(
                "Error updating B2INST draft record: internal detail", "Record is not editable."));
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    ApiExternalMetadataUpdate outcome = doi.getExternalMetadataUpdate();
    assertFalse(outcome.isSucceeded());
    assertTrue(outcome.getReason().contains("externalUpdateFailed"), outcome.getReason());
    assertTrue(outcome.getReason().contains("Record is not editable."), outcome.getReason());
  }

  /**
   * DataCite's exception carries only a developer sentence - the client's canned messages ask the
   * reader about repository prefixes and credentials - so no detail is interpolated for that
   * provider.
   */
  @Test
  void reportsADataCiteFailureWithoutLeakingTheDeveloperMessage() {
    expectPayloadBuildAndMessages();
    when(rspaceToExternalProviderAdapter.buildDataCiteDoi(
            any(ApiInventoryDOI.class), eq(instrument)))
        .thenReturn(new DataCiteDoi());
    when(dataCiteConnector.updateDoi(any(DataCiteDoi.class), eq(InventorySettingType.PIDINST)))
        .thenThrow(
            new DataCiteConnectionException(
                "NotFound error when connecting to DataCite Members API. Are connection credentials"
                    + " correct?",
                null));
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_DATACITE, "draft", DOI);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    ApiExternalMetadataUpdate outcome = doi.getExternalMetadataUpdate();
    assertFalse(outcome.isSucceeded());
    assertFalse(outcome.getReason().contains("credentials"), outcome.getReason());
  }

  @Test
  void auditsEveryAttempt() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());
    when(b2instConnector.updateDraftDoi(anyString(), any(B2instDoi.class)))
        .thenThrow(new B2instConnectionException("dev detail", "Record is not editable."));

    service.pushMetadataUpdates(
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "draft", RID)),
        new ApiInstrument(),
        user);

    ArgumentCaptor<HistoricalEvent> event = ArgumentCaptor.forClass(HistoricalEvent.class);
    verify(auditer).notify(event.capture());
    GenericEvent audited = (GenericEvent) event.getValue();
    assertEquals(AuditAction.WRITE, audited.getAuditAction());
    assertEquals(instrument, audited.getAuditedObject());
    assertTrue(audited.getDescription().contains("Record is not editable."));
  }

  /**
   * {@code doiType} is not guaranteed to hold an {@code IdentifierType} name: {@code
   * applyChangesToDatabaseDOI} documents the {@code "dois"} JSON:API literal arriving from DataCite
   * responses. An unrecognisable provider must skip, never fall through to a default provider.
   */
  @ParameterizedTest
  @ValueSource(strings = {"dois", "", "PIDINST_SOMETHING_ELSE"})
  void skipsAnIdentifierWhoseTypeIsNotAKnownProvider(String doiType) {
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);
    doi.setDoiType(doiType);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    verifyNoInteractions(b2instConnector, dataCiteConnector, rspaceToExternalProviderAdapter);
    assertNull(doi.getExternalMetadataUpdate());
  }

  @Test
  void skipsAnIdentifierWithNoState() {
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, null, RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    verifyNoInteractions(b2instConnector, rspaceToExternalProviderAdapter);
  }

  /** Nothing to push must be a quiet return, not an NPE on the way out of a successful save. */
  @Test
  void toleratesAnInstrumentWithNothingToPush() {
    service.pushMetadataUpdates(savedInstrumentWith(), new ApiInstrument(), user);

    ApiInstrument noIdentifiersAtAll = new ApiInstrument();
    noIdentifiersAtAll.setId(INSTRUMENT_ID);
    noIdentifiersAtAll.setIdentifiers(null);
    service.pushMetadataUpdates(noIdentifiersAtAll, null, user);

    verifyNoInteractions(
        instrumentApiMgr, b2instConnector, dataCiteConnector, rspaceToExternalProviderAdapter);
  }

  /**
   * A record can hold more than one identifier row, and eligibility is per identifier: the accepted
   * one is left alone while the draft beside it is still pushed and decorated.
   */
  @Test
  void pushesOnlyTheEligibleIdentifierOfAMixedSet() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());
    ApiInventoryDOI accepted = identifier(IdentifierType.PIDINST_B2INST, "accepted", "acc-ept01");
    accepted.setId(8L);
    ApiInventoryDOI draft = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);

    service.pushMetadataUpdates(savedInstrumentWith(accepted, draft), new ApiInstrument(), user);

    verify(b2instConnector).updateDraftDoi(eq(RID), any(B2instDoi.class));
    verify(b2instConnector, never()).updateDraftDoi(eq("acc-ept01"), any(B2instDoi.class));
    assertNull(accepted.getExternalMetadataUpdate());
    assertTrue(draft.getExternalMetadataUpdate().isSucceeded());
  }

  /** Success is audited too, not only failure: the audit trail is the record of every attempt. */
  @Test
  void auditsASuccessfulAttempt() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());

    service.pushMetadataUpdates(
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "draft", RID)),
        new ApiInstrument(),
        user);

    ArgumentCaptor<HistoricalEvent> event = ArgumentCaptor.forClass(HistoricalEvent.class);
    verify(auditer).notify(event.capture());
    assertEquals(AuditAction.WRITE, event.getValue().getAuditAction());
    assertEquals(instrument, event.getValue().getAuditedObject());
    assertTrue(event.getValue().getDescription().contains("externalUpdated"));
  }

  /**
   * A provider message arrives as whatever the provider wrote, including line breaks and the gap
   * left where a missing detail would have gone. The reason ends up in a JSON string a UI renders,
   * so it is normalised to one line.
   */
  @Test
  void collapsesWhitespaceAndLineBreaksInTheReason() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());
    when(b2instConnector.updateDraftDoi(anyString(), any(B2instDoi.class)))
        .thenThrow(new B2instConnectionException("dev detail", "Record is\n\nnot   editable.\n"));
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), new ApiInstrument(), user);

    String reason = doi.getExternalMetadataUpdate().getReason();
    assertFalse(reason.contains("\n"), reason);
    assertTrue(reason.contains("Record is not editable."), reason);
    assertEquals(reason.trim(), reason);
  }

  /**
   * A payload rebuild that blows up is deliberately NOT swallowed here. The mapping adapter runs
   * inside the transaction and a failure there means the push never happened for any identifier, so
   * the caller must see it; {@code InstrumentsApiController} is the guard that keeps it out of the
   * response, which {@code InstrumentsApiControllerTest} pins.
   */
  @Test
  void aPayloadRebuildFailurePropagatesToTheGuardingCaller() {
    when(instrumentApiMgr.getIfExists(INSTRUMENT_ID)).thenReturn(instrument);
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenThrow(new IllegalStateException("PIDINST providers accept only Instrument records"));
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);
    ApiInstrument saved = savedInstrumentWith(doi);

    assertThrows(
        IllegalStateException.class,
        () -> service.pushMetadataUpdates(saved, new ApiInstrument(), user));

    verifyNoInteractions(b2instConnector, auditer);
    assertNull(doi.getExternalMetadataUpdate());
  }

  @Test
  void auditsNothingWhenNothingWasEligible() {
    service.pushMetadataUpdates(
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "accepted", RID)),
        new ApiInstrument(),
        user);

    verify(auditer, never()).notify(any());
  }
}
