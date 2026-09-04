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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryDOI.ApiExternalMetadataUpdate;
import com.researchspace.api.v1.model.ApiInventoryDOI.ApiExternalMetadataUpdate.Outcome;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.webapp.integrations.b2inst.B2instConnectionException;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import jakarta.ws.rs.NotFoundException;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    // The enablement gate sits in front of every push, so it is a precondition of almost every test
    // here rather than the subject of one. Lenient because the tests that never reach the gate (a
    // blank record id or state is filtered before it) would otherwise fail as unnecessary stubbing.
    // The gate itself is asserted by the two tests that switch a provider off.
    lenient().when(b2instConnector.isConfiguredAndEnabled()).thenReturn(true);
    lenient()
        .when(dataCiteConnector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST))
        .thenReturn(true);
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

  /** An identifier as the record itself carries it, which is where candidates must come from. */
  private DigitalObjectIdentifier entityIdentifier(
      IdentifierType type, String state, String providerRecordId) {
    DigitalObjectIdentifier doi =
        new DigitalObjectIdentifier(providerRecordId, "a title", "aPublicSuffix");
    doi.setId(7L);
    doi.setType(type);
    doi.setState(state);
    return doi;
  }

  /** The response a caller left with only LIMITED_READ gets: lists blanked, id intact. */
  private ApiInstrument limitedViewOf(Long instrumentId) {
    ApiInstrument limited = new ApiInstrument();
    limited.setId(instrumentId);
    limited.setPermittedActions(
        List.of(ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.LIMITED_READ));
    // an empty list, not null: clearPropertiesForLimitedView copies over a fresh instance whose
    // identifiers field already defaults to an empty ArrayList, so this is production's real shape
    limited.setIdentifiers(new java.util.ArrayList<>());
    return limited;
  }

  /**
   * The owner-transfer case, and the reason the push has to read the record rather than the
   * response. A transfer leaves the departing owner with LIMITED_READ, and {@code
   * clearPropertiesForLimitedView} blanks that response's lists, identifiers included. Taking
   * candidates from the response therefore skipped the push silently, and the registered record
   * kept the previous owner's contact address - one of the three drifts RSDEV-1251 exists to stop.
   */
  @Test
  void pushesFromTheRecordWhenTheCallersOwnViewCarriesNoIdentifiers() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());
    when(instrument.getActiveIdentifiers())
        .thenReturn(List.of(entityIdentifier(IdentifierType.PIDINST_B2INST, "draft", RID)));

    service.pushMetadataUpdates(limitedViewOf(INSTRUMENT_ID), user);

    verify(b2instConnector).updateDraftDoi(eq(RID), any(B2instDoi.class));
    verify(auditer).notify(any());
  }

  /**
   * And nothing is reported back to that caller, which is correct rather than a shortcoming: they
   * can no longer see the identifier at all, so there is nowhere to put an outcome and nothing they
   * could do with one.
   */
  @Test
  void reportsNothingToACallerWhoseViewHasNoIdentifierToDecorate() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());
    when(instrument.getActiveIdentifiers())
        .thenReturn(List.of(entityIdentifier(IdentifierType.PIDINST_B2INST, "draft", RID)));
    ApiInstrument limited = limitedViewOf(INSTRUMENT_ID);

    service.pushMetadataUpdates(limited, user);

    verify(b2instConnector).updateDraftDoi(eq(RID), any(B2instDoi.class));
    assertTrue(limited.getIdentifiers().isEmpty(), "no outcome can be attached to a blanked list");
  }

  /**
   * The class's whole safety argument holds only if this transaction is the service's own. Under
   * the default {@code PROPAGATION_REQUIRED} the template silently joins a caller's transaction
   * instead: {@code setReadOnly} is then ignored, the boundary never closes so the provider HTTP
   * exchange pins a pooled JDBC connection for its whole duration, and because the mapping adapter
   * is {@code @Transactional(propagation = MANDATORY)} a build failure marks that outer transaction
   * rollback-only, losing the instrument save to {@code UnexpectedRollbackException}. Asserting the
   * demanded definition rather than the field keeps this true however the demarcation is written.
   */
  @Test
  void theMetadataRebuildDemandsItsOwnReadOnlyTransaction() {
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    service.setTransactionManager(txManager);
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());

    service.pushMetadataUpdates(
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "draft", RID)), user);

    // Several boundaries are opened now - one to classify, then one per identifier build - and the
    // argument applies to every one of them, so assert all of them rather than the first.
    ArgumentCaptor<TransactionDefinition> definition =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(txManager, atLeastOnce()).getTransaction(definition.capture());
    assertFalse(definition.getAllValues().isEmpty(), "a boundary must actually be opened");
    for (TransactionDefinition opened : definition.getAllValues()) {
      assertTrue(opened.isReadOnly(), "nothing in the rebuild may write");
      assertEquals(
          TransactionDefinition.PROPAGATION_REQUIRES_NEW,
          opened.getPropagationBehavior(),
          "must suspend a caller's transaction rather than join it");
    }
  }

  /**
   * The class's contract is that it is called with no transaction open, and until now that was only
   * documented. It is reachable with one open: bulk {@code CHANGE_OWNER} with {@code
   * rollbackOnError} runs the whole batch in one transaction ({@code
   * InventoryBulkOperationApiManager}) and calls back into {@code
   * InstrumentsApiController.changeInstrumentOwner}, which pushes.
   *
   * <p>Pushing from there would be wrong in a way always-push cannot heal: a later record in the
   * batch fails, the owner change rolls back locally, and the provider is left holding an owner
   * RSpace no longer has. It would also make the provider exchange hold the caller's connection
   * while REQUIRES_NEW suspends it. So the push declines rather than trying, and the metadata
   * drifts until the next ordinary save, which is the recoverable direction.
   */
  @Test
  void declinesToPushWhenTheCallerAlreadyHasATransactionOpen() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      service.pushMetadataUpdates(
          savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "draft", RID)), user);
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    verify(b2instConnector, never()).updateDraftDoi(anyString(), any(B2instDoi.class));
    verifyNoInteractions(instrumentApiMgr, rspaceToExternalProviderAdapter, auditer);
  }

  @Test
  void pushesADraftDataCiteDoiThroughThePidinstClient() {
    expectPayloadBuildAndMessages();
    DataCiteDoi rebuilt = new DataCiteDoi();
    when(rspaceToExternalProviderAdapter.buildDataCiteDoi(
            any(ApiInventoryDOI.class), eq(instrument)))
        .thenReturn(rebuilt);
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_DATACITE, "draft", DOI);

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    verify(dataCiteConnector).updateDoi(rebuilt, InventorySettingType.PIDINST);
    verifyNoInteractions(b2instConnector);
    assertEquals(Outcome.UPDATED, doi.getExternalMetadataUpdate().getOutcome());
  }

  /**
   * The type x state matrix that must not be pushed, and must say so. A record frozen by its own
   * state (an accepted B2INST review, a DataCite DOI past draft) still gets an outcome, because the
   * acceptance criteria ask for a plain explanation of why nothing happened rather than silence
   * that is indistinguishable from having no identifier at all.
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
    "PIDINST_DATACITE,registered"
  })
  void explainsWhyAnIdentifierFrozenByItsStateWasNotPushed(String type, String state) {
    when(messages.getMessage(anyString(), any(Object[].class)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0)
                    + " "
                    + Arrays.toString(invocation.getArgument(1, Object[].class)));
    ApiInventoryDOI doi = identifier(IdentifierType.valueOf(type), state, RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    verify(b2instConnector, never()).updateDraftDoi(anyString(), any(B2instDoi.class));
    verify(dataCiteConnector, never()).updateDoi(any(DataCiteDoi.class), any());
    verifyNoInteractions(rspaceToExternalProviderAdapter, auditer);
    ApiExternalMetadataUpdate outcome = doi.getExternalMetadataUpdate();
    assertNotNull(outcome, "a frozen record must still be explained");
    assertEquals(Outcome.NOT_UPDATABLE, outcome.getOutcome());
    // provider-specific, because the two are frozen for different reasons. The raw provider state
    // is deliberately not interpolated, so it is not asserted.
    String expectedKey =
        IdentifierType.PIDINST_B2INST.name().equals(type)
            ? "externalUpdateNotPossibleB2inst"
            : "externalUpdateNotPossibleDataCite";
    assertTrue(outcome.getReason().contains(expectedKey), outcome.getReason());
    assertTrue(outcome.getReason().contains(providerNameFor(type)), outcome.getReason());
  }

  private String providerNameFor(String type) {
    return IdentifierType.PIDINST_B2INST.name().equals(type) ? "B2INST" : "DataCite";
  }

  /**
   * An IGSN is the one exception: it stays silent. An instrument should never carry one, so there
   * is nothing for a user to act on and no PIDINST record the sentence could be about.
   */
  @ParameterizedTest
  @ValueSource(strings = {"draft", "findable"})
  void skipsAnIgsnWithoutSayingAnything(String state) {
    ApiInventoryDOI doi = identifier(IdentifierType.IGSN_DATACITE, state, RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    verifyNoInteractions(rspaceToExternalProviderAdapter, auditer);
    assertNull(doi.getExternalMetadataUpdate());
  }

  /**
   * The push rides on an ordinary save, so a switched-off integration must be silent rather than
   * put a failure on every save of every instrument that still holds a draft. Switching PIDINST
   * provider disables the sibling automatically, which is what makes this routine rather than
   * exotic.
   */
  @Test
  void saysNothingWhenTheB2instIntegrationIsSwitchedOff() {
    when(b2instConnector.isConfiguredAndEnabled()).thenReturn(false);
    when(instrumentApiMgr.getIfExists(INSTRUMENT_ID)).thenReturn(instrument);
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    verify(b2instConnector, never()).updateDraftDoi(anyString(), any(B2instDoi.class));
    // the record is read before the provider is consulted, so only the push must not happen
    verifyNoInteractions(rspaceToExternalProviderAdapter, auditer);
    assertNull(doi.getExternalMetadataUpdate());
  }

  @Test
  void saysNothingWhenTheDataCiteIntegrationIsSwitchedOff() {
    when(dataCiteConnector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST))
        .thenReturn(false);
    when(instrumentApiMgr.getIfExists(INSTRUMENT_ID)).thenReturn(instrument);
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_DATACITE, "draft", DOI);

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    verify(dataCiteConnector, never()).updateDoi(any(DataCiteDoi.class), any());
    verifyNoInteractions(rspaceToExternalProviderAdapter, auditer);
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
    B2instDoi rebuilt = new B2instDoi();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(rebuilt);
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, state, RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    verify(b2instConnector).updateDraftDoi(RID, rebuilt);
    // the fast path: a populated response list is complete, so the record's own list is not read
    verify(instrument, never()).getActiveIdentifiers();
    assertEquals(Outcome.UPDATED, doi.getExternalMetadataUpdate().getOutcome());
    assertTrue(
        doi.getExternalMetadataUpdate().getReason().contains("externalUpdated"),
        doi.getExternalMetadataUpdate().getReason());
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
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, state, RID)), user);

    verify(b2instConnector).updateDraftDoi(eq(RID), any(B2instDoi.class));
  }

  @Test
  void skipsAnIdentifierWithNoProviderRecordId() {
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", null);

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    verifyNoInteractions(b2instConnector, rspaceToExternalProviderAdapter);
  }

  /**
   * {@code getIfExists} throws rather than returning null, so a save racing a deletion propagates
   * to the controller's guard, which logs it and returns the instrument unannotated. Pinned here
   * because the service used to carry an unreachable null branch that claimed otherwise.
   */
  @Test
  void anInstrumentDeletedUnderTheSavePropagatesToTheGuardingCaller() {
    when(instrumentApiMgr.getIfExists(INSTRUMENT_ID))
        .thenThrow(new NotFoundException("No Instrument or InstrumentTemplate found with id: 1"));
    ApiInstrument saved =
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "draft", RID));

    assertThrows(NotFoundException.class, () -> service.pushMetadataUpdates(saved, user));

    // not verifyNoInteractions: the enablement gate legitimately asks the connector first
    verify(b2instConnector, never()).updateDraftDoi(anyString(), any(B2instDoi.class));
    verifyNoInteractions(auditer);
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
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "draft", RID)), user);

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

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    ApiExternalMetadataUpdate outcome = doi.getExternalMetadataUpdate();
    assertEquals(Outcome.FAILED, outcome.getOutcome());
    assertTrue(outcome.getReason().contains("externalUpdateFailed"), outcome.getReason());
    assertTrue(outcome.getReason().contains("Record is not editable."), outcome.getReason());
  }

  /**
   * Nothing upstream bounds the provider's own wording, and this sentence is shown to a user in a
   * toast that waits to be dismissed, so it is abbreviated at the width the log path already uses.
   */
  @Test
  void abbreviatesAnOversizedProviderDetailBeforeItReachesTheUser() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    String oversized = "x".repeat(2000);
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());
    when(b2instConnector.updateDraftDoi(anyString(), any(B2instDoi.class)))
        .thenThrow(new B2instConnectionException("Error updating B2INST draft record", oversized));
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    String reason = doi.getExternalMetadataUpdate().getReason();
    assertFalse(reason.contains(oversized), "the provider's full wording reached the user");
    assertTrue(reason.length() < 700, reason.length() + " characters: " + reason);
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

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    ApiExternalMetadataUpdate outcome = doi.getExternalMetadataUpdate();
    assertEquals(Outcome.FAILED, outcome.getOutcome());
    assertFalse(outcome.getReason().contains("credentials"), outcome.getReason());
  }

  @Test
  void auditsAFailedAttempt() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());
    when(b2instConnector.updateDraftDoi(anyString(), any(B2instDoi.class)))
        .thenThrow(new B2instConnectionException("dev detail", "Record is not editable."));

    service.pushMetadataUpdates(
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "draft", RID)), user);

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

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    verifyNoInteractions(b2instConnector, dataCiteConnector, rspaceToExternalProviderAdapter);
    assertNull(doi.getExternalMetadataUpdate());
  }

  @Test
  void skipsAnIdentifierWithNoState() {
    ApiInventoryDOI doi = identifier(IdentifierType.PIDINST_B2INST, null, RID);

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    verifyNoInteractions(b2instConnector, rspaceToExternalProviderAdapter);
  }

  /**
   * Nothing to push must be a quiet return, not an NPE on the way out of a successful save. These
   * views carry no permitted actions, so they could be filtered ones and the record is consulted;
   * it has nothing either.
   */
  @Test
  void toleratesAnInstrumentWithNothingToPush() {
    when(instrumentApiMgr.getIfExists(INSTRUMENT_ID)).thenReturn(instrument);
    when(instrument.getActiveIdentifiers()).thenReturn(List.of());

    service.pushMetadataUpdates(savedInstrumentWith(), user);

    ApiInstrument noIdentifiersAtAll = new ApiInstrument();
    noIdentifiersAtAll.setId(INSTRUMENT_ID);
    noIdentifiersAtAll.setIdentifiers(null);
    service.pushMetadataUpdates(noIdentifiersAtAll, user);

    verifyNoInteractions(b2instConnector, dataCiteConnector, rspaceToExternalProviderAdapter);
  }

  /**
   * The ordinary save must not pay for the transfer fix. A caller who can see the whole record is
   * shown every identifier it has, so an empty list there is conclusive and the record is not read.
   */
  @Test
  void doesNotReadTheRecordWhenAnUnfilteredViewAlreadyShowsNoIdentifiers() {
    ApiInstrument fullView = new ApiInstrument();
    fullView.setId(INSTRUMENT_ID);
    fullView.setPermittedActions(
        List.of(
            ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.READ,
            ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.UPDATE));
    fullView.setIdentifiers(List.of());

    service.pushMetadataUpdates(fullView, user);

    verifyNoInteractions(
        instrumentApiMgr,
        b2instConnector,
        dataCiteConnector,
        rspaceToExternalProviderAdapter,
        auditer);
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

    service.pushMetadataUpdates(savedInstrumentWith(accepted, draft), user);

    verify(b2instConnector).updateDraftDoi(eq(RID), any(B2instDoi.class));
    verify(b2instConnector, never()).updateDraftDoi(eq("acc-ept01"), any(B2instDoi.class));
    assertEquals(Outcome.NOT_UPDATABLE, accepted.getExternalMetadataUpdate().getOutcome());
    assertEquals(Outcome.UPDATED, draft.getExternalMetadataUpdate().getOutcome());
  }

  /** Success is audited too, not only failure: the audit trail is the record of every attempt. */
  @Test
  void auditsASuccessfulAttempt() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenReturn(new B2instDoi());

    service.pushMetadataUpdates(
        savedInstrumentWith(identifier(IdentifierType.PIDINST_B2INST, "draft", RID)), user);

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

    service.pushMetadataUpdates(savedInstrumentWith(doi), user);

    String reason = doi.getExternalMetadataUpdate().getReason();
    assertFalse(reason.contains("\n"), reason);
    assertTrue(reason.contains("Record is not editable."), reason);
    assertEquals(reason.trim(), reason);
  }

  /**
   * A mapping failure is isolated to its own identifier, matching the push. The other identifiers
   * of the same instrument still go, and the failed one carries a reason instead of the whole batch
   * vanishing with nothing said. Not audited: nothing was sent.
   */
  @Test
  void aPayloadRebuildFailureIsReportedOnItsOwnIdentifierAndTheRestStillGo() {
    expectPayloadBuildAndMessages();
    expectServerUrl();
    ApiInventoryDOI broken = identifier(IdentifierType.PIDINST_B2INST, "draft", "br-oken01");
    broken.setId(9L);
    ApiInventoryDOI good = identifier(IdentifierType.PIDINST_B2INST, "draft", RID);
    when(rspaceToExternalProviderAdapter.buildB2instDoi(eq(instrument), anyString()))
        .thenThrow(new IllegalStateException("PIDINST providers accept only Instrument records"))
        .thenReturn(new B2instDoi());

    service.pushMetadataUpdates(savedInstrumentWith(broken, good), user);

    verify(b2instConnector).updateDraftDoi(eq(RID), any(B2instDoi.class));
    verify(b2instConnector, never()).updateDraftDoi(eq("br-oken01"), any(B2instDoi.class));
    assertEquals(Outcome.FAILED, broken.getExternalMetadataUpdate().getOutcome());
    assertTrue(
        broken.getExternalMetadataUpdate().getReason().contains("externalUpdateFailed"),
        broken.getExternalMetadataUpdate().getReason());
    assertEquals(Outcome.UPDATED, good.getExternalMetadataUpdate().getOutcome());
    verify(auditer, times(1)).notify(any());
  }
}
