package com.researchspace.service.inventory.impl;

import static com.researchspace.service.inventory.PidinstLookupManager.MIN_QUERY_LENGTH;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.api.v1.model.ApiPidinstRecord;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.b2inst.model.metadata.B2instIdentifier;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.b2inst.model.metadata.B2instManufacturer;
import com.researchspace.b2inst.model.metadata.B2instOwner;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRecordLinks;
import com.researchspace.b2inst.model.response.B2instSearchResult;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiAttributes;
import com.researchspace.datacite.model.DataCiteDoiSearchResult;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.PidinstAlreadyLinkedException;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PidinstLookupManagerImplTest {

  private static final String HANDLE = "21.T11975/abcde-12345";

  @Mock private B2instConnector b2instConnector;
  @Mock private DataCiteConnector dataCiteConnector;
  @Mock private DigitalObjectIdentifierDao doiDao;
  @Mock private InstrumentTemplateDao instrumentTemplateDao;
  @Mock private InstrumentEntityApiManager instrumentApiMgr;
  @Mock private InventoryIdentifierApiManager identifierMgr;
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private MessageSourceUtils messages;
  @InjectMocks private PidinstLookupManagerImpl manager;

  private final User user = new User("jane");

  @BeforeEach
  void setUp() {
    // B2INST is the enabled provider in every test here; DataCite is asked only if B2INST is off
    lenient().when(b2instConnector.isConfiguredAndEnabled()).thenReturn(true);
    // nothing is linked unless a test says so; search annotates every page through this one query
    lenient().when(doiDao.findActiveByIdentifiersAndType(any(), any())).thenReturn(List.of());
    lenient()
        .when(messages.getMessage(anyString(), any(Object[].class)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0) + " " + invocation.getArgument(1, Object[].class)[0]);
    // eq(user) is deliberate: a permission check made with any other user must not match here
    lenient()
        .when(
            invPermissions.canUserReadOrLimitedReadInventoryRecord(
                any(InventoryRecord.class), eq(user)))
        .thenReturn(true);
  }

  private static B2instDraftRecord publishedRecord() {
    B2instInstrumentMetadata md = new B2instInstrumentMetadata();
    md.setName("Test microscope");
    md.setIdentifier(new B2instIdentifier("Handle", HANDLE));
    md.setOwner(List.of(new B2instOwner("Lab A", null)));
    md.setManufacturer(List.of(new B2instManufacturer("Zeiss", null)));
    B2instDraftRecord record = new B2instDraftRecord();
    record.setId("abcde-12345");
    record.setIsPublished(true);
    record.setMetadata(md);
    B2instRecordLinks links = new B2instRecordLinks();
    links.setSelfHtml("https://b2inst-test.gwdg.de/records/abcde-12345");
    record.setLinks(links);
    return record;
  }

  private static B2instSearchResult searchResultOf(B2instDraftRecord record, int total) {
    B2instSearchResult result = new B2instSearchResult();
    result.getHits().getHits().add(record);
    result.getHits().setTotal(total);
    return result;
  }

  private static InstrumentTemplate lockedTemplate() {
    InstrumentTemplate template = new InstrumentTemplate();
    template.setId(7L);
    for (String name : List.of("Owner", "Manufacturer")) {
      InventoryEntityField field = new InventoryStringField(name);
      field.setMandatory(true);
      field.setInventoryRecord(template);
      field.setColumnIndex(template.getFields().size() + 1);
      template.getFields().add(field);
    }
    template.refreshActiveFieldsAndColumnIndex();
    return template;
  }

  /** RSDEV-1522: both registries match whole analysed tokens, so a bare substring found nothing. */
  @Test
  void freeTextSearchWrapsTheQueryInWildcardsSoASubstringOfATokenMatches() {
    when(b2instConnector.searchRecords("*microscope*", 50))
        .thenReturn(searchResultOf(publishedRecord(), 3));

    ApiPidinstSearchResult result = manager.search("  microscope ", user);

    assertEquals(1, result.getHits().size());
    verify(b2instConnector).searchRecords("*microscope*", 50);
  }

  @Test
  void dataCiteFreeTextSearchWrapsTheQueryInWildcards() {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois(anyString(), eq(50), any()))
        .thenReturn(dataCitePage(0));

    manager.search("microscope", user);

    verify(dataCiteConnector)
        .searchInstrumentDois("*microscope*", 50, InventorySettingType.PIDINST);
  }

  /**
   * Per-word would match more but exceeds DataCite's 30s read timeout at three words, which is why
   * the whole-sentence wrap was chosen (ADR 0009 decision 8). This test is what stops someone
   * "improving" it back.
   */
  @Test
  void aMultiWordQueryIsWrappedOnceAsAWholeSentenceNotPerWord() {
    when(b2instConnector.searchRecords("*electro micro stub*", 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));

    manager.search("electro micro stub", user);

    verify(b2instConnector).searchRecords("*electro micro stub*", 50);
  }

  /** Escaping runs first, so our wildcards stay live while the user's own text stays literal. */
  @Test
  void dataCiteWildcardsGoOutsideTheEscapeNotInsideIt() {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois(anyString(), eq(50), any()))
        .thenReturn(dataCitePage(0));

    manager.search("Zeiss &&", user);

    verify(dataCiteConnector)
        .searchInstrumentDois("*Zeiss \\&\\&*", 50, InventorySettingType.PIDINST);
  }

  @Test
  void freeTextSearchGoesToTheEnabledProviderAndFlagsAlreadyLinkedPids() {
    when(b2instConnector.searchRecords("*microscope*", 50))
        .thenReturn(searchResultOf(publishedRecord(), 3));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier(HANDLE, "Test microscope", "suffix1234567890");
    Instrument owner = new Instrument();
    owner.setId(99L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifiersAndType(
            List.of(HANDLE, "abcde-12345"), IdentifierType.PIDINST_B2INST))
        .thenReturn(List.of(existing));

    ApiPidinstSearchResult result = manager.search("  microscope ", user);

    assertEquals("PIDINST_B2INST", result.getProvider());
    assertEquals(3, result.getTotal());
    assertEquals(1, result.getHits().size());
    assertEquals(HANDLE, result.getHits().get(0).getPid());
    assertEquals("IN99", result.getHits().get(0).getLinkedInstrumentGlobalId());
    assertTrue(result.getHits().get(0).isAlreadyLinked());
    verify(dataCiteConnector, never()).searchInstrumentDois(anyString(), eq(50), any());
  }

  @Test
  void aHandleIsADirectLookupAndADoiYieldsNoHitOnAB2instDeployment() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(publishedRecord()));

    ApiPidinstSearchResult direct = manager.search("https://hdl.handle.net/" + HANDLE, user);
    assertEquals(1, direct.getHits().size());
    assertNull(direct.getHits().get(0).getLinkedInstrumentGlobalId());
    assertFalse(direct.getHits().get(0).isAlreadyLinked());
    verify(b2instConnector, never()).searchRecords(anyString(), eq(50));

    ApiPidinstSearchResult foreign = manager.search("10.15151/esrf-instr-gco8", user);
    assertTrue(foreign.getHits().isEmpty());
    assertEquals(0, foreign.getTotal());
    verify(b2instConnector, never()).searchRecords(anyString(), eq(50));
  }

  @Test
  void importCreatesFromTheLockedTemplateThenLinksTheIdentifier() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(publishedRecord()));
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.empty());
    when(instrumentTemplateDao.findLockedTemplateByName("Instrument (PIDINST 1.0)"))
        .thenReturn(Optional.of(lockedTemplate()));
    ApiInstrument created = new ApiInstrument();
    created.setId(5L);
    created.setGlobalId("IN5");
    when(instrumentApiMgr.createNewApiInstrument(any(ApiInstrument.class), eq(user)))
        .thenReturn(created);
    ApiInstrument linked = new ApiInstrument();
    linked.setId(5L);
    when(identifierMgr.linkExternalIdentifier(
            eq(new GlobalIdentifier("IN5")), any(ApiInventoryDOI.class), eq(user)))
        .thenReturn(linked);

    ApiInstrument result = manager.importInstrument(HANDLE, null, user);

    assertEquals(5L, result.getId());
    ArgumentCaptor<ApiInstrument> toCreate = ArgumentCaptor.forClass(ApiInstrument.class);
    verify(instrumentApiMgr).createNewApiInstrument(toCreate.capture(), eq(user));
    assertEquals(7L, toCreate.getValue().getTemplateId());
    assertEquals("Lab A", toCreate.getValue().getFields().get(0).getContent());
    assertEquals("Zeiss", toCreate.getValue().getFields().get(1).getContent());
    ArgumentCaptor<ApiInventoryDOI> link = ArgumentCaptor.forClass(ApiInventoryDOI.class);
    verify(identifierMgr)
        .linkExternalIdentifier(eq(new GlobalIdentifier("IN5")), link.capture(), eq(user));
    assertTrue(link.getValue().isLinked());
    assertEquals(HANDLE, link.getValue().getDoi());
    assertEquals("accepted", link.getValue().getState());
  }

  @Test
  void importRefusesAPidThatIsAlreadyLinkedNamingTheInstrument() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(publishedRecord()));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier(HANDLE, "Test microscope", "suffix1234567890");
    Instrument owner = new Instrument();
    owner.setId(99L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.of(existing));

    PidinstAlreadyLinkedException ex =
        assertThrows(
            PidinstAlreadyLinkedException.class,
            () -> manager.importInstrument(HANDLE, null, user));

    assertEquals("errors.inventory.identifier.pidinstAlreadyLinked", ex.getMessageKey());
    assertArrayEquals(new Object[] {"IN99"}, ex.getArgs());
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  /**
   * A B2INST PID this deployment minted itself stores the draft RID in {@code identifier}, not the
   * Handle, which is only ever minted on publish and only ever reaches publicUrl. Importing that
   * same Handle has to be refused like any other duplicate.
   */
  @Test
  void importRefusesAHandleWhosePidThisDeploymentMintedItself() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(publishedRecord()));
    // the row as the register path writes it: identifier is the RID, the Handle is not stored here
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier("abcde-12345", "Test microscope", "suffix1234567890");
    Instrument owner = new Instrument();
    owner.setId(77L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.empty());
    lenient()
        .when(doiDao.findActiveByIdentifierAndType("abcde-12345", IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.of(existing));

    PidinstAlreadyLinkedException ex =
        assertThrows(
            PidinstAlreadyLinkedException.class,
            () -> manager.importInstrument(HANDLE, null, user));

    assertArrayEquals(new Object[] {"IN77"}, ex.getArgs());
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  /** RSDEV-1505: the refusal still refuses, and still says why, without naming anything. */
  @Test
  void importRefusesAPidLinkedByAnInstrumentTheCallerCannotOpenWithoutNamingIt() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(publishedRecord()));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier(HANDLE, "Test microscope", "suffix1234567890");
    Instrument hidden = new Instrument();
    hidden.setId(99L);
    hidden.addIdentifier(existing);
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.of(existing));
    when(invPermissions.canUserReadOrLimitedReadInventoryRecord(hidden, user)).thenReturn(false);

    PidinstAlreadyLinkedException ex =
        assertThrows(
            PidinstAlreadyLinkedException.class,
            () -> manager.importInstrument(HANDLE, null, user));

    assertEquals("errors.inventory.identifier.pidinstAlreadyLinkedNoAccess", ex.getMessageKey());
    // stronger than reading the rendered text: the hidden Global ID is not in the exception at all
    assertEquals(0, ex.getArgs().length);
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  /** The same gap on the search path: the hit must come back already flagged as linked. */
  @Test
  void searchFlagsAHitWhosePidThisDeploymentMintedItself() {
    when(b2instConnector.searchRecords("*microscope*", 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier("abcde-12345", "Test microscope", "suffix1234567890");
    Instrument owner = new Instrument();
    owner.setId(77L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifiersAndType(any(), eq(IdentifierType.PIDINST_B2INST)))
        .thenReturn(List.of(existing));

    ApiPidinstSearchResult result = manager.search("microscope", user);

    assertEquals("IN77", result.getHits().get(0).getLinkedInstrumentGlobalId());
    assertTrue(result.getHits().get(0).isAlreadyLinked());
  }

  /** RSDEV-1505: defensive, but it decides a disclosure, so it is pinned rather than asserted. */
  @Test
  void aLinkedRowHoldingNoInstrumentIsHiddenRatherThanUnlinked() {
    when(b2instConnector.searchRecords("microscope", 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));
    DigitalObjectIdentifier orphan =
        new DigitalObjectIdentifier(HANDLE, "Test microscope", "suffix1234567890");
    when(doiDao.findActiveByIdentifiersAndType(any(), eq(IdentifierType.PIDINST_B2INST)))
        .thenReturn(List.of(orphan));

    ApiPidinstRecord hit = manager.search("microscope", user).getHits().get(0);

    assertTrue(hit.isAlreadyLinked(), "the PID is taken whether or not a record holds the row");
    assertNull(hit.getLinkedInstrumentGlobalId());
    // the point of the guard: the permission check is never handed a record that is not there
    verifyNoInteractions(invPermissions);
  }

  /** RSDEV-1505: the registry record is public, so the PID is; the RSpace instrument is not. */
  @Test
  void searchMarksAHitLinkedWithoutNamingAnInstrumentTheCallerCannotOpen() {
    when(b2instConnector.searchRecords("microscope", 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier(HANDLE, "Test microscope", "suffix1234567890");
    Instrument hidden = new Instrument();
    hidden.setId(99L);
    hidden.addIdentifier(existing);
    when(doiDao.findActiveByIdentifiersAndType(any(), eq(IdentifierType.PIDINST_B2INST)))
        .thenReturn(List.of(existing));
    when(invPermissions.canUserReadOrLimitedReadInventoryRecord(hidden, user)).thenReturn(false);

    ApiPidinstRecord hit = manager.search("microscope", user).getHits().get(0);

    assertTrue(hit.isAlreadyLinked(), "the PID is taken, and the caller must be told so");
    assertNull(hit.getLinkedInstrumentGlobalId(), "but not by which instrument");
  }

  @Test
  void importIs404WhenTheProviderHasNoPublishedRecordOrTheValueIsNotAPidOfThisProvider() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> manager.importInstrument(HANDLE, null, user));
    assertThrows(
        NotFoundException.class,
        () -> manager.importInstrument("10.15151/esrf-instr-gco8", null, user));
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  @Test
  void withNoPidinstProviderEnabledThereIsNoLookup() {
    when(b2instConnector.isConfiguredAndEnabled()).thenReturn(false);
    when(dataCiteConnector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST))
        .thenReturn(false);

    assertThrows(UnsupportedOperationException.class, () -> manager.search("microscope", user));
  }

  @Test
  void aRecordThatIsNotPublishedIsNeitherFoundNorImportable() {
    // only a public PID may be linked: B2INST accepted (published), DataCite findable
    B2instDraftRecord unpublished = publishedRecord();
    unpublished.setIsPublished(false);
    unpublished.setStatus("in_review");
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(unpublished));

    assertThrows(NotFoundException.class, () -> manager.importInstrument(HANDLE, null, user));
    assertTrue(manager.search(HANDLE, user).getHits().isEmpty());
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  @Test
  void b2instSearchDropsAnyHitTheIndexReportsAsNotPublished() {
    B2instDraftRecord unpublished = publishedRecord();
    unpublished.setIsPublished(false);
    unpublished.setStatus("draft");
    unpublished
        .getMetadata()
        .setIdentifier(new B2instIdentifier("Handle", "21.T11975/anaf6-fk223"));
    B2instSearchResult page = searchResultOf(publishedRecord(), 3);
    page.getHits().getHits().add(unpublished);
    when(b2instConnector.searchRecords("*microscope*", 50)).thenReturn(page);

    ApiPidinstSearchResult result = manager.search("microscope", user);

    assertEquals(1, result.getHits().size(), "the unpublished hit is dropped");
    assertEquals(HANDLE, result.getHits().get(0).getPid());
    assertEquals("accepted", result.getHits().get(0).getState());
    assertEquals(3, result.getTotal(), "the provider's own total, unaltered");
  }

  @Test
  void importPlacesTheNewInstrumentInTheRequestedTargetLocation() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(publishedRecord()));
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.empty());
    when(instrumentTemplateDao.findLockedTemplateByName("Instrument (PIDINST 1.0)"))
        .thenReturn(Optional.of(lockedTemplate()));
    ApiInstrument created = new ApiInstrument();
    created.setId(5L);
    created.setGlobalId("IN5");
    when(instrumentApiMgr.createNewApiInstrument(any(ApiInstrument.class), eq(user)))
        .thenReturn(created);
    when(identifierMgr.linkExternalIdentifier(
            eq(new GlobalIdentifier("IN5")), any(ApiInventoryDOI.class), eq(user)))
        .thenReturn(created);
    ApiContainerLocation location = new ApiContainerLocation();
    location.setCoordX(2);
    location.setCoordY(3);

    manager.importInstrument(HANDLE, new ApiTargetLocation(12L, location), user);

    ArgumentCaptor<ApiInstrument> toCreate = ArgumentCaptor.forClass(ApiInstrument.class);
    verify(instrumentApiMgr).createNewApiInstrument(toCreate.capture(), eq(user));
    assertEquals(
        12L,
        toCreate.getValue().getParentContainer().getId(),
        "the container is translated the way a plain instrument POST translates it");
    assertEquals(location, toCreate.getValue().getParentLocation());
  }

  // ---------------------------------------------------------------------------------------------
  // DataCite deployment: B2INST off, so enabledProvider() resolves to PIDINST_DATACITE
  // ---------------------------------------------------------------------------------------------

  private static final String DOI = "10.15151/esrf-instr-gco8";

  /** A findable instrument DOI, the shape DataCite returns for an ESRF beamline. */
  private static DataCiteDoi dataCiteInstrument(String doi, String state, String resourceType) {
    DataCiteDoi result = new DataCiteDoi();
    result.setId(doi);
    DataCiteDoiAttributes attributes = result.getAttributes();
    attributes.setDoi(doi);
    attributes.setState(state);
    attributes.setTitles(List.of(new DataCiteDoiAttributes.Title("ID21 Beamline")));
    DataCiteDoiAttributes.Types types = new DataCiteDoiAttributes.Types();
    types.setResourceTypeGeneral(resourceType);
    attributes.setTypes(types);
    return result;
  }

  private static DataCiteDoiSearchResult dataCitePage(int total, DataCiteDoi... dois) {
    DataCiteDoiSearchResult page = new DataCiteDoiSearchResult();
    page.getData().addAll(List.of(dois));
    page.getMeta().setTotal(total);
    return page;
  }

  private void onADataCiteDeployment() {
    when(b2instConnector.isConfiguredAndEnabled()).thenReturn(false);
    when(dataCiteConnector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST))
        .thenReturn(true);
  }

  @Test
  void searchRefusesAQueryShorterThanTheMinimum() {
    ApiRuntimeException thrown =
        assertThrows(ApiRuntimeException.class, () -> manager.search("qvt", user));

    assertEquals("errors.inventory.identifier.pidinstQueryTooShort", thrown.getErrorCode());
    verify(b2instConnector, never()).searchRecords(anyString(), eq(50));
    verify(dataCiteConnector, never()).searchInstrumentDois(anyString(), eq(50), any());
  }

  @Test
  void searchCountsTheTrimmedQueryTowardsTheMinimum() {
    assertThrows(ApiRuntimeException.class, () -> manager.search("  ab  ", user));
  }

  @Test
  void theRefusalCarriesTheMinimumSoTheMessageCanNameIt() {
    ApiRuntimeException thrown =
        assertThrows(ApiRuntimeException.class, () -> manager.search("qvt", user));

    // the catalogue entry is "Enter at least {0} characters...", so a dropped or wrong argument
    // ships a sentence with a hole in it, which asserting the code alone cannot see
    assertArrayEquals(new Object[] {MIN_QUERY_LENGTH}, thrown.getArgs());
  }

  @Test
  void searchAcceptsAQueryOfExactlyTheMinimum() {
    onADataCiteDeployment();
    String shortest = "qvtb";
    assertEquals(MIN_QUERY_LENGTH, shortest.length(), "the point of this test is the boundary");
    when(dataCiteConnector.searchInstrumentDois(
            "*" + shortest + "*", 50, InventorySettingType.PIDINST))
        .thenReturn(dataCitePage(1, dataCiteInstrument(DOI, "findable", "Instrument")));

    ApiPidinstSearchResult result = manager.search(shortest, user);

    // pins < against <=: an off-by-one here refuses what the dialog's own minimum lets through
    assertEquals(1, result.getHits().size());
  }

  @Test
  void dataCiteRetriesADoiWildcardWhenFreeTextFindsNothing() {
    onADataCiteDeployment();
    // the free-text call carries the hyphen escaped and the query wildcarded; the retry composes
    // its own clause from the raw query, so neither applies to the second of these two
    when(dataCiteConnector.searchInstrumentDois("*qvtb\\-aw74*", 50, InventorySettingType.PIDINST))
        .thenReturn(dataCitePage(0));
    when(dataCiteConnector.searchInstrumentDois(
            "doi:*qvtb-aw74*", 50, InventorySettingType.PIDINST))
        .thenReturn(dataCitePage(1, dataCiteInstrument(DOI, "findable", "Instrument")));

    ApiPidinstSearchResult result = manager.search("qvtb-aw74", user);

    assertEquals(1, result.getHits().size(), "the wildcard retry's hit is returned");
    assertEquals(DOI, result.getHits().get(0).getPid());
    assertEquals(1, result.getTotal(), "the total comes from the retry, not the empty first page");
  }

  @Test
  void dataCiteDoesNotRetryWhenFreeTextAlreadyFoundSomething() {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois("*Zeiss*", 50, InventorySettingType.PIDINST))
        .thenReturn(dataCitePage(3, dataCiteInstrument(DOI, "findable", "Instrument")));

    manager.search("Zeiss", user);

    verify(dataCiteConnector, never())
        .searchInstrumentDois("doi:*Zeiss*", 50, InventorySettingType.PIDINST);
  }

  @Test
  void dataCiteDoesNotRetryAQueryThatIsNotADoiFragment() {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois("*Carl Zeiss*", 50, InventorySettingType.PIDINST))
        .thenReturn(dataCitePage(0));

    ApiPidinstSearchResult result = manager.search("Carl Zeiss", user);

    assertTrue(result.getHits().isEmpty());
    verify(dataCiteConnector, never())
        .searchInstrumentDois("doi:*Carl Zeiss*", 50, InventorySettingType.PIDINST);
  }

  static Stream<Arguments> queriesCarryingQueryStringSyntax() {
    return Stream.of(
        arguments("Zeiss\"broken", "Zeiss\\\"broken"),
        arguments("Zeiss[broken", "Zeiss\\[broken"),
        arguments("(Zeiss", "\\(Zeiss"),
        arguments("Zeiss!", "Zeiss\\!"),
        arguments("Zeiss^2", "Zeiss\\^2"),
        arguments("Zeiss &&", "Zeiss \\&\\&"),
        arguments("Zeiss OR", "Zeiss \\OR"),
        arguments("NOT Zeiss", "\\NOT Zeiss"),
        arguments("Zeiss AND Bruker", "Zeiss \\AND Bruker"));
  }

  /**
   * DataCite's {@code query} is Elasticsearch query-string syntax, so user text reaches it escaped:
   * unbalanced syntax answers 400 rather than no hits, which the dialog can only show as an error.
   * Verified 2026-09-18 against api.datacite.org, where {@code foo"bar}, {@code foo[bar}, {@code
   * (foo}, {@code foo!}, {@code foo^}, {@code zeiss &&} and a dangling {@code abc OR} all answer
   * 400 while every escaped form answers 200.
   */
  @ParameterizedTest
  @MethodSource("queriesCarryingQueryStringSyntax")
  void dataCiteFreeTextSearchEscapesQueryStringSyntax(String typed, String sent) {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois(anyString(), eq(50), any()))
        .thenReturn(dataCitePage(0));

    manager.search(typed, user);

    verify(dataCiteConnector)
        .searchInstrumentDois("*" + sent + "*", 50, InventorySettingType.PIDINST);
  }

  /**
   * The range operators are left alone on purpose, and a reviewer has already asked for them once.
   * A backslash before one is ignored, so they cannot be escaped, and they need no handling: a
   * range exists only against a field, and the {@code :} that builds one is escaped above. Measured
   * 2026-09-18 against api.datacite.org: {@code publicationYear:>2020} answers 95,411,191 while
   * {@code publicationYear\:>2020}, which is what this sends, answers 15, the same as the plain
   * {@code publicationYear 2020}. Loose, {@code Zeiss>4} answers 6,539, exactly what {@code Zeiss
   * 4} and every other separator answer, so it is inert rather than parsed.
   */
  @ParameterizedTest
  @ValueSource(strings = {"Zeiss>4", "Zeiss<4", "Zeiss=4"})
  void dataCiteFreeTextSearchLeavesRangeOperatorsAlone(String query) {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois(anyString(), eq(50), any()))
        .thenReturn(dataCitePage(0));

    manager.search(query, user);

    verify(dataCiteConnector)
        .searchInstrumentDois("*" + query + "*", 50, InventorySettingType.PIDINST);
  }

  /**
   * The slash is reserved in query-string syntax, but DataCite answers 400 for {@code
   * 10.5281\/zenodo} and 200 for {@code 10.5281/zenodo} (verified 2026-09-18), so escaping it would
   * break the DOI fragments this search exists to match.
   */
  @Test
  void dataCiteFreeTextSearchLeavesTheSlashAlone() {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois(anyString(), eq(50), any()))
        .thenReturn(dataCitePage(0));

    manager.search("82316/qvtb", user);

    verify(dataCiteConnector)
        .searchInstrumentDois("*82316/qvtb*", 50, InventorySettingType.PIDINST);
  }

  /**
   * Escaping is the free-text call's business; the retry composes its own clause from the raw
   * query, so the allow-listed fragment must reach {@code doi:*...*} unescaped.
   */
  @Test
  void dataCiteWildcardRetryUsesTheUnescapedQuery() {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois(anyString(), eq(50), any()))
        .thenReturn(dataCitePage(0));

    manager.search("qvtb-aw74", user);

    verify(dataCiteConnector)
        .searchInstrumentDois("doi:*qvtb-aw74*", 50, InventorySettingType.PIDINST);
  }

  /**
   * A space is the weakest possible negative case: it would still be refused by a class that had
   * been widened to admit query-string metacharacters. These are the characters that actually break
   * the query - verified 2026-09-17 against api.datacite.org, where {@code doi:*"broken*} answers
   * 400 - so widening DOI_FRAGMENT to let one through fails here.
   */
  @ParameterizedTest
  @ValueSource(strings = {"qvtb\"aw74", "qvtb*aw74", "qvtb?aw74", "qvtb:aw74", "qvtb(aw74"})
  void dataCiteDoesNotRetryAQueryCarryingQueryStringSyntax(String query) {
    onADataCiteDeployment();
    // answers any query, so a retry that should not happen fails the verify below rather than
    // dying on an unstubbed call
    when(dataCiteConnector.searchInstrumentDois(anyString(), eq(50), any()))
        .thenReturn(dataCitePage(0));

    manager.search(query, user);

    verify(dataCiteConnector, never())
        .searchInstrumentDois("doi:*" + query + "*", 50, InventorySettingType.PIDINST);
  }

  /**
   * The slash is reserved in query-string syntax but DataCite accepts it inside a wildcard term, so
   * it is deliberately in DOI_FRAGMENT: it is what lets a pasted prefix/suffix pair match. Pinned
   * because the syntax alone argues for removing it, which would silently drop that case.
   */
  @Test
  void dataCiteRetriesAPastedPrefixAndSuffixPair() {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois(
            "*82316/qvtb\\-aw74*", 50, InventorySettingType.PIDINST))
        .thenReturn(dataCitePage(0));
    when(dataCiteConnector.searchInstrumentDois(
            "doi:*82316/qvtb-aw74*", 50, InventorySettingType.PIDINST))
        .thenReturn(dataCitePage(1, dataCiteInstrument(DOI, "findable", "Instrument")));

    ApiPidinstSearchResult result = manager.search("82316/qvtb-aw74", user);

    assertEquals(1, result.getHits().size());
  }

  @Test
  void dataCiteFreeTextSearchReturnsFindableInstrumentsAndFlagsLinkedOnes() {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois("*Zeiss*", 50, InventorySettingType.PIDINST))
        .thenReturn(dataCitePage(7, dataCiteInstrument(DOI, "findable", "Instrument")));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier(DOI, "ID21 Beamline", "suffix1234567890");
    Instrument owner = new Instrument();
    owner.setId(42L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifiersAndType(List.of(DOI), IdentifierType.PIDINST_DATACITE))
        .thenReturn(List.of(existing));

    ApiPidinstSearchResult result = manager.search(" Zeiss ", user);

    assertEquals("PIDINST_DATACITE", result.getProvider());
    assertEquals(7, result.getTotal(), "the provider's own total, unaltered");
    assertEquals(1, result.getHits().size());
    assertEquals(DOI, result.getHits().get(0).getPid());
    assertEquals("IN42", result.getHits().get(0).getLinkedInstrumentGlobalId());
    verify(b2instConnector, never()).searchRecords(anyString(), eq(50));
  }

  /**
   * ADR 0009 says RSpace re-checks the state on every hit, so the rule holds whatever the index
   * returns. Asking DataCite for findable instruments is not the same as being given them.
   */
  @Test
  void dataCiteSearchDropsAHitThatIsNotAFindableInstrument() {
    onADataCiteDeployment();
    when(dataCiteConnector.searchInstrumentDois("*Zeiss*", 50, InventorySettingType.PIDINST))
        .thenReturn(
            dataCitePage(
                3,
                dataCiteInstrument(DOI, "findable", "Instrument"),
                dataCiteInstrument("10.1234/draft-one", "draft", "Instrument"),
                dataCiteInstrument("10.1234/a-dataset", "findable", "Dataset")));

    ApiPidinstSearchResult result = manager.search("Zeiss", user);

    assertEquals(1, result.getHits().size(), "only the findable instrument survives");
    assertEquals(DOI, result.getHits().get(0).getPid());
  }

  @Test
  void aDoiIsADirectLookupOnADataCiteDeploymentAndAHandleYieldsNoHit() {
    onADataCiteDeployment();
    when(dataCiteConnector.findDoi(DOI, InventorySettingType.PIDINST))
        .thenReturn(Optional.of(dataCiteInstrument(DOI, "findable", "Instrument")));

    ApiPidinstSearchResult direct = manager.search("https://doi.org/" + DOI, user);
    assertEquals(1, direct.getHits().size());
    assertEquals(DOI, direct.getHits().get(0).getPid());
    verify(dataCiteConnector, never()).searchInstrumentDois(anyString(), eq(50), any());

    ApiPidinstSearchResult foreign = manager.search("21.T11975/abcde-12345", user);
    assertTrue(foreign.getHits().isEmpty(), "a Handle cannot be resolved on a DataCite deployment");
    assertEquals(0, foreign.getTotal());
  }

  @Test
  void aDirectDoiLookupRefusesADraftAndANonInstrument() {
    onADataCiteDeployment();
    when(dataCiteConnector.findDoi(DOI, InventorySettingType.PIDINST))
        .thenReturn(Optional.of(dataCiteInstrument(DOI, "draft", "Instrument")));
    assertTrue(manager.search(DOI, user).getHits().isEmpty(), "a draft DOI has no public page");

    when(dataCiteConnector.findDoi(DOI, InventorySettingType.PIDINST))
        .thenReturn(Optional.of(dataCiteInstrument(DOI, "findable", "Dataset")));
    assertTrue(manager.search(DOI, user).getHits().isEmpty(), "a dataset is not an instrument");
  }

  /**
   * B2INST resolves the suffix after the last slash and ignores the prefix, so a well-formed but
   * wrong prefix used to answer with the real record under the deployment's own prefix.
   */
  @Test
  void aB2instLookupRefusesARecordWhoseHandleIsNotTheOneAsked() {
    when(b2instConnector.getRecordByHandle("21.FAKE/abcde-12345"))
        .thenReturn(Optional.of(publishedRecord()));

    ApiPidinstSearchResult result = manager.search("21.FAKE/abcde-12345", user);

    assertTrue(result.getHits().isEmpty(), "the record returned carries a different Handle");
    assertEquals(0, result.getTotal());
  }

  /** A whole page of hits costs one link-status query, not one per hit. */
  @Test
  void everyHitOnAPageIsAnnotatedWithASingleQuery() {
    B2instDraftRecord second = publishedRecord();
    second.setId("fghij-67890");
    second.getMetadata().setName("Another microscope");
    second.getMetadata().setIdentifier(new B2instIdentifier("Handle", "21.T11975/fghij-67890"));
    B2instSearchResult page = searchResultOf(publishedRecord(), 2);
    page.getHits().getHits().add(second);
    when(b2instConnector.searchRecords("*microscope*", 50)).thenReturn(page);
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier("21.T11975/fghij-67890", "Another microscope", "suffix123456");
    Instrument owner = new Instrument();
    owner.setId(7L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifiersAndType(
            List.of("21.T11975/fghij-67890", "fghij-67890", HANDLE, "abcde-12345"),
            IdentifierType.PIDINST_B2INST))
        .thenReturn(List.of(existing));

    ApiPidinstSearchResult result = manager.search("microscope", user);

    assertEquals(2, result.getHits().size());
    // sorted by name, so "Another microscope" comes first
    assertEquals("IN7", result.getHits().get(0).getLinkedInstrumentGlobalId());
    assertNull(result.getHits().get(1).getLinkedInstrumentGlobalId());
    verify(doiDao, never()).findActiveByIdentifierAndType(anyString(), any());
  }
}
