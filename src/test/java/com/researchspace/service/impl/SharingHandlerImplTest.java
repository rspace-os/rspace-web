package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailImpl;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.audittrail.HistoryDAO;
import com.researchspace.model.dto.SharingResult;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.IdConstraint;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.LocalizedIllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.testutils.TestFactory;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SharingHandlerImplTest {

  @Mock private RecordSharingManager sharingManager;

  @Mock private IPermissionUtils permissionUtils;
  @Mock private HistoryDAO historyDao;
  @Captor private ArgumentCaptor<Iterable<HistoricData>> historyCaptor;
  @Captor private ArgumentCaptor<ShareConfigElement[]> unshareCaptor;

  private SharingHandlerImpl handler;
  private User user;

  @BeforeEach
  void setUp() {
    handler = new SharingHandlerImpl();
    user = TestFactory.createAnyUser("sharer");
    ReflectionTestUtils.setField(handler, "sharingManager", sharingManager);
    ReflectionTestUtils.setField(
        handler, "messages", new MessageSourceUtils(new JsonMessageSource()));
  }

  @Test
  void shareResultResolvesLocalizedChildErrorArguments() {
    when(sharingManager.shareRecord(eq(user), eq(1L), any(ShareConfigElement[].class)))
        .thenThrow(new LocalizedIllegalAddChildOperation("folder.addChild.errors.cycle", "A", "B"));

    SharingResult result = handler.shareRecordsWithResult(command(), user);

    assertEquals(
        "Addition of [A] to  folder[B]  would produce a cycle in the folder tree,"
            + " which currently is not permitted.",
        result.getError().getErrorMessages().get(0));
  }

  @Test
  void shareResultPreservesOrdinaryIllegalAddChildMessage() {
    when(sharingManager.shareRecord(eq(user), eq(1L), any(ShareConfigElement[].class)))
        .thenThrow(new IllegalAddChildOperation("legacy failure"));

    SharingResult result = handler.shareRecordsWithResult(command(), user);

    assertEquals("legacy failure", result.getError().getErrorMessages().get(0));
  }

  @Test
  void unshareAuditsRecordAndPreservesSharingMetadata() {
    StructuredDocument record = TestFactory.createAnySD();
    record.setId(123L);
    Group recipient =
        TestFactory.createAnyGroup(TestFactory.createAnyUserWithRole("pi", "ROLE_PI"));
    recipient.setId(456L);
    Folder target = TestFactory.createAFolder("Shared destination", user);
    target.setId(789L);
    RecordGroupSharing share = new RecordGroupSharing(recipient, record);
    ReflectionTestUtils.setField(share, "id", 42L);
    share.setTargetFolder(target);
    ConstraintBasedPermission permission =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
    when(sharingManager.get(42L)).thenReturn(share);
    when(permissionUtils.findBy(
            eq(recipient.getPermissions()),
            eq(PermissionDomain.RECORD),
            eq(new IdConstraint(123L))))
        .thenReturn(permission);
    AuditTrailImpl audit = new AuditTrailImpl();
    audit.setHistoryDao(historyDao);
    ReflectionTestUtils.setField(handler, "auditService", audit);
    ReflectionTestUtils.setField(handler, "permissionUtils", permissionUtils);

    assertTrue(handler.unshare(42L, user).isSucceeded());

    verify(sharingManager).unshareRecord(eq(user), eq(123L), unshareCaptor.capture());
    assertEquals(recipient.getId(), unshareCaptor.getValue()[0].getGroupid());
    verify(historyDao).save(historyCaptor.capture());
    HistoricData event = historyCaptor.getValue().iterator().next();
    assertEquals(AuditAction.UNSHARE, event.getAction());
    assertEquals(AuditDomain.RECORD, event.getDomain());
    var data = event.getData().getData();
    assertEquals(record.getGlobalIdentifier(), data.get("id"));
    assertEquals(record.getName(), data.get("name"));
    var sharee = (Map<?, ?>) data.get("sharee");
    assertEquals(recipient.getUniqueName(), sharee.get("uniqueName"));
    assertEquals(recipient.getOid().toString(), sharee.get("oid"));
    assertEquals(
        record.getGlobalIdentifier(),
        ((Map<?, ?>) data.get("sharedRecordId")).get("globalIdentifier"));
    assertEquals(
        target.getGlobalIdentifier(),
        ((Map<?, ?>) data.get("targetFolderId")).get("globalIdentifier"));
  }

  private ShareConfigCommand command() {
    return new ShareConfigCommand(new Long[] {1L}, new ShareConfigElement[0]);
  }
}
