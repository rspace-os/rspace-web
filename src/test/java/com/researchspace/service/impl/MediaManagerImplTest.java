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
    when(permUtils.isRecordAccessPermitted(user, readableDoc, PermissionType.READ))
        .thenReturn(true);
    when(permUtils.isRecordAccessPermitted(user, unreadableDoc, PermissionType.READ))
        .thenReturn(false);

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
  void getIdsOfLinkedDocumentsReturnsOwnerlessPlaceholderWhenLinkedRecordNotLoaded() {
    // Defensive branch: a linked-row id missing from the batch load still yields a private
    // placeholder (with no owner name) rather than an NPE or a leaked row
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(mediaFile));
    when(permUtils.isRecordAccessPermitted(user, mediaFile, PermissionType.READ)).thenReturn(true);
    RecordInformation info = new RecordInformation();
    info.setId(5L);
    when(recordDao.getInfosOfDocumentsLinkedToMediaFile(1L)).thenReturn(List.of(info));
    when(recordDao.getRecordsById(List.of(5L))).thenReturn(List.of());

    List<RecordInformation> result = mediaManager.getIdsOfLinkedDocuments(1L, user);

    assertEquals(1, result.size());
    assertNull(result.get(0).getId());
    assertNull(result.get(0).getOwnerFullName());
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
    when(permUtils.isRecordAccessPermitted(user, doc5, PermissionType.READ)).thenReturn(true);
    when(permUtils.isRecordAccessPermitted(user, doc6, PermissionType.READ)).thenReturn(true);

    mediaManager.getIdsOfLinkedDocuments(1L, user);

    verify(recordDao, times(1)).getRecordsById(List.of(5L, 6L));
    // only the media-file lookup, never one per linked document
    verify(recordDao, times(1)).getSafeNull(anyLong());
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
    when(permUtils.isRecordAccessPermitted(user, linkedDoc, PermissionType.READ)).thenReturn(true);

    List<RecordInformation> result = mediaManager.getIdsOfLinkedDocuments(1L, user);

    assertEquals(1, result.size());
    assertEquals(GlobalIdPrefix.SD, result.get(0).getOid().getPrefix());
  }
}
