package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.dao.FieldDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
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
class FieldManagerImplTest {

  @Mock private FieldDao fieldDao;
  @Mock private RecordDao recordDao;
  @Mock private IPermissionUtils permUtils;
  @Mock private MessageSourceUtils messages;
  @InjectMocks private FieldManagerImpl fieldManager;

  private User user;
  private StructuredDocument doc;

  @BeforeEach
  void setUp() {
    user = TestFactory.createAnyUser("any");
    doc = TestFactory.createAnySD();
    doc.setId(1L);
  }

  @Test
  void getFieldsByRecordIdThrowsForNullUser() {
    // RSDEV-1329: must fail closed, never return field content without a subject
    assertThrows(AuthorizationException.class, () -> fieldManager.getFieldsByRecordId(1L, null));
    verifyNoInteractions(fieldDao);
  }

  @Test
  void getFieldsByRecordIdThrowsForAnonymousGuestAccount() {
    // RSDEV-1329: the published-view guest must never read draft (autosaved) field content.
    // The happy path is stubbed leniently so the ONLY thing that can refuse is the guest check;
    // without these stubs the "record not found" branch would throw first and the test would
    // still pass with the guest check deleted.
    User anonymousGuest = TestFactory.createAnyUser(RecordGroupSharing.ANONYMOUS_USER);
    lenient().when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(doc));
    lenient()
        .when(permUtils.isRecordAccessPermitted(anonymousGuest, doc, PermissionType.READ))
        .thenReturn(true);

    assertThrows(
        AuthorizationException.class, () -> fieldManager.getFieldsByRecordId(1L, anonymousGuest));
    verifyNoInteractions(fieldDao);
  }

  @Test
  void getFieldsByRecordIdThrowsForUnknownRecordId() {
    // a missing record must be indistinguishable from a forbidden one (no existence oracle)
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.empty());

    assertThrows(AuthorizationException.class, () -> fieldManager.getFieldsByRecordId(1L, user));
    verifyNoInteractions(fieldDao);
  }

  @Test
  void getFieldsByRecordIdThrowsForUserWithoutReadPermission() {
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(doc));
    when(permUtils.isRecordAccessPermitted(user, doc, PermissionType.READ)).thenReturn(false);

    assertThrows(AuthorizationException.class, () -> fieldManager.getFieldsByRecordId(1L, user));
    verifyNoInteractions(fieldDao);
  }

  @Test
  void getFieldsByRecordIdReturnsFieldsForPermittedUser() {
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(doc));
    when(permUtils.isRecordAccessPermitted(user, doc, PermissionType.READ)).thenReturn(true);
    List<Field> fields = List.of(doc.getFields().iterator().next());
    when(fieldDao.getFieldFromStructuredDocument(1L)).thenReturn(fields);

    assertEquals(fields, fieldManager.getFieldsByRecordId(1L, user));
    verifyNoInteractions(messages);
  }

  @Test
  void getAutoSavedFieldsRequiresWriteNotRead() {
    // RSDEV-1329: PermissionUtils short-circuits READ to true for ANY user on a published record,
    // so a READ gate would hand the owner's unsaved editing buffer to every logged-in account once
    // the document is published. Only WRITE (edit rights) may open the draft.
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(doc));
    lenient()
        .when(permUtils.isRecordAccessPermitted(user, doc, PermissionType.READ))
        .thenReturn(true);
    when(permUtils.isRecordAccessPermitted(user, doc, PermissionType.WRITE)).thenReturn(false);

    assertThrows(
        AuthorizationException.class, () -> fieldManager.getAutoSavedFieldsByRecordId(1L, user));
    verifyNoInteractions(fieldDao);
  }

  @Test
  void getAutoSavedFieldsReturnsFieldsForUserWithWritePermission() {
    when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(doc));
    when(permUtils.isRecordAccessPermitted(user, doc, PermissionType.WRITE)).thenReturn(true);
    List<Field> fields = List.of(doc.getFields().iterator().next());
    when(fieldDao.getFieldFromStructuredDocument(1L)).thenReturn(fields);

    assertEquals(fields, fieldManager.getAutoSavedFieldsByRecordId(1L, user));
    verifyNoInteractions(messages);
  }

  @Test
  void getAutoSavedFieldsThrowsForAnonymousGuestAccount() {
    User anonymousGuest = TestFactory.createAnyUser(RecordGroupSharing.ANONYMOUS_USER);
    lenient().when(recordDao.getSafeNull(1L)).thenReturn(Optional.of(doc));
    lenient()
        .when(permUtils.isRecordAccessPermitted(anonymousGuest, doc, PermissionType.WRITE))
        .thenReturn(true);

    assertThrows(
        AuthorizationException.class,
        () -> fieldManager.getAutoSavedFieldsByRecordId(1L, anonymousGuest));
    verifyNoInteractions(fieldDao);
  }

  @Test
  void getAutoSavedFieldsThrowsForNullUser() {
    assertThrows(
        AuthorizationException.class, () -> fieldManager.getAutoSavedFieldsByRecordId(1L, null));
    verifyNoInteractions(fieldDao);
  }
}
