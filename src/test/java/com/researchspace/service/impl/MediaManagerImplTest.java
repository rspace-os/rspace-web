package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.RecordDao;
import com.researchspace.model.EcatImage;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaManagerImplTest {

  @Mock private RecordDao recordDao;
  @Mock private IPermissionUtils permUtils;
  @Mock private MessageSourceUtils messages;
  @InjectMocks private MediaManagerImpl mediaManager;

  private User user;
  private EcatImage mediaFile;

  @BeforeEach
  void setUp() {
    user = TestFactory.createAnyUser("any");
    mediaFile = TestFactory.createEcatImage(1L);
  }

  @Test
  void getIdsOfLinkedDocumentsThrowsForNullUser() {
    // RSDEV-1329: must fail closed, never reveal linked-document info without a subject
    assertThrows(
        AuthorizationException.class, () -> mediaManager.getIdsOfLinkedDocuments(1L, null));
    verify(recordDao, never()).getInfosOfDocumentsLinkedToMediaFile(anyLong());
  }

  @Test
  void getIdsOfLinkedDocumentsThrowsForUnknownMediaId() {
    // a missing media file must be indistinguishable from a forbidden one (no existence oracle)
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.empty());

    assertThrows(
        AuthorizationException.class, () -> mediaManager.getIdsOfLinkedDocuments(1L, user));
    verify(recordDao, never()).getInfosOfDocumentsLinkedToMediaFile(anyLong());
    verify(messages)
        .getMessage(eq("errors.authorization.failure.listLinkedDocuments"), any(Object[].class));
  }

  @Test
  void getIdsOfLinkedDocumentsThrowsForUserWithoutReadPermission() {
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(mediaFile));
    when(permUtils.isRecordAccessPermitted(user, mediaFile, PermissionType.READ)).thenReturn(false);

    assertThrows(
        AuthorizationException.class, () -> mediaManager.getIdsOfLinkedDocuments(1L, user));
    verify(recordDao, never()).getInfosOfDocumentsLinkedToMediaFile(anyLong());
    // same refusal message as the unknown-id branch: an authenticated caller must not be able
    // to distinguish an existing inaccessible media id from a nonexistent one
    verify(messages)
        .getMessage(eq("errors.authorization.failure.listLinkedDocuments"), any(Object[].class));
  }

  @Test
  void getIdsOfLinkedDocumentsThrowsForAnonymousGuestAccount() {
    // RSDEV-1329: the published-view guest can hold READ on published media via media links,
    // but must never see which documents link a file. The happy path is stubbed leniently so the
    // ONLY thing that can refuse is the guest check; without these stubs the "media not found"
    // branch would throw first and the test would pass with the guest check deleted.
    User anonymousGuest = TestFactory.createAnyUser(RecordGroupSharing.ANONYMOUS_USER);
    lenient().when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(mediaFile));
    lenient()
        .when(permUtils.isRecordAccessPermitted(anonymousGuest, mediaFile, PermissionType.READ))
        .thenReturn(true);

    assertThrows(
        AuthorizationException.class,
        () -> mediaManager.getIdsOfLinkedDocuments(1L, anonymousGuest));
    verify(recordDao, never()).getInfosOfDocumentsLinkedToMediaFile(anyLong());
  }

  @Test
  void getIdsOfLinkedDocumentsReplacesUnreadableDocumentsWithOwnerPlaceholders() {
    // RSDEV-1329: unreadable rows are NOT dropped. The frontend
    // (modules/workspace/schema.ts) treats a row with no id/oid as a private document and counts
    // it into "N private documents by <owner>", so dropping them would silently zero that count.
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(mediaFile));
    when(permUtils.isRecordAccessPermitted(user, mediaFile, PermissionType.READ)).thenReturn(true);
    RecordInformation readable = new RecordInformation();
    readable.setId(5L);
    RecordInformation privateDoc = new RecordInformation();
    privateDoc.setId(6L);
    when(recordDao.getInfosOfDocumentsLinkedToMediaFile(1L))
        .thenReturn(List.of(readable, privateDoc));
    StructuredDocument readableDoc = TestFactory.createAnySD();
    readableDoc.setId(5L);
    StructuredDocument unreadableDoc = TestFactory.createAnySD();
    unreadableDoc.setId(6L);
    when(recordDao.getRecordsById(List.of(5L, 6L))).thenReturn(List.of(readableDoc, unreadableDoc));
    when(permUtils.isPermitted(readableDoc, PermissionType.READ, user)).thenReturn(true);
    when(permUtils.isPermitted(unreadableDoc, PermissionType.READ, user)).thenReturn(false);

    List<RecordInformation> result = mediaManager.getIdsOfLinkedDocuments(1L, user);

    assertEquals(2, result.size());
    assertEquals(5L, result.get(0).getId().longValue());
    // the private row keeps the owner's display name but reveals neither the document's identity
    // nor the owner's login name (usernames are credential identifiers; no consumer reads them)
    assertNull(result.get(1).getId());
    assertEquals(unreadableDoc.getOwner().getFullName(), result.get(1).getOwnerFullName());
    assertNull(result.get(1).getOwnerUsername());
  }

  @Test
  void getIdsOfLinkedDocumentsOmitsRowsWhoseRecordDoesNotResolve() {
    // Defensive branch: a linked-row id missing from the batch load cannot be shown to be
    // readable and has no owner to attribute a private row to. Emitting a placeholder would
    // render as "1 private docs belonging to " with a blank name in both consumers, so the row
    // is omitted instead (RSDEV-1329).
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(mediaFile));
    when(permUtils.isRecordAccessPermitted(user, mediaFile, PermissionType.READ)).thenReturn(true);
    RecordInformation info = new RecordInformation();
    info.setId(5L);
    when(recordDao.getInfosOfDocumentsLinkedToMediaFile(1L)).thenReturn(List.of(info));
    when(recordDao.getRecordsById(List.of(5L))).thenReturn(List.of());

    List<RecordInformation> result = mediaManager.getIdsOfLinkedDocuments(1L, user);

    assertEquals(List.of(), result);
  }

  @Test
  void getIdsOfLinkedDocumentsLoadsLinkedRecordsInOneBatch() {
    // RSDEV-1329: guards against the N+1 that a per-document getSafeNull lookup reintroduces
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(mediaFile));
    when(permUtils.isRecordAccessPermitted(user, mediaFile, PermissionType.READ)).thenReturn(true);
    RecordInformation first = new RecordInformation();
    first.setId(5L);
    RecordInformation second = new RecordInformation();
    second.setId(6L);
    when(recordDao.getInfosOfDocumentsLinkedToMediaFile(1L)).thenReturn(List.of(first, second));
    StructuredDocument doc5 = TestFactory.createAnySD();
    doc5.setId(5L);
    StructuredDocument doc6 = TestFactory.createAnySD();
    doc6.setId(6L);
    when(recordDao.getRecordsById(List.of(5L, 6L))).thenReturn(List.of(doc5, doc6));
    when(permUtils.isPermitted(doc5, PermissionType.READ, user)).thenReturn(true);
    when(permUtils.isPermitted(doc6, PermissionType.READ, user)).thenReturn(true);

    mediaManager.getIdsOfLinkedDocuments(1L, user);

    verify(recordDao, times(1)).getRecordsById(List.of(5L, 6L));
    // only the media-file lookup, never one per linked document
    verify(recordDao, times(1)).getSafeNull(anyLong());
  }

  @Test
  void getIdsOfLinkedDocumentsCountsADocumentEmbeddingTheSameFileTwiceOnlyOnce() {
    // RecordDaoHibernate.LINKED_DOCS_QUERY has no `distinct`, so a document that embeds the same
    // media file in two fields comes back as two rows. That was cosmetic before; now that
    // unreadable rows feed a per-owner "N private docs" count, the duplicate would report one
    // private document as two, so rows must be de-duplicated by document id.
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(mediaFile));
    when(permUtils.isRecordAccessPermitted(user, mediaFile, PermissionType.READ)).thenReturn(true);
    RecordInformation firstLink = new RecordInformation();
    firstLink.setId(6L);
    RecordInformation secondLink = new RecordInformation();
    secondLink.setId(6L);
    when(recordDao.getInfosOfDocumentsLinkedToMediaFile(1L))
        .thenReturn(List.of(firstLink, secondLink));
    StructuredDocument unreadableDoc = TestFactory.createAnySD();
    unreadableDoc.setId(6L);
    when(recordDao.getRecordsById(List.of(6L))).thenReturn(List.of(unreadableDoc));
    when(permUtils.isPermitted(unreadableDoc, PermissionType.READ, user)).thenReturn(false);

    List<RecordInformation> result = mediaManager.getIdsOfLinkedDocuments(1L, user);

    assertEquals(1, result.size());
    assertNull(result.get(0).getId());
  }

  @Test
  void getIdsOfLinkedDocumentsDoesNotGrantLinkingDocumentAccessViaMediaLinkFallback() {
    // RSDEV-1329: isRecordAccessPermitted ORs in isPermittedViaMediaLinksToRecords, which grants
    // READ on a record when the subject can read something it links to. That fallback is load
    // bearing for the media file itself, but applying it to a LINKING document would re-open the
    // very leak the placeholder mechanism exists to close, so the per-document filter must use
    // the plain permission check.
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(mediaFile));
    when(permUtils.isRecordAccessPermitted(user, mediaFile, PermissionType.READ)).thenReturn(true);
    RecordInformation info = new RecordInformation();
    info.setId(5L);
    when(recordDao.getInfosOfDocumentsLinkedToMediaFile(1L)).thenReturn(List.of(info));
    StructuredDocument linkedDoc = TestFactory.createAnySD();
    linkedDoc.setId(5L);
    when(recordDao.getRecordsById(List.of(5L))).thenReturn(List.of(linkedDoc));
    // the fallback would GRANT access to the linking document; the plain check refuses it. Lenient
    // because correct production code never consults the fallback for a linking document.
    lenient()
        .when(permUtils.isRecordAccessPermitted(user, linkedDoc, PermissionType.READ))
        .thenReturn(true);
    when(permUtils.isPermitted(linkedDoc, PermissionType.READ, user)).thenReturn(false);

    List<RecordInformation> result = mediaManager.getIdsOfLinkedDocuments(1L, user);

    assertEquals(1, result.size());
    assertNull(result.get(0).getId());
    assertEquals(linkedDoc.getOwner().getFullName(), result.get(0).getOwnerFullName());
  }

  @Test
  void getIdsOfLinkedDocumentsReturnsInfosForPermittedUser() {
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(mediaFile));
    when(permUtils.isRecordAccessPermitted(user, mediaFile, PermissionType.READ)).thenReturn(true);
    RecordInformation info = new RecordInformation();
    info.setId(5L);
    when(recordDao.getInfosOfDocumentsLinkedToMediaFile(1L)).thenReturn(List.of(info));
    StructuredDocument linkedDoc = TestFactory.createAnySD();
    linkedDoc.setId(5L);
    when(recordDao.getRecordsById(List.of(5L))).thenReturn(List.of(linkedDoc));
    when(permUtils.isPermitted(linkedDoc, PermissionType.READ, user)).thenReturn(true);

    List<RecordInformation> result = mediaManager.getIdsOfLinkedDocuments(1L, user);

    assertEquals(1, result.size());
    assertEquals(GlobalIdPrefix.SD, result.get(0).getOid().getPrefix());
  }
}
