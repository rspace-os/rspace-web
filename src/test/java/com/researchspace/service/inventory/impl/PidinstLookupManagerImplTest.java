package com.researchspace.service.inventory.impl;

import static com.researchspace.service.inventory.PidinstLookupManager.MIN_QUERY_LENGTH;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiPidinstRecord;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.api.v1.model.ApiPidinstSkippedRelatedIdentifier;
import com.researchspace.api.v1.model.ApiPidinstSkippedRelatedIdentifier.Reason;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.b2inst.model.metadata.B2instIdentifier;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.b2inst.model.metadata.B2instManufacturer;
import com.researchspace.b2inst.model.metadata.B2instOwner;
import com.researchspace.b2inst.model.metadata.B2instRelatedIdentifier;
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
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.PidinstAlreadyLinkedException;
import com.researchspace.service.inventory.PidinstLookupManager;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
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
  @Mock private InventoryLinkManager inventoryLinkManager;
  @Mock private IPropertyHolder properties;
  @InjectMocks private PidinstLookupManagerImpl manager;

  private static final String SERVER = "https://rspace.example.com";
  private static final String OTHER_SERVER =
      "https://rsdev-1253-map-clibration-and-measurement-f50c365a-11.researchspace.com";
  private static final String OTHER_HOST =
      "rsdev-1253-map-clibration-and-measurement-f50c365a-11.researchspace.com";

  private final User user = new User("jane");

  @BeforeEach
  void setUp() {
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
    lenient().when(properties.getServerUrl()).thenReturn(SERVER);
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

  private static B2instDraftRecord publishedRecord(
      String handle, String recordId, String updated, String created) {
    B2instDraftRecord record = publishedRecord();
    record.setId(recordId);
    record.getMetadata().setIdentifier(new B2instIdentifier("Handle", handle));
    record.setUpdated(updated);
    record.setCreated(created);
    return record;
  }

  private static B2instSearchResult searchResultOf(int total, B2instDraftRecord... records) {
    B2instSearchResult result = new B2instSearchResult();
    result.getHits().getHits().addAll(List.of(records));
    result.getHits().setTotal(total);
    return result;
  }

  /** Fifty distinct published records, so a page reads as full. */
  private static B2instSearchResult fullB2instPage(int total, String updated) {
    B2instDraftRecord[] records = new B2instDraftRecord[50];
    for (int i = 0; i < 50; i++) {
      records[i] = publishedRecord("21.T11975/full-" + i, "full-" + i, updated, updated);
    }
    return searchResultOf(total, records);
  }

  private static final List<String> BOTH = List.of("PIDINST_DATACITE", "PIDINST_B2INST");

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
    // the two link fields the import fills, so they sit at payload indices 2 and 3
    for (String name : List.of("Measurement technique", "Calibration")) {
      InventoryLinkField field = new InventoryLinkField();
      field.setName(name);
      field.setInventoryRecord(template);
      field.setColumnIndex(template.getFields().size() + 1);
      template.getFields().add(field);
    }
    template.refreshActiveFieldsAndColumnIndex();
    return template;
  }

  private static B2instRelatedIdentifier entry(String label, String address) {
    return new B2instRelatedIdentifier("URL", address, "IsDescribedBy", label);
  }

  private static B2instDraftRecord publishedRecordWith(B2instRelatedIdentifier... related) {
    B2instDraftRecord record = publishedRecord();
    record.getMetadata().setRelatedIdentifier(List.of(related));
    return record;
  }

  /**
   * Every collaborator an import needs past the fetch: the template, create, link. Nothing is
   * linked: Mockito answers Optional.empty() for the unstubbed already-linked lookups. Returns the
   * instrument the link step answers with, which is what importInstrument returns.
   */
  private ApiInstrument stubImportCollaborators() {
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
    return linked;
  }

  private ApiInstrument capturedCreatePayload() {
    ArgumentCaptor<ApiInstrument> toCreate = ArgumentCaptor.forClass(ApiInstrument.class);
    verify(instrumentApiMgr).createNewApiInstrument(toCreate.capture(), eq(user));
    return toCreate.getValue();
  }

  /**
   * RSDEV-1522 follow-up. Unescaped, the query does not reach the registry as one term:
   * Elasticsearch parses it before the wildcards apply and splits it on whitespace itself, leaving
   * {@code *Instr1} and {@code prova_COPY*}, which B2INST joins with OR - so a record named {@code
   * Instr1 prova 123} came back on its {@code instr1} token alone. Escaping the space keeps it one
   * term, and a real substring match.
   */
  @Test
  void aMultiWordQueryDoesNotMatchARecordHoldingOnlyOneOfTheWords() {
    B2instDraftRecord wanted = publishedRecord();
    wanted.getMetadata().setName("Instr1 prova_COPY");
    when(b2instConnector.searchPublicRecords("*Instr1\\ prova_COPY*", 0, 50))
        .thenReturn(searchResultOf(wanted, 1));

    ApiPidinstSearchResult result =
        manager.search("Instr1 prova_COPY", List.of("PIDINST_B2INST"), 0, user);

    assertEquals(1, result.getHits().size());
    verify(b2instConnector).searchPublicRecords("*Instr1\\ prova_COPY*", 0, 50);
  }

  /**
   * One search with both registries ticked gives each its own shape for the same words: B2INST one
   * wildcard term with the space escaped, so it matches a substring of the whole name; DataCite the
   * words as typed, so it answers what its own portal answers (ADR 0009 decision 8).
   */
  @Test
  void theTwoRegistriesGetDifferentlyShapedMultiWordQueries() {
    when(b2instConnector.searchPublicRecords(anyString(), eq(0), eq(50)))
        .thenReturn(searchResultOf(publishedRecord(), 1));
    when(dataCiteConnector.searchPublicInstrumentDois(anyString(), eq(0), eq(50)))
        .thenReturn(dataCitePage(0));

    manager.search("Instr1 prova_COPY", BOTH, 0, user);

    verify(b2instConnector).searchPublicRecords("*Instr1\\ prova_COPY*", 0, 50);
    verify(dataCiteConnector).searchPublicInstrumentDois("Instr1 prova_COPY", 0, 50);
  }

  /** RSDEV-1522: both registries match whole analysed tokens, so a bare substring found nothing. */
  @Test
  void freeTextSearchWrapsTheQueryInWildcardsSoASubstringOfATokenMatches() {
    when(b2instConnector.searchPublicRecords("*microscope*", 0, 50))
        .thenReturn(searchResultOf(publishedRecord(), 3));

    ApiPidinstSearchResult result =
        manager.search("  microscope ", List.of("PIDINST_B2INST"), 0, user);

    assertEquals(1, result.getHits().size());
    verify(b2instConnector).searchPublicRecords("*microscope*", 0, 50);
  }

  /**
   * One pair of wildcards around the whole query, its spaces escaped so it stays a single term and
   * matches a substring of the whole name. Leaving the spaces raw lets B2INST's OR return records
   * holding only one of the words (ADR 0009 decision 8). B2INST only: DataCite gets the query
   * unwildcarded.
   */
  @Test
  void aMultiWordB2instQueryIsOneTermWithItsSpacesEscaped() {
    when(b2instConnector.searchPublicRecords("*electro\\ micro\\ stub*", 0, 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));

    manager.search("electro micro stub", List.of("PIDINST_B2INST"), 0, user);

    verify(b2instConnector).searchPublicRecords("*electro\\ micro\\ stub*", 0, 50);
  }

  static Stream<Arguments> b2instQueriesCarryingQuerySyntax() {
    return Stream.of(
        arguments("\"Instr1 prova_COPY\"", "*Instr1\\ prova_COPY*"),
        arguments("\"Instr1", "*Instr1*"),
        arguments("Instr\"1", "*Instr1*"),
        arguments("Instr1 > 2", "*Instr1\\ 2*"),
        arguments("Instr1<=2", "*Instr1\\=2*"),
        arguments("(Instr1)", "*\\(Instr1\\)*"),
        arguments("Instr1~2", "*Instr1\\~2*"),
        arguments("Instr1^2", "*Instr1\\^2*"),
        arguments("Name:Instr1", "*Name\\:Instr1*"),
        arguments("T11975/97g70-tsv60", "*T11975\\/97g70\\-tsv60*"),
        arguments("Ins*1", "*Ins*1*"),
        // U+3000 is whitespace to Lucene's query parser but not to Java's \s, and left raw it split
        // the query back into clauses: *Instr1<U+3000>OR<U+3000>** matched all 810 records
        arguments("abcd\u3000OR\u3000*", "*abcd\\ OR\\ **"),
        arguments("Instr1\u3000prova_COPY", "*Instr1\\ prova_COPY*"));
  }

  /**
   * The wildcards turn unescaped syntax into operators around them: {@code *"a\ b"*} is {@code *}
   * OR a phrase OR {@code *}, and matched all 810 records on b2inst-test.gwdg.de where the quoted
   * phrase alone matched 1 (2026-09-25). So quotes are dropped, the whole query already being one
   * phrase ({@code *Instr1\ prova_COPY*} answers that same 1), the other syntax is escaped to a
   * literal, and {@code *}/{@code ?} stay live. This also ends the 400 on a pasted partial Handle.
   */
  @ParameterizedTest
  @MethodSource("b2instQueriesCarryingQuerySyntax")
  void b2instQuerySyntaxIsNeutralisedBeforeTheWildcardsGoOn(String typed, String sent) {
    when(b2instConnector.searchPublicRecords(anyString(), eq(0), eq(50)))
        .thenReturn(searchResultOf(publishedRecord(), 1));

    manager.search(typed, List.of("PIDINST_B2INST"), 0, user);

    verify(b2instConnector).searchPublicRecords(sent, 0, 50);
  }

  @Test
  void freeTextSearchGoesToTheTickedRegistryOnlyAndFlagsAlreadyLinkedPids() {
    when(b2instConnector.searchPublicRecords("*microscope*", 0, 50))
        .thenReturn(searchResultOf(publishedRecord(), 3));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier(HANDLE, "Test microscope", "suffix1234567890");
    Instrument owner = new Instrument();
    owner.setId(99L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifiersAndType(
            List.of(HANDLE, "abcde-12345"), IdentifierType.PIDINST_B2INST))
        .thenReturn(List.of(existing));

    ApiPidinstSearchResult result =
        manager.search("  microscope ", List.of("PIDINST_B2INST"), 0, user);

    assertEquals("PIDINST_B2INST", result.getProviders().get(0));
    assertEquals(3, result.getTotalHits());
    assertEquals(1, result.getHits().size());
    assertEquals(HANDLE, result.getHits().get(0).getPid());
    assertEquals("IN99", result.getHits().get(0).getLinkedInstrumentGlobalId());
    assertTrue(result.getHits().get(0).isAlreadyLinked());
    verify(dataCiteConnector, never()).searchPublicInstrumentDois(anyString(), anyInt(), anyInt());
  }

  @Test
  void aHandleIsADirectLookupAndADoiYieldsNoHitAtB2inst() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(Optional.of(publishedRecord()));

    ApiPidinstSearchResult direct =
        manager.search("https://hdl.handle.net/" + HANDLE, List.of("PIDINST_B2INST"), 0, user);
    assertEquals(1, direct.getHits().size());
    assertNull(direct.getHits().get(0).getLinkedInstrumentGlobalId());
    assertFalse(direct.getHits().get(0).isAlreadyLinked());
    verify(b2instConnector, never()).searchPublicRecords(anyString(), anyInt(), anyInt());

    ApiPidinstSearchResult foreign =
        manager.search("10.15151/esrf-instr-gco8", List.of("PIDINST_B2INST"), 0, user);
    assertTrue(foreign.getHits().isEmpty());
    assertEquals(0, foreign.getTotalHits());
    verify(b2instConnector, never()).searchPublicRecords(anyString(), anyInt(), anyInt());
  }

  @Test
  void importCreatesFromTheLockedTemplateThenLinksTheIdentifier() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(Optional.of(publishedRecord()));
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

    ApiInstrument result = manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user);

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
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(Optional.of(publishedRecord()));
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
            () -> manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user));

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
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(Optional.of(publishedRecord()));
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
            () -> manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user));

    assertArrayEquals(new Object[] {"IN77"}, ex.getArgs());
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  /** RSDEV-1505: the refusal still refuses, and still says why, without naming anything. */
  @Test
  void importRefusesAPidLinkedByAnInstrumentTheCallerCannotOpenWithoutNamingIt() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(Optional.of(publishedRecord()));
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
            () -> manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user));

    assertEquals("errors.inventory.identifier.pidinstAlreadyLinkedNoAccess", ex.getMessageKey());
    // stronger than reading the rendered text: the hidden Global ID is not in the exception at all
    assertEquals(0, ex.getArgs().length);
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  /** The same gap on the search path: the hit must come back already flagged as linked. */
  @Test
  void searchFlagsAHitWhosePidThisDeploymentMintedItself() {
    when(b2instConnector.searchPublicRecords("*microscope*", 0, 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier("abcde-12345", "Test microscope", "suffix1234567890");
    Instrument owner = new Instrument();
    owner.setId(77L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifiersAndType(any(), eq(IdentifierType.PIDINST_B2INST)))
        .thenReturn(List.of(existing));

    ApiPidinstSearchResult result =
        manager.search("microscope", List.of("PIDINST_B2INST"), 0, user);

    assertEquals("IN77", result.getHits().get(0).getLinkedInstrumentGlobalId());
    assertTrue(result.getHits().get(0).isAlreadyLinked());
  }

  /** RSDEV-1505: defensive, but it decides a disclosure, so it is pinned rather than asserted. */
  @Test
  void aLinkedRowHoldingNoInstrumentIsHiddenRatherThanUnlinked() {
    when(b2instConnector.searchPublicRecords("*microscope*", 0, 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));
    DigitalObjectIdentifier orphan =
        new DigitalObjectIdentifier(HANDLE, "Test microscope", "suffix1234567890");
    when(doiDao.findActiveByIdentifiersAndType(any(), eq(IdentifierType.PIDINST_B2INST)))
        .thenReturn(List.of(orphan));

    ApiPidinstRecord hit =
        manager.search("microscope", List.of("PIDINST_B2INST"), 0, user).getHits().get(0);

    assertTrue(hit.isAlreadyLinked(), "the PID is taken whether or not a record holds the row");
    assertNull(hit.getLinkedInstrumentGlobalId());
    // the point of the guard: the permission check is never handed a record that is not there
    verifyNoInteractions(invPermissions);
  }

  /** RSDEV-1505: the registry record is public, so the PID is; the RSpace instrument is not. */
  @Test
  void searchMarksAHitLinkedWithoutNamingAnInstrumentTheCallerCannotOpen() {
    when(b2instConnector.searchPublicRecords("*microscope*", 0, 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier(HANDLE, "Test microscope", "suffix1234567890");
    Instrument hidden = new Instrument();
    hidden.setId(99L);
    hidden.addIdentifier(existing);
    when(doiDao.findActiveByIdentifiersAndType(any(), eq(IdentifierType.PIDINST_B2INST)))
        .thenReturn(List.of(existing));
    when(invPermissions.canUserReadOrLimitedReadInventoryRecord(hidden, user)).thenReturn(false);

    ApiPidinstRecord hit =
        manager.search("microscope", List.of("PIDINST_B2INST"), 0, user).getHits().get(0);

    assertTrue(hit.isAlreadyLinked(), "the PID is taken, and the caller must be told so");
    assertNull(hit.getLinkedInstrumentGlobalId(), "but not by which instrument");
  }

  @Test
  void importIs404WhenTheRegistryHasNoPublishedRecordOrTheValueIsNotAPidOfIt() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user));
    assertThrows(
        NotFoundException.class,
        () -> manager.importInstrument("10.15151/esrf-instr-gco8", "PIDINST_B2INST", null, user));
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  @Test
  void aRecordThatIsNotPublishedIsNeitherFoundNorImportable() {
    // only a public PID may be linked: B2INST accepted (published), DataCite findable
    B2instDraftRecord unpublished = publishedRecord();
    unpublished.setIsPublished(false);
    unpublished.setStatus("in_review");
    when(b2instConnector.getPublicRecordByHandle(HANDLE)).thenReturn(Optional.of(unpublished));

    assertThrows(
        NotFoundException.class,
        () -> manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user));
    assertTrue(manager.search(HANDLE, List.of("PIDINST_B2INST"), 0, user).getHits().isEmpty());
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
    when(b2instConnector.searchPublicRecords("*microscope*", 0, 50)).thenReturn(page);

    ApiPidinstSearchResult result =
        manager.search("microscope", List.of("PIDINST_B2INST"), 0, user);

    assertEquals(1, result.getHits().size(), "the unpublished hit is dropped");
    assertEquals(HANDLE, result.getHits().get(0).getPid());
    assertEquals("accepted", result.getHits().get(0).getState());
    assertEquals(3, result.getTotalHits(), "the provider's own total, unaltered");
  }

  @Test
  void importPlacesTheNewInstrumentInTheRequestedTargetLocation() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(Optional.of(publishedRecord()));
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

    manager.importInstrument(HANDLE, "PIDINST_B2INST", new ApiTargetLocation(12L, location), user);

    ArgumentCaptor<ApiInstrument> toCreate = ArgumentCaptor.forClass(ApiInstrument.class);
    verify(instrumentApiMgr).createNewApiInstrument(toCreate.capture(), eq(user));
    assertEquals(
        12L,
        toCreate.getValue().getParentContainer().getId(),
        "the container is translated the way a plain instrument POST translates it");
    assertEquals(location, toCreate.getValue().getParentLocation());
  }

  // ---------------------------------------------------------------------------------------------
  // RSDEV-1528: the record's Measurement Technique and Calibration related identifiers
  // ---------------------------------------------------------------------------------------------

  @Test
  void entriesNamingThisServersItemsBecomeLinksWithTheFieldsRelationAndThePinKept() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(
            Optional.of(
                publishedRecordWith(
                    // registry labels are matched case-insensitively
                    entry("measurement technique", SERVER + "/globalId/SD12"),
                    entry("Calibration", SERVER + "/globalId/SA32768v3"))));
    ApiInstrument linked = stubImportCollaborators();
    when(inventoryLinkManager.canCreateLink(any(ApiInventoryLink.class), eq(user)))
        .thenReturn(true);

    ApiInstrument result = manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user);

    ApiInstrument payload = capturedCreatePayload();
    ApiInventoryLink technique = payload.getFields().get(2).getLink();
    assertEquals(
        "IsDocumentedBy",
        technique.getRelationType(),
        "the field's own relation, not the registry's constant IsDescribedBy");
    assertEquals("SD12", technique.getTargetGlobalId());
    ApiInventoryLink calibration = payload.getFields().get(3).getLink();
    assertEquals("IsCalibratedBy", calibration.getRelationType());
    assertEquals(
        "SA32768v3",
        calibration.getTargetGlobalId(),
        "the version suffix is passed through, so the link pins the version the address named");
    assertSame(linked, result);
    assertTrue(result.getSkippedRelatedIdentifiers().isEmpty(), "nothing to warn about");
  }

  @Test
  void entriesNamingAnotherServerAreSkippedWithItsHostAndNeverCheckedForReadability() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(
            Optional.of(
                publishedRecordWith(
                    entry("Measurement Technique", OTHER_SERVER + "/globalId/IC65536"),
                    entry("Calibration", OTHER_SERVER + "/globalId/SA32768"))));
    stubImportCollaborators();

    ApiInstrument result = manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user);

    ApiInstrument payload = capturedCreatePayload();
    assertNull(payload.getFields().get(2).getLink());
    assertNull(payload.getFields().get(3).getLink());
    assertEquals(
        List.of(
            new ApiPidinstSkippedRelatedIdentifier(
                "Measurement technique",
                Reason.OTHER_SERVER,
                OTHER_SERVER + "/globalId/IC65536",
                OTHER_HOST),
            new ApiPidinstSkippedRelatedIdentifier(
                "Calibration",
                Reason.OTHER_SERVER,
                OTHER_SERVER + "/globalId/SA32768",
                OTHER_HOST)),
        result.getSkippedRelatedIdentifiers());
    verify(inventoryLinkManager, never()).canCreateLink(any(), any());
  }

  /**
   * ADR 0002: unreadable, missing and unsupported all read the same, so the import never confirms
   * that an item exists. The write path's own check decides, with the link as it would be created.
   */
  @Test
  void anEntryOfThisServerTheUserCannotLinkIsSkippedAsNotAvailableWithoutSayingWhy() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(
            Optional.of(publishedRecordWith(entry("Calibration", SERVER + "/globalId/SA999"))));
    stubImportCollaborators();
    when(inventoryLinkManager.canCreateLink(any(ApiInventoryLink.class), eq(user)))
        .thenReturn(false);

    ApiInstrument result = manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user);

    assertNull(capturedCreatePayload().getFields().get(3).getLink());
    assertEquals(
        List.of(
            new ApiPidinstSkippedRelatedIdentifier(
                "Calibration", Reason.NOT_AVAILABLE, SERVER + "/globalId/SA999", null)),
        result.getSkippedRelatedIdentifiers());
    ArgumentCaptor<ApiInventoryLink> asked = ArgumentCaptor.forClass(ApiInventoryLink.class);
    verify(inventoryLinkManager).canCreateLink(asked.capture(), eq(user));
    assertEquals("SA999", asked.getValue().getTargetGlobalId());
    assertEquals("IsCalibratedBy", asked.getValue().getRelationType());
  }

  @Test
  void anEntryThatIsNotAnAddressInThisRSpaceIsSkippedWithoutAHost() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(Optional.of(publishedRecordWith(entry("Calibration", "10.1000/manual"))));
    stubImportCollaborators();

    ApiInstrument result = manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user);

    assertEquals(
        List.of(
            new ApiPidinstSkippedRelatedIdentifier(
                "Calibration", Reason.OTHER_SERVER, "10.1000/manual", null)),
        result.getSkippedRelatedIdentifiers());
  }

  /** Our own host is never named as another server; host names ignore case. */
  @Test
  void anEntryOnThisServersHostThatIsNotAnItemPageIsSkippedWithoutAHost() {
    String ownPage = "https://RSpace.Example.com/public/inventory/abc";
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(Optional.of(publishedRecordWith(entry("Calibration", ownPage))));
    stubImportCollaborators();

    ApiInstrument result = manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user);

    assertEquals(
        List.of(
            new ApiPidinstSkippedRelatedIdentifier(
                "Calibration", Reason.OTHER_SERVER, ownPage, null)),
        result.getSkippedRelatedIdentifiers());
  }

  /** An installation fault reaches the user as a translated sentence, not developer detail. */
  @Test
  void aMissingLockedTemplateRefusesTheImportWithATranslatedMessage() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(Optional.of(publishedRecord()));
    when(instrumentTemplateDao.findLockedTemplateByName("Instrument (PIDINST 1.0)"))
        .thenReturn(Optional.empty());

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user));

    assertEquals(
        "errors.inventory.identifier.pidinstTemplateMissing Instrument (PIDINST 1.0)",
        ex.getMessage());
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  /** URI.create refuses an address with a space, and the entry is still reported, not a failure. */
  @Test
  void anEntryThatIsNotEvenAUriIsSkippedWithoutAHost() {
    String notAUri = "https://example.org/my manual.pdf";
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(Optional.of(publishedRecordWith(entry("Calibration", notAUri))));
    stubImportCollaborators();

    ApiInstrument result = manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user);

    assertEquals(
        List.of(
            new ApiPidinstSkippedRelatedIdentifier(
                "Calibration", Reason.OTHER_SERVER, notAUri, null)),
        result.getSkippedRelatedIdentifiers());
  }

  @Test
  void aRecordWithoutTheTwoEntriesImportsAsTodayAndOtherLabelsAreIgnoredQuietly() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(
            Optional.of(
                publishedRecordWith(
                    entry("Manual", OTHER_SERVER + "/globalId/GL1"),
                    entry(null, SERVER + "/globalId/SA1"))));
    stubImportCollaborators();

    ApiInstrument result = manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user);

    assertTrue(result.getSkippedRelatedIdentifiers().isEmpty());
    ApiInstrument payload = capturedCreatePayload();
    assertNull(payload.getFields().get(2).getLink());
    assertNull(payload.getFields().get(3).getLink());
    verify(inventoryLinkManager, never()).canCreateLink(any(), any());
  }

  @Test
  void withSeveralEntriesForOneFieldTheFirstThatLinksWinsAndOtherwiseEachIsReported() {
    when(b2instConnector.getPublicRecordByHandle(HANDLE))
        .thenReturn(
            Optional.of(
                publishedRecordWith(
                    entry("Calibration", OTHER_SERVER + "/globalId/SA1"),
                    entry("Calibration", SERVER + "/globalId/SA2"),
                    entry("Calibration", SERVER + "/globalId/SA3"),
                    entry("Measurement Technique", OTHER_SERVER + "/globalId/SD1"),
                    entry("Measurement Technique", SERVER + "/globalId/SD2"))));
    stubImportCollaborators();
    when(inventoryLinkManager.canCreateLink(any(ApiInventoryLink.class), eq(user)))
        .thenAnswer(
            invocation ->
                "SA2"
                    .equals(invocation.getArgument(0, ApiInventoryLink.class).getTargetGlobalId()));

    ApiInstrument result = manager.importInstrument(HANDLE, "PIDINST_B2INST", null, user);

    ApiInstrument payload = capturedCreatePayload();
    assertEquals(
        "SA2",
        payload.getFields().get(3).getLink().getTargetGlobalId(),
        "the first Calibration entry that links wins");
    assertNull(payload.getFields().get(2).getLink());
    assertEquals(
        List.of(
            new ApiPidinstSkippedRelatedIdentifier(
                "Measurement technique",
                Reason.OTHER_SERVER,
                OTHER_SERVER + "/globalId/SD1",
                OTHER_HOST),
            new ApiPidinstSkippedRelatedIdentifier(
                "Measurement technique", Reason.NOT_AVAILABLE, SERVER + "/globalId/SD2", null)),
        result.getSkippedRelatedIdentifiers(),
        "no Measurement Technique entry linked, so both are reported; the losing Calibration"
            + " entries are not");
    verify(inventoryLinkManager, never())
        .canCreateLink(
            argThat((ApiInventoryLink link) -> "SA3".equals(link.getTargetGlobalId())), any());
  }

  /** The DataCite half of the same rule: relationTypeInformation is the label (ADR 0007). */
  @Test
  void aDataCiteRecordsEntriesAreResolvedTheSameWay() {
    DataCiteDoi doi = dataCiteInstrument(DOI, "findable", "Instrument");
    // the locked template's mandatory Owner and Manufacturer: publisher fallback and a creator
    doi.getAttributes().setPublisher("ESRF");
    DataCiteDoiAttributes.Creator creator = new DataCiteDoiAttributes.Creator();
    creator.setName("ESRF");
    doi.getAttributes().setCreators(List.of(creator));
    doi.getAttributes()
        .setRelatedIdentifiers(
            List.of(
                new DataCiteDoiAttributes.RelatedIdentifier(
                    "IsDescribedBy", SERVER + "/globalId/SA7", "URL", "Calibration")));
    when(dataCiteConnector.findPublicDoi(DOI)).thenReturn(Optional.of(doi));
    stubImportCollaborators();
    when(inventoryLinkManager.canCreateLink(any(ApiInventoryLink.class), eq(user)))
        .thenReturn(true);

    ApiInstrument result = manager.importInstrument(DOI, "PIDINST_DATACITE", null, user);

    ApiInventoryLink calibration = capturedCreatePayload().getFields().get(3).getLink();
    assertEquals("SA7", calibration.getTargetGlobalId());
    assertEquals("IsCalibratedBy", calibration.getRelationType());
    assertTrue(result.getSkippedRelatedIdentifiers().isEmpty());
  }

  // ---------------------------------------------------------------------------------------------
  // DataCite
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

  private static DataCiteDoi dataCiteInstrumentUpdatedAt(
      String doi, String updated, String created) {
    DataCiteDoi result = dataCiteInstrument(doi, "findable", "Instrument");
    result.getAttributes().setUpdated(Date.from(Instant.parse(updated)));
    result.getAttributes().setCreated(Date.from(Instant.parse(created)));
    return result;
  }

  private static DataCiteDoiSearchResult fullDataCitePage(int total) {
    DataCiteDoi[] dois = new DataCiteDoi[50];
    for (int i = 0; i < 50; i++) {
      dois[i] =
          dataCiteInstrumentUpdatedAt(
              "10.1/full-" + i, "2026-03-01T00:00:00Z", "2026-03-01T00:00:00Z");
    }
    return dataCitePage(total, dois);
  }

  @Test
  void searchRefusesAQueryShorterThanTheMinimum() {
    ApiRuntimeException thrown =
        assertThrows(
            ApiRuntimeException.class,
            () -> manager.search("qvt", List.of("PIDINST_B2INST"), 0, user));

    assertEquals("errors.inventory.identifier.pidinstQueryTooShort", thrown.getErrorCode());
    verify(b2instConnector, never()).searchPublicRecords(anyString(), anyInt(), anyInt());
    verify(dataCiteConnector, never()).searchPublicInstrumentDois(anyString(), anyInt(), anyInt());
  }

  /**
   * The minimum is checked again on what B2INST would actually search: removing syntax could shrink
   * an accepted query to one wildcarded letter, and {@code <<<a} sent as {@code *a*} matched all
   * 810 records on b2inst-test.gwdg.de (2026-09-25). Wildcards the user typed do not count either.
   */
  @ParameterizedTest
  @ValueSource(strings = {"<<<a", "\"\"\"\"", "a\"<>", "***a", "?a?b"})
  void aB2instQueryBelowTheMinimumOnceItsSyntaxIsGoneFindsNothing(String typed) {
    ApiPidinstSearchResult result = manager.search(typed, List.of("PIDINST_B2INST"), 0, user);

    assertTrue(result.getHits().isEmpty());
    assertEquals(0, result.getTotalHits());
    verify(b2instConnector, never()).searchPublicRecords(anyString(), anyInt(), anyInt());
  }

  @ParameterizedTest
  @ValueSource(strings = {"  ab  ", "\u3000\u3000ab\u3000"})
  void searchCountsTheTrimmedQueryTowardsTheMinimum(String padded) {
    assertThrows(
        ApiRuntimeException.class,
        () -> manager.search(padded, List.of("PIDINST_B2INST"), 0, user));
  }

  @Test
  void theRefusalCarriesTheMinimumSoTheMessageCanNameIt() {
    ApiRuntimeException thrown =
        assertThrows(
            ApiRuntimeException.class,
            () -> manager.search("qvt", List.of("PIDINST_B2INST"), 0, user));

    // the catalogue entry is "Enter at least {0} characters...", so a dropped or wrong argument
    // ships a sentence with a hole in it, which asserting the code alone cannot see
    assertArrayEquals(new Object[] {MIN_QUERY_LENGTH}, thrown.getArgs());
  }

  @Test
  void searchAcceptsAQueryOfExactlyTheMinimum() {
    String shortest = "qvtb";
    assertEquals(MIN_QUERY_LENGTH, shortest.length(), "the point of this test is the boundary");
    when(dataCiteConnector.searchPublicInstrumentDois(shortest, 0, 50))
        .thenReturn(dataCitePage(1, dataCiteInstrument(DOI, "findable", "Instrument")));

    ApiPidinstSearchResult result = manager.search(shortest, List.of("PIDINST_DATACITE"), 0, user);

    // pins < against <=: an off-by-one here refuses what the dialog's own minimum lets through
    assertEquals(1, result.getHits().size());
  }

  @Test
  void dataCiteRetriesADoiWildcardWhenFreeTextFindsNothing() {
    when(dataCiteConnector.searchPublicInstrumentDois("qvtb", 0, 50)).thenReturn(dataCitePage(0));
    when(dataCiteConnector.searchPublicInstrumentDois("doi:*qvtb*", 0, 50))
        .thenReturn(dataCitePage(1, dataCiteInstrument(DOI, "findable", "Instrument")));

    ApiPidinstSearchResult result = manager.search("qvtb", List.of("PIDINST_DATACITE"), 0, user);

    assertEquals(1, result.getHits().size(), "the wildcard retry's hit is returned");
    assertEquals(DOI, result.getHits().get(0).getPid());
    assertEquals(
        1, result.getTotalHits(), "the total comes from the retry, not the empty first page");
  }

  @Test
  void dataCiteDoesNotRetryWhenFreeTextAlreadyFoundSomething() {
    when(dataCiteConnector.searchPublicInstrumentDois("Zeiss", 0, 50))
        .thenReturn(dataCitePage(3, dataCiteInstrument(DOI, "findable", "Instrument")));

    manager.search("Zeiss", List.of("PIDINST_DATACITE"), 0, user);

    verify(dataCiteConnector, never()).searchPublicInstrumentDois("doi:*Zeiss*", 0, 50);
  }

  @Test
  void dataCiteDoesNotRetryAQueryThatIsNotADoiFragment() {
    when(dataCiteConnector.searchPublicInstrumentDois("Carl Zeiss", 0, 50))
        .thenReturn(dataCitePage(0));

    ApiPidinstSearchResult result =
        manager.search("Carl Zeiss", List.of("PIDINST_DATACITE"), 0, user);

    assertTrue(result.getHits().isEmpty());
    verify(dataCiteConnector, never()).searchPublicInstrumentDois("doi:*Carl Zeiss*", 0, 50);
  }

  static Stream<Arguments> queriesCarryingQueryStringSyntax() {
    return Stream.of(
        arguments("Zeiss\"broken", "Zeiss\\\"broken"),
        arguments("Zeiss[broken", "Zeiss\\[broken"),
        arguments("(Zeiss", "\\(Zeiss"),
        arguments("Zeiss!", "Zeiss\\!"),
        arguments("Zeiss^2", "Zeiss\\^2"),
        arguments("((((", "\\(\\(\\(\\("),
        arguments("Zeiss &&", "Zeiss \\&\\&"),
        arguments("&&&&", "\\&\\&\\&\\&"),
        arguments("X-ray microscope", "X\\-ray microscope"),
        arguments("-Zeiss", "\\-Zeiss"),
        arguments("test/instrument", "test/instrument"),
        arguments("TEM:STEM", "TEM\\:STEM"),
        arguments("\u4e2d\u56fd\u663e\u5fae\u955c", "\u4e2d\u56fd\u663e\u5fae\u955c"),
        arguments("station 14.1", "station 14.1"),
        arguments("Zeiss>4", "Zeiss>4"),
        arguments("82316/qvtb", "82316/qvtb"),
        arguments("Zeiss. Bruker", "Zeiss. Bruker"),
        arguments("Zeiss OR", "Zeiss \\OR"),
        arguments("NOT Zeiss", "\\NOT Zeiss"),
        arguments("Zeiss AND Bruker", "Zeiss \\AND Bruker"));
  }

  /**
   * DataCite's {@code query} is Elasticsearch query-string syntax, so user syntax must not reach
   * it: unbalanced syntax answers 400 rather than no hits, which the dialog can only show as an
   * error. The escape covers the reserved characters and the bare operators, and is transparent
   * otherwise, so what the user typed is what DataCite matches on. Verified 2026-09-18 against
   * api.datacite.org, where {@code foo"bar}, {@code foo[bar}, {@code (foo}, {@code foo!}, {@code
   * foo^}, {@code zeiss &&} and a dangling {@code abc OR} all answer 400 while every escaped form
   * answers 200.
   */
  @ParameterizedTest
  @MethodSource("queriesCarryingQueryStringSyntax")
  void dataCiteFreeTextSearchEscapesQueryStringSyntaxOnly(String typed, String sent) {
    when(dataCiteConnector.searchPublicInstrumentDois(anyString(), eq(0), eq(50)))
        .thenReturn(dataCitePage(0));

    manager.search(typed, List.of("PIDINST_DATACITE"), 0, user);

    verify(dataCiteConnector).searchPublicInstrumentDois(sent, 0, 50);
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
    // answers any query, so a retry that should not happen fails the verify below rather than
    // dying on an unstubbed call
    when(dataCiteConnector.searchPublicInstrumentDois(anyString(), eq(0), eq(50)))
        .thenReturn(dataCitePage(0));

    manager.search(query, List.of("PIDINST_DATACITE"), 0, user);

    verify(dataCiteConnector, never()).searchPublicInstrumentDois("doi:*" + query + "*", 0, 50);
  }

  /**
   * The slash is reserved in query-string syntax but DataCite accepts it inside a wildcard term, so
   * it is deliberately in DOI_FRAGMENT: it is what lets a pasted prefix/suffix pair match. Pinned
   * because the syntax alone argues for removing it, which would silently drop that case.
   */
  @Test
  void dataCiteFindsAPastedPrefixAndSuffixPair() {
    when(dataCiteConnector.searchPublicInstrumentDois("82316/qvtb\\-aw74", 0, 50))
        .thenReturn(dataCitePage(0));
    when(dataCiteConnector.searchPublicInstrumentDois("doi:*82316/qvtb-aw74*", 0, 50))
        .thenReturn(dataCitePage(1, dataCiteInstrument(DOI, "findable", "Instrument")));

    ApiPidinstSearchResult result =
        manager.search("82316/qvtb-aw74", List.of("PIDINST_DATACITE"), 0, user);

    assertEquals(1, result.getHits().size());
  }

  @Test
  void dataCiteFreeTextSearchReturnsFindableInstrumentsAndFlagsLinkedOnes() {
    when(dataCiteConnector.searchPublicInstrumentDois("Zeiss", 0, 50))
        .thenReturn(dataCitePage(7, dataCiteInstrument(DOI, "findable", "Instrument")));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier(DOI, "ID21 Beamline", "suffix1234567890");
    Instrument owner = new Instrument();
    owner.setId(42L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifiersAndType(List.of(DOI), IdentifierType.PIDINST_DATACITE))
        .thenReturn(List.of(existing));

    ApiPidinstSearchResult result = manager.search(" Zeiss ", List.of("PIDINST_DATACITE"), 0, user);

    assertEquals("PIDINST_DATACITE", result.getProviders().get(0));
    assertEquals(7, result.getTotalHits(), "the provider's own total, unaltered");
    assertEquals(1, result.getHits().size());
    assertEquals(DOI, result.getHits().get(0).getPid());
    assertEquals("IN42", result.getHits().get(0).getLinkedInstrumentGlobalId());
    verify(b2instConnector, never()).searchPublicRecords(anyString(), anyInt(), anyInt());
  }

  /**
   * ADR 0009 says RSpace re-checks the state on every hit, so the rule holds whatever the index
   * returns. Asking DataCite for findable instruments is not the same as being given them.
   */
  @Test
  void dataCiteSearchDropsAHitThatIsNotAFindableInstrument() {
    when(dataCiteConnector.searchPublicInstrumentDois("Zeiss", 0, 50))
        .thenReturn(
            dataCitePage(
                3,
                dataCiteInstrument(DOI, "findable", "Instrument"),
                dataCiteInstrument("10.1234/draft-one", "draft", "Instrument"),
                dataCiteInstrument("10.1234/a-dataset", "findable", "Dataset")));

    ApiPidinstSearchResult result = manager.search("Zeiss", List.of("PIDINST_DATACITE"), 0, user);

    assertEquals(1, result.getHits().size(), "only the findable instrument survives");
    assertEquals(DOI, result.getHits().get(0).getPid());
  }

  @Test
  void aDoiIsADirectLookupAtDataCiteAndAHandleYieldsNoHit() {
    when(dataCiteConnector.findPublicDoi(DOI))
        .thenReturn(Optional.of(dataCiteInstrument(DOI, "findable", "Instrument")));

    ApiPidinstSearchResult direct =
        manager.search("https://doi.org/" + DOI, List.of("PIDINST_DATACITE"), 0, user);
    assertEquals(1, direct.getHits().size());
    assertEquals(DOI, direct.getHits().get(0).getPid());
    verify(dataCiteConnector, never()).searchPublicInstrumentDois(anyString(), anyInt(), anyInt());

    ApiPidinstSearchResult foreign =
        manager.search("21.T11975/abcde-12345", List.of("PIDINST_DATACITE"), 0, user);
    assertTrue(foreign.getHits().isEmpty(), "a Handle cannot be resolved at DataCite");
    assertEquals(0, foreign.getTotalHits());
  }

  @Test
  void aDirectDoiLookupRefusesADraftAndANonInstrument() {
    when(dataCiteConnector.findPublicDoi(DOI))
        .thenReturn(Optional.of(dataCiteInstrument(DOI, "draft", "Instrument")));
    assertTrue(
        manager.search(DOI, List.of("PIDINST_DATACITE"), 0, user).getHits().isEmpty(),
        "a draft DOI has no public page");

    when(dataCiteConnector.findPublicDoi(DOI))
        .thenReturn(Optional.of(dataCiteInstrument(DOI, "findable", "Dataset")));
    assertTrue(
        manager.search(DOI, List.of("PIDINST_DATACITE"), 0, user).getHits().isEmpty(),
        "a dataset is not an instrument");
  }

  /**
   * B2INST resolves the suffix after the last slash and ignores the prefix, so a well-formed but
   * wrong prefix used to answer with the real record under the deployment's own prefix.
   */
  @Test
  void aB2instLookupRefusesARecordWhoseHandleIsNotTheOneAsked() {
    when(b2instConnector.getPublicRecordByHandle("21.FAKE/abcde-12345"))
        .thenReturn(Optional.of(publishedRecord()));

    ApiPidinstSearchResult result =
        manager.search("21.FAKE/abcde-12345", List.of("PIDINST_B2INST"), 0, user);

    assertTrue(result.getHits().isEmpty(), "the record returned carries a different Handle");
    assertEquals(0, result.getTotalHits());
  }

  /** A whole page of hits costs one link-status query, not one per hit. */
  @Test
  void everyHitOnAPageIsAnnotatedWithASingleQuery() {
    B2instDraftRecord second = publishedRecord();
    second.setId("fghij-67890");
    second.getMetadata().setName("Another microscope");
    second.getMetadata().setIdentifier(new B2instIdentifier("Handle", "21.T11975/fghij-67890"));
    second.setUpdated("2026-09-01T00:00:00+00:00");
    B2instSearchResult page = searchResultOf(publishedRecord(), 2);
    page.getHits().getHits().add(second);
    when(b2instConnector.searchPublicRecords("*microscope*", 0, 50)).thenReturn(page);
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier("21.T11975/fghij-67890", "Another microscope", "suffix123456");
    Instrument owner = new Instrument();
    owner.setId(7L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifiersAndType(
            List.of("21.T11975/fghij-67890", "fghij-67890", HANDLE, "abcde-12345"),
            IdentifierType.PIDINST_B2INST))
        .thenReturn(List.of(existing));

    ApiPidinstSearchResult result =
        manager.search("microscope", List.of("PIDINST_B2INST"), 0, user);

    assertEquals(2, result.getHits().size());
    // newest update first, so the dated "Another microscope" comes first
    assertEquals("IN7", result.getHits().get(0).getLinkedInstrumentGlobalId());
    assertNull(result.getHits().get(1).getLinkedInstrumentGlobalId());
    verify(doiDao, never()).findActiveByIdentifierAndType(anyString(), any());
  }

  // ---------------------------------------------------------------------------------------------
  // RSDEV-1518: the public registries, one merged list, paged (ADR 0011)
  // ---------------------------------------------------------------------------------------------

  @Test
  void hitsFromBothRegistriesFormOneListNewestUpdateFirstWithTiesInTheOrderAsked() {
    when(b2instConnector.searchPublicRecords("*Zeiss*", 0, 50))
        .thenReturn(
            searchResultOf(
                2,
                // created after its DataCite twin, yet listed after it: DataCite was asked first
                publishedRecord(
                    "21.T11975/b-tie",
                    "b-tie",
                    "2026-01-01T00:00:00+00:00",
                    "2025-12-01T00:00:00+00:00"),
                publishedRecord(
                    "21.T11975/b-new",
                    "b-new",
                    "2026-09-01T12:00:00.000001+00:00",
                    "2026-01-01T00:00:00+00:00")));
    when(dataCiteConnector.searchPublicInstrumentDois("Zeiss", 0, 50))
        .thenReturn(
            dataCitePage(
                2,
                dataCiteInstrumentUpdatedAt(
                    "10.1/d-tie", "2026-01-01T00:00:00Z", "2025-06-01T00:00:00Z"),
                dataCiteInstrumentUpdatedAt(
                    "10.1/d-newest", "2026-09-30T00:00:00Z", "2024-01-01T00:00:00Z")));

    ApiPidinstSearchResult result = manager.search("Zeiss", BOTH, 0, user);

    assertIterableEquals(
        List.of("10.1/d-newest", "21.T11975/b-new", "10.1/d-tie", "21.T11975/b-tie"),
        result.getHits().stream().map(ApiPidinstRecord::getPid).toList());
    assertEquals(List.of("PIDINST_DATACITE", "PIDINST_B2INST"), result.getProviders());
    assertEquals(4, result.getTotalHits());
    assertEquals(2, result.getTotalsByProvider().get("PIDINST_B2INST"));
    assertEquals(2, result.getTotalsByProvider().get("PIDINST_DATACITE"));
    assertEquals(0, result.getPageNumber());
  }

  @Test
  void aHitWithNoOrAnUnreadableUpdateTimeSortsAfterADatedOne() {
    when(b2instConnector.searchPublicRecords("*Zeiss*", 0, 50))
        .thenReturn(
            searchResultOf(
                3,
                publishedRecord("21.T11975/a-undated", "a", null, null),
                publishedRecord("21.T11975/b-garbled", "b", "not-a-date", null),
                publishedRecord("21.T11975/c-dated", "c", "2020-01-01T00:00:00+00:00", null)));

    ApiPidinstSearchResult result = manager.search("Zeiss", List.of("PIDINST_B2INST"), 0, user);

    assertIterableEquals(
        List.of("21.T11975/c-dated", "21.T11975/a-undated", "21.T11975/b-garbled"),
        result.getHits().stream().map(ApiPidinstRecord::getPid).toList());
  }

  @Test
  void pageOneIsBuiltFromTheFirstTwoPagesOfEachRegistryAndHoldsFiftyHits() {
    when(b2instConnector.searchPublicRecords("*Zeiss*", 0, 50))
        .thenReturn(fullB2instPage(120, "2026-05-01T00:00:00+00:00"));
    when(b2instConnector.searchPublicRecords("*Zeiss*", 1, 50))
        .thenReturn(fullB2instPage(120, "2026-04-01T00:00:00+00:00"));
    when(dataCiteConnector.searchPublicInstrumentDois("Zeiss", 0, 50))
        .thenReturn(
            dataCitePage(
                1,
                dataCiteInstrumentUpdatedAt(
                    "10.1/only", "2026-06-01T00:00:00Z", "2026-06-01T00:00:00Z")));

    ApiPidinstSearchResult result = manager.search("Zeiss", BOTH, 1, user);

    assertEquals(50, result.getHits().size());
    assertEquals(121, result.getTotalHits());
    assertEquals(1, result.getPageNumber());
    // the DataCite page was short, so its second page was never asked for
    verify(dataCiteConnector, never()).searchPublicInstrumentDois("Zeiss", 1, 50);
    verify(b2instConnector).searchPublicRecords("*Zeiss*", 1, 50);
  }

  @Test
  void pagingThroughEqualUpdateTimesAcrossARegistryPageBoundaryShowsEveryHitOnce() {
    String sameUpdate = "2026-05-01T00:00:00+00:00";
    B2instDraftRecord[] firstPage = new B2instDraftRecord[50];
    for (int i = 0; i < 50; i++) {
      firstPage[i] =
          publishedRecord(
              "21.T11975/first-" + i, "first-" + i, sameUpdate, "2025-01-01T00:00:00+00:00");
    }
    when(b2instConnector.searchPublicRecords("*Zeiss*", 0, 50))
        .thenReturn(searchResultOf(51, firstPage));
    // the registry orders by update time only, so its page 2 may hold a newer creation
    when(b2instConnector.searchPublicRecords("*Zeiss*", 1, 50))
        .thenReturn(
            searchResultOf(
                51,
                publishedRecord(
                    "21.T11975/late", "late", sameUpdate, "2026-04-01T00:00:00+00:00")));

    List<String> seen = new ArrayList<>();
    for (int page = 0; page <= 1; page++) {
      manager.search("Zeiss", List.of("PIDINST_B2INST"), page, user).getHits().stream()
          .map(ApiPidinstRecord::getPid)
          .forEach(seen::add);
    }

    assertEquals(51, seen.size());
    assertEquals(51, new HashSet<>(seen).size(), "a PID repeated: " + seen);
  }

  @Test
  void aRegistryWhosePageIsShortIsNotAskedForTheNextOne() {
    when(b2instConnector.searchPublicRecords("*Zeiss*", 0, 50))
        .thenReturn(
            searchResultOf(
                3,
                publishedRecord(
                    "21.T11975/a", "a", "2026-01-03T00:00:00+00:00", "2026-01-01T00:00:00+00:00"),
                publishedRecord(
                    "21.T11975/b", "b", "2026-01-02T00:00:00+00:00", "2026-01-01T00:00:00+00:00"),
                publishedRecord(
                    "21.T11975/c", "c", "2026-01-01T00:00:00+00:00", "2026-01-01T00:00:00+00:00")));

    ApiPidinstSearchResult page1 = manager.search("Zeiss", List.of("PIDINST_B2INST"), 1, user);

    assertTrue(page1.getHits().isEmpty());
    assertEquals(3, page1.getTotalHits());
    verify(b2instConnector, never()).searchPublicRecords(anyString(), eq(1), eq(50));
  }

  @Test
  void theDataCiteWildcardRetryRunsOnlyWhenFreeTextHasNoHitsAtAll() {
    // page 0 of "qvtb" finds nothing as free text, so the same page is asked as doi:*qvtb*
    when(dataCiteConnector.searchPublicInstrumentDois("qvtb", 0, 50)).thenReturn(dataCitePage(0));
    when(dataCiteConnector.searchPublicInstrumentDois("doi:*qvtb*", 0, 50))
        .thenReturn(
            dataCitePage(
                1,
                dataCiteInstrumentUpdatedAt(
                    "10.82316/qvtb-aw74", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z")));
    assertEquals(
        "10.82316/qvtb-aw74",
        manager.search("qvtb", List.of("PIDINST_DATACITE"), 0, user).getHits().get(0).getPid());

    // an exhausted page of a query that DID match as free text is not retried: that would mix
    // queries
    when(dataCiteConnector.searchPublicInstrumentDois("gco8", 0, 50))
        .thenReturn(fullDataCitePage(50));
    when(dataCiteConnector.searchPublicInstrumentDois("gco8", 1, 50)).thenReturn(dataCitePage(50));
    manager.search("gco8", List.of("PIDINST_DATACITE"), 1, user);
    verify(dataCiteConnector, never())
        .searchPublicInstrumentDois(eq("doi:*gco8*"), anyInt(), anyInt());
  }

  @ParameterizedTest
  @MethodSource("badRegistries")
  void withNoRegistryOrAnUnknownOneTheSearchIs422(List<String> providers) {
    ApiRuntimeException thrown =
        assertThrows(ApiRuntimeException.class, () -> manager.search("Zeiss", providers, 0, user));
    assertEquals("errors.inventory.identifier.pidinstRegistryRequired", thrown.getErrorCode());
    verifyNoInteractions(b2instConnector, dataCiteConnector);
  }

  static Stream<Arguments> badRegistries() {
    return Stream.of(
        arguments((List<String>) null),
        arguments(List.of()),
        arguments(List.of("IGSN_DATACITE")),
        arguments(List.of("PIDINST_B2INST", "pidinst_b2inst_typo")));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, PidinstLookupManager.MAX_PAGE_NUMBER + 1})
  void aPageOutsideTheRangeIsRefusedBeforeAnyRegistryCall(int pageNumber) {
    ApiRuntimeException thrown =
        assertThrows(
            ApiRuntimeException.class, () -> manager.search("Zeiss", BOTH, pageNumber, user));

    assertEquals("errors.inventory.identifier.pidinstPageOutOfRange", thrown.getErrorCode());
    verifyNoInteractions(b2instConnector, dataCiteConnector);
  }

  @Test
  void aRegistryNameIsMatchedIgnoringCaseAndPadding() {
    when(b2instConnector.searchPublicRecords("*Zeiss*", 0, 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));

    assertEquals(
        List.of("PIDINST_B2INST"),
        manager.search("Zeiss", List.of(" pidinst_b2inst "), 0, user).getProviders());
  }

  @Test
  void theLookupNeedsNoEnabledProvider() {
    when(b2instConnector.searchPublicRecords("*Zeiss*", 0, 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));

    assertEquals(1, manager.search("Zeiss", List.of("PIDINST_B2INST"), 0, user).getHits().size());

    verify(b2instConnector, never()).isConfiguredAndEnabled();
    verify(dataCiteConnector, never()).isDataCiteConfiguredAndEnabled(any());
  }

  @Test
  void aDoiCountsOnlyAtDataCiteAndAHandleOnlyAtB2instWhenBothAreTicked() {
    when(dataCiteConnector.findPublicDoi(DOI))
        .thenReturn(Optional.of(dataCiteInstrument(DOI, "findable", "Instrument")));

    ApiPidinstSearchResult result = manager.search(DOI, BOTH, 0, user);

    assertEquals(1, result.getHits().size());
    assertEquals(1, result.getTotalsByProvider().get("PIDINST_DATACITE"));
    assertEquals(0, result.getTotalsByProvider().get("PIDINST_B2INST"));
    verify(b2instConnector, never()).searchPublicRecords(anyString(), anyInt(), anyInt());
    verify(b2instConnector, never()).getPublicRecordByHandle(anyString());
  }

  @Test
  void linkedHitsAreAnnotatedWithOneQueryPerRegistry() {
    when(b2instConnector.searchPublicRecords("*Zeiss*", 0, 50))
        .thenReturn(searchResultOf(publishedRecord(), 1));
    when(dataCiteConnector.searchPublicInstrumentDois("Zeiss", 0, 50))
        .thenReturn(dataCitePage(1, dataCiteInstrument(DOI, "findable", "Instrument")));

    manager.search("Zeiss", BOTH, 0, user);

    verify(doiDao)
        .findActiveByIdentifiersAndType(
            List.of(HANDLE, "abcde-12345"), IdentifierType.PIDINST_B2INST);
    verify(doiDao).findActiveByIdentifiersAndType(List.of(DOI), IdentifierType.PIDINST_DATACITE);
  }

  @Test
  void importGoesToTheNamedRegistryAndRefusesAnUnknownOne() {
    DataCiteDoi doi = dataCiteInstrument(DOI, "findable", "Instrument");
    // the locked template's mandatory Owner and Manufacturer: publisher fallback and a creator
    doi.getAttributes().setPublisher("ESRF");
    DataCiteDoiAttributes.Creator creator = new DataCiteDoiAttributes.Creator();
    creator.setName("ESRF");
    doi.getAttributes().setCreators(List.of(creator));
    when(dataCiteConnector.findPublicDoi(DOI)).thenReturn(Optional.of(doi));
    stubImportCollaborators();

    manager.importInstrument(DOI, "PIDINST_DATACITE", null, user);
    verify(dataCiteConnector).findPublicDoi(DOI);
    verify(b2instConnector, never()).getPublicRecordByHandle(anyString());

    ApiRuntimeException thrown =
        assertThrows(
            ApiRuntimeException.class,
            () -> manager.importInstrument(DOI, "IGSN_DATACITE", null, user));
    assertEquals(
        "errors.inventory.identifier.pidinstImportProviderRequired", thrown.getErrorCode());
  }
}
