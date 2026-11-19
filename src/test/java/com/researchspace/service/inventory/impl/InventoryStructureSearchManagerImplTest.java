package com.researchspace.service.inventory.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.axiope.search.SearchManager;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.booking.service.BookableInstrumentLookup;
import com.researchspace.dao.InventoryFileDao;
import com.researchspace.dao.RSChemElementDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecord.InventoryRecordType;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.InventoryStructureHit;
import com.researchspace.service.inventory.SampleApiManager;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryStructureSearchManagerImplTest {

  @Mock private RSChemElementDao rsChemElementDao;
  @Mock private InventoryFileDao inventoryFileDao;
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private BookableInstrumentLookup bookableInstrumentLookup;
  @Mock private SampleApiManager sampleApiManager;
  @Mock private SearchManager searchManager;
  @Mock private IPropertyHolder properties;
  @Mock private IntegrationsHandler integrationsHandler;
  @InjectMocks private InventoryStructureSearchManagerImpl manager;

  @Captor private ArgumentCaptor<List<? extends InventoryRecord>> pagedRecords;

  private final PaginationCriteria<InventoryRecord> pgCrit =
      PaginationCriteria.createDefaultForClass(InventoryRecord.class);
  private final ApiInventorySearchResult page = new ApiInventorySearchResult();
  private User user;

  @BeforeEach
  void setUp() {
    user = new User("searcher");
  }

  private List<Long> chemHits = List.of();

  private void stubChemistryHits(Long... chemIds) {
    chemHits = List.of(chemIds);
  }

  /** Chem hits 1 and 2 resolve to Gallery file 10, attached to the given records and fields. */
  private void stubAttachments(List<InventoryRecord> attachedTo, List<InventoryRecord> fieldOf) {
    stubChemistryHits(1L, 2L);
    when(rsChemElementDao.getGalleryChemFileIds(List.of(1L, 2L))).thenReturn(List.of(10L));
    List<InventoryFile> files = attachedTo.stream().map(this::fileOn).toList();
    List<InventoryAttachmentField> fields = fieldOf.stream().map(this::fieldOn).toList();
    when(inventoryFileDao.findByMediaFileIds(List.of(10L))).thenReturn(files);
    when(inventoryFileDao.findAttachmentFieldsByMediaFileIds(List.of(10L))).thenReturn(fields);
  }

  private InventoryFile fileOn(InventoryRecord rec) {
    InventoryFile file = mock(InventoryFile.class);
    when(file.getInventoryRecord()).thenReturn(rec);
    return file;
  }

  private InventoryAttachmentField fieldOn(InventoryRecord rec) {
    InventoryAttachmentField field = mock(InventoryAttachmentField.class);
    when(field.getInventoryRecord()).thenReturn(rec);
    return field;
  }

  private InventoryRecord record(String globalId) {
    InventoryRecord rec = mock(InventoryRecord.class);
    when(rec.getGlobalIdentifier()).thenReturn(globalId);
    return rec;
  }

  private InventoryRecord readable(String globalId) {
    InventoryRecord rec = record(globalId);
    when(invPermissions.canUserReadInventoryRecord(rec, user)).thenReturn(true);
    return rec;
  }

  private ApiInventorySearchResult search(
      String text,
      InventorySearchType type,
      String ownedBy,
      InventorySearchDeletedOption deleted,
      Boolean bookable) {
    return manager.searchByStructure(
        chemHits, text, type, ownedBy, deleted, bookable, pgCrit, user);
  }

  private List<InventoryRecord> searchAndCapturePage(
      InventorySearchType type,
      String ownedBy,
      InventorySearchDeletedOption deleted,
      Boolean bookable) {
    when(sampleApiManager.sortRepaginateConvertToApiInventorySearchResult(
            eq(pgCrit), pagedRecords.capture(), eq(user)))
        .thenReturn(page);
    assertSame(page, search(null, type, ownedBy, deleted, bookable));
    return List.copyOf(pagedRecords.getValue());
  }

  @Test
  void returnsOwnersOfRecordAndFieldAttachments() {
    InventoryRecord subSample = readable("SS1");
    InventoryRecord template = readable("IT2");
    stubAttachments(List.of(subSample), List.of(template));

    assertThat(searchAndCapturePage(null, null, null, null)).containsExactly(subSample, template);
  }

  @Test
  void returnsRecordOnceWhenSeveralAttachmentsMatch() {
    InventoryRecord sample = readable("SA1");
    stubAttachments(List.of(sample, sample), List.of(sample));

    assertThat(searchAndCapturePage(null, null, null, null)).containsExactly(sample);
  }

  @Test
  void skipsRecordsTheUserCannotRead() {
    InventoryRecord mine = readable("SA1");
    InventoryRecord notMine = record("SA2");
    stubAttachments(List.of(mine, notMine), List.of());

    assertThat(searchAndCapturePage(null, null, null, null)).containsExactly(mine);
  }

  @Test
  void excludesDeletedRecordsByDefault() {
    InventoryRecord live = readable("SA1");
    InventoryRecord deleted = readable("SA2");
    when(deleted.isDeleted()).thenReturn(true);
    stubAttachments(List.of(live, deleted), List.of());

    assertThat(searchAndCapturePage(null, null, null, null)).containsExactly(live);
  }

  @Test
  void deletedOnlyKeepsDeletedRecords() {
    InventoryRecord live = readable("SA1");
    InventoryRecord deleted = readable("SA2");
    when(deleted.isDeleted()).thenReturn(true);
    stubAttachments(List.of(live, deleted), List.of());

    assertThat(searchAndCapturePage(null, null, InventorySearchDeletedOption.DELETED_ONLY, null))
        .containsExactly(deleted);
  }

  @Test
  void filtersByResultTypeAndOwner() {
    InventoryRecord alicesSample = readable("SA1");
    when(alicesSample.getType()).thenReturn(InventoryRecordType.SAMPLE);
    when(alicesSample.getOwner()).thenReturn(new User("alice"));
    InventoryRecord bobsSample = readable("SA2");
    when(bobsSample.getType()).thenReturn(InventoryRecordType.SAMPLE);
    when(bobsSample.getOwner()).thenReturn(new User("bob"));
    InventoryRecord alicesContainer = readable("IC3");
    when(alicesContainer.getType()).thenReturn(InventoryRecordType.CONTAINER);
    stubAttachments(List.of(alicesSample, bobsSample, alicesContainer), List.of());

    assertThat(searchAndCapturePage(InventorySearchType.SAMPLE, "alice", null, null))
        .containsExactly(alicesSample);
  }

  @Test
  void bookableFilterKeepsOnlyBookableInstruments() {
    InventoryRecord bookableInstrument = readable("IN1");
    when(bookableInstrument.isInstrument()).thenReturn(true);
    when(bookableInstrument.getId()).thenReturn(1L);
    InventoryRecord sample = readable("SA2");
    when(bookableInstrumentLookup.findReadableBookableInstrumentIds(user)).thenReturn(Set.of(1L));
    stubAttachments(List.of(bookableInstrument, sample), List.of());

    assertThat(searchAndCapturePage(null, null, null, true)).containsExactly(bookableInstrument);
  }

  @Test
  void notBookableFilterDropsBookableInstruments() {
    InventoryRecord bookableInstrument = readable("IN1");
    when(bookableInstrument.isInstrument()).thenReturn(true);
    when(bookableInstrument.getId()).thenReturn(1L);
    InventoryRecord sample = readable("SA2");
    when(bookableInstrumentLookup.findReadableBookableInstrumentIds(user)).thenReturn(Set.of(1L));
    stubAttachments(List.of(bookableInstrument, sample), List.of());

    assertThat(searchAndCapturePage(null, null, null, false)).containsExactly(sample);
  }

  @Test
  void noBookableFilterSkipsBookingLookup() {
    InventoryRecord sample = readable("SA1");
    stubAttachments(List.of(sample), List.of());

    searchAndCapturePage(null, null, null, null);

    verifyNoInteractions(bookableInstrumentLookup);
  }

  @Test
  void noChemistryHitsQueriesNoAttachments() {
    stubChemistryHits();

    assertThat(searchAndCapturePage(null, null, null, null)).isEmpty();

    verifyNoInteractions(rsChemElementDao, inventoryFileDao);
  }

  @Test
  void queriesChemIdsInBatches() {
    manager.setIdsBatchSize(2);
    stubChemistryHits(1L, 2L, 3L);
    when(rsChemElementDao.getGalleryChemFileIds(List.of(1L, 2L))).thenReturn(List.of(10L));
    when(rsChemElementDao.getGalleryChemFileIds(List.of(3L))).thenReturn(List.of(10L));
    InventoryRecord sample = readable("SA1");
    InventoryFile file = fileOn(sample);
    when(inventoryFileDao.findByMediaFileIds(List.of(10L))).thenReturn(List.of(file));
    when(inventoryFileDao.findAttachmentFieldsByMediaFileIds(List.of(10L))).thenReturn(List.of());

    assertThat(searchAndCapturePage(null, null, null, null)).containsExactly(sample);
  }

  @Test
  void stopsResolvingAttachmentsOnceTheLimitIsPassed() {
    manager.setIdsBatchSize(1);
    manager.setMaxStructureHits(1);
    stubChemistryHits(1L, 2L, 3L);
    when(rsChemElementDao.getGalleryChemFileIds(List.of(1L))).thenReturn(List.of(10L));
    when(rsChemElementDao.getGalleryChemFileIds(List.of(2L))).thenReturn(List.of(11L));
    when(rsChemElementDao.getGalleryChemFileIds(List.of(3L))).thenReturn(List.of(12L));
    InventoryFile firstFile = fileOn(readable("SA1"));
    InventoryFile secondFile = fileOn(readable("SA2"));
    when(inventoryFileDao.findByMediaFileIds(List.of(10L))).thenReturn(List.of(firstFile));
    when(inventoryFileDao.findByMediaFileIds(List.of(11L))).thenReturn(List.of(secondFile));
    when(inventoryFileDao.findAttachmentFieldsByMediaFileIds(anyList())).thenReturn(List.of());

    assertThat(searchAndCapturePage(null, null, null, null))
        .containsExactly(firstFile.getInventoryRecord());

    assertTrue(page.getStructureSearchLimitReached());
    verify(inventoryFileDao, never()).findByMediaFileIds(List.of(12L));
  }

  @Test
  void recordsFilteredOutDoNotCountTowardsTheLimit() {
    manager.setMaxStructureHits(1);
    InventoryRecord unreadable = record("SA1");
    when(invPermissions.canUserReadInventoryRecord(unreadable, user)).thenReturn(false);
    InventoryRecord readable = readable("SA2");
    stubAttachments(List.of(unreadable, readable), List.of());

    assertThat(searchAndCapturePage(null, null, null, null)).containsExactly(readable);

    assertNull(page.getStructureSearchLimitReached());
  }

  @Test
  void textQueryReportsTheStructureLimit() {
    manager.setMaxStructureHits(1);
    InventoryRecord first = readable("SA1");
    InventoryRecord second = readable("SA2");
    stubAttachments(List.of(first, second), List.of());
    when(searchManager.searchInventoryWithSimpleQuery(
            "buffer", null, null, null, null, null, List.of("SA1"), pgCrit, user))
        .thenReturn(page);

    assertSame(page, search("buffer", null, null, null, null));

    assertTrue(page.getStructureSearchLimitReached());
  }

  @Test
  void workspaceListingStopsAtTheLimit() {
    manager.setMaxStructureHits(1);
    when(rsChemElementDao.getGalleryChemFileIds(List.of(1L))).thenReturn(List.of(10L));
    InventoryRecord first = readable("SA1");
    when(first.getOwner()).thenReturn(new User("owner"));
    InventoryFile firstFile = fileOn(first);
    InventoryFile pastTheLimit = mock(InventoryFile.class);
    when(inventoryFileDao.findByMediaFileIds(List.of(10L)))
        .thenReturn(List.of(firstFile, pastTheLimit));
    when(inventoryFileDao.findAttachmentFieldsByMediaFileIds(List.of(10L))).thenReturn(List.of());

    assertThat(manager.findReadableStructureHits(List.of(1L), user))
        .extracting(InventoryStructureHit::globalId)
        .containsExactly("SA1");
  }

  @Test
  void textQueryLimitsTextSearchToStructureHits() {
    InventoryRecord sample = readable("SA1");
    InventoryRecord subSample = readable("SS2");
    stubAttachments(List.of(sample, subSample), List.of());
    when(searchManager.searchInventoryWithSimpleQuery(
            "buffer",
            InventorySearchType.ALL,
            null,
            "alice",
            InventorySearchDeletedOption.INCLUDE,
            null,
            List.of("SA1", "SS2"),
            pgCrit,
            user))
        .thenReturn(page);
    when(sample.getOwner()).thenReturn(new User("alice"));
    when(subSample.getOwner()).thenReturn(new User("alice"));

    assertSame(
        page,
        search(
            "buffer",
            InventorySearchType.ALL,
            "alice",
            InventorySearchDeletedOption.INCLUDE,
            null));

    verifyNoInteractions(sampleApiManager);
  }

  @Test
  void textQueryWithNoStructureHitsSkipsTextSearch() {
    stubChemistryHits();

    ApiInventorySearchResult result = search("buffer", null, null, null, null);

    assertThat(result.getRecords()).isEmpty();
    verify(searchManager, never())
        .searchInventoryWithSimpleQuery(
            any(), any(), isNull(), any(), any(), any(), anyList(), any(), any());
  }

  private void stubChemistryIntegration(boolean available, boolean enabled) {
    when(properties.getChemistryProvider()).thenReturn("indigo");
    IntegrationInfo info = new IntegrationInfo();
    info.setAvailable(available);
    info.setEnabled(enabled);
    when(integrationsHandler.getIntegration(user, "CHEMISTRY")).thenReturn(info);
  }

  @Test
  void structureSearchAvailableWithIndigoAndEnabledChemistryApp() {
    stubChemistryIntegration(true, true);

    assertTrue(manager.isStructureSearchAvailable(user));
  }

  @Test
  void structureSearchUnavailableWithoutIndigoProvider() {
    when(properties.getChemistryProvider()).thenReturn("");

    assertFalse(manager.isStructureSearchAvailable(user));
    verifyNoInteractions(integrationsHandler);
  }

  @Test
  void structureSearchUnavailableWhenChemistryAppNotAllowed() {
    stubChemistryIntegration(false, true);

    assertFalse(manager.isStructureSearchAvailable(user));
  }

  @Test
  void structureSearchUnavailableWhenUserHasNotEnabledChemistryApp() {
    stubChemistryIntegration(true, false);

    assertFalse(manager.isStructureSearchAvailable(user));
  }

  @Test
  void workspaceListingGetsReadableLiveHits() {
    when(rsChemElementDao.getGalleryChemFileIds(List.of(1L, 2L))).thenReturn(List.of(10L));
    InventoryRecord sample = readable("SA1");
    when(sample.getName()).thenReturn("aspirin batch");
    when(sample.getType()).thenReturn(InventoryRecordType.SAMPLE);
    User owner = new User("owner");
    owner.setFirstName("Ann");
    owner.setLastName("Owner");
    when(sample.getOwner()).thenReturn(owner);
    Date modified = new Date();
    when(sample.getModificationDate()).thenReturn(modified);
    InventoryRecord deleted = readable("SA2");
    when(deleted.isDeleted()).thenReturn(true);
    InventoryRecord unreadable = record("SA3");
    List<InventoryFile> files = List.of(fileOn(sample), fileOn(deleted), fileOn(unreadable));
    when(inventoryFileDao.findByMediaFileIds(List.of(10L))).thenReturn(files);
    when(inventoryFileDao.findAttachmentFieldsByMediaFileIds(List.of(10L))).thenReturn(List.of());

    List<InventoryStructureHit> hits = manager.findReadableStructureHits(List.of(1L, 2L), user);

    assertEquals(
        List.of(
            new InventoryStructureHit(
                "SA1", "aspirin batch", InventoryRecordType.SAMPLE, owner.getFullName(), modified)),
        hits);
  }
}
