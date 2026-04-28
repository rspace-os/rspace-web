package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.SharingHandler;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplateTransferServiceTest {

  @Mock AuditTrailService auditTrailService;

  @Mock RecordGroupSharingDao recordGroupSharingDao;

  @Mock FolderManager folderManager;

  @Mock RecordManager recordManager;

  @Mock BaseRecordManager baseRecordManager;

  @Mock SharingHandler recordSharingHandler;

  @InjectMocks TemplateTransferService templateTransferService;

  @Test
  void transferOwnershipDoesNothingWhenUserHasNoSharedTemplates() {
    User originalOwner = new User();
    originalOwner.setUsername("original");
    User newOwner = new User();
    newOwner.setUsername("new");

    Mockito.when(recordGroupSharingDao.getTemplatesSharedByUser(originalOwner))
        .thenReturn(Collections.emptyList());

    templateTransferService.transferOwnership(originalOwner, newOwner);

    Mockito.verify(recordGroupSharingDao).getTemplatesSharedByUser(originalOwner);
    Mockito.verifyNoMoreInteractions(recordGroupSharingDao);
    Mockito.verifyNoInteractions(
        folderManager, recordManager, recordSharingHandler, auditTrailService);
    Mockito.verifyNoInteractions(baseRecordManager);
  }

  @Test
  void transferOwnershipUnsharesMovesTransfersAndNotifiesAuditTrail_whenFoldersExist() {
    templateTransferService =
        new TemplateTransferService(
            auditTrailService,
            recordGroupSharingDao,
            folderManager,
            recordManager,
            baseRecordManager,
            recordSharingHandler);

    User originalOwner = new User();
    originalOwner.setUsername("original");
    User newOwner = new User();
    newOwner.setUsername("new");

    BaseRecord template1 = Mockito.mock(BaseRecord.class);
    Mockito.when(template1.getId()).thenReturn(1L);
    BaseRecord template2 = Mockito.mock(BaseRecord.class);
    Mockito.when(template2.getId()).thenReturn(2L);
    List<BaseRecord> templates = List.of(template1, template2);
    Mockito.when(recordGroupSharingDao.getTemplatesSharedByUser(originalOwner))
        .thenReturn(templates);

    RecordGroupSharing sharing1 = Mockito.mock(RecordGroupSharing.class);
    Mockito.when(sharing1.getId()).thenReturn(11L);
    RecordGroupSharing sharing2 = Mockito.mock(RecordGroupSharing.class);
    Mockito.when(sharing2.getId()).thenReturn(22L);
    Mockito.when(recordGroupSharingDao.getRecordGroupSharingsForRecord(1L))
        .thenReturn(List.of(sharing1));
    Mockito.when(recordGroupSharingDao.getRecordGroupSharingsForRecord(2L))
        .thenReturn(List.of(sharing2));

    Folder templateRoot = new Folder();
    templateRoot.setId(100L);
    templateRoot.setName("Templates");
    Folder deletedUsersTemplates = new Folder();
    deletedUsersTemplates.setId(200L);
    deletedUsersTemplates.setName(TemplateTransferService.DELETED_USER_TEMPLATES_FOLDER);
    Folder deletedUserFolder = new Folder();
    deletedUserFolder.setId(300L);
    deletedUserFolder.setName(originalOwner.getUsername());

    Mockito.when(folderManager.getTemplateFolderForUser(newOwner)).thenReturn(templateRoot);
    Mockito.when(folderManager.getSubFolders(templateRoot))
        .thenReturn(List.of(deletedUsersTemplates));
    Mockito.when(folderManager.getSubFolders(deletedUsersTemplates))
        .thenReturn(List.of(deletedUserFolder));

    templateTransferService.transferOwnership(originalOwner, newOwner);

    Mockito.verify(recordGroupSharingDao).getTemplatesSharedByUser(originalOwner);
    Mockito.verify(recordGroupSharingDao).getRecordGroupSharingsForRecord(1L);
    Mockito.verify(recordGroupSharingDao).getRecordGroupSharingsForRecord(2L);
    Mockito.verify(recordSharingHandler).unshare(11L, originalOwner);
    Mockito.verify(recordSharingHandler).unshare(22L, originalOwner);

    List<Long> expectedTemplateIds = List.of(1L, 2L);
    InOrder inOrder = Mockito.inOrder(recordManager);
    inOrder
        .verify(recordManager)
        .moveUsersRecordsToFolder(expectedTemplateIds, originalOwner, deletedUserFolder);
    inOrder
        .verify(recordManager)
        .transferTemplates(
            originalOwner,
            newOwner,
            expectedTemplateIds,
            deletedUserFolder,
            originalOwner.getUsername() + TemplateTransferService.DELETED_USER_NAME_SUFFIX);

    Mockito.verify(folderManager, Mockito.never())
        .createNewFolder(Mockito.anyLong(), Mockito.anyString(), Mockito.any(User.class));

    ArgumentCaptor<GenericEvent> auditEventCaptor = ArgumentCaptor.forClass(GenericEvent.class);
    Mockito.verify(auditTrailService, Mockito.times(2)).notify(auditEventCaptor.capture());
    assertAuditEvent(auditEventCaptor.getAllValues().get(0), newOwner, template1, originalOwner);
    assertAuditEvent(auditEventCaptor.getAllValues().get(1), newOwner, template2, originalOwner);

    Mockito.verifyNoMoreInteractions(auditTrailService);
  }

  @Test
  void transferOwnershipCreatesDeletedUsersFoldersWhenMissing() {
    templateTransferService =
        new TemplateTransferService(
            auditTrailService,
            recordGroupSharingDao,
            folderManager,
            recordManager,
            baseRecordManager,
            recordSharingHandler);

    User originalOwner = new User();
    originalOwner.setUsername("original");
    User newOwner = new User();
    newOwner.setUsername("new");

    BaseRecord template1 = Mockito.mock(BaseRecord.class);
    Mockito.when(template1.getId()).thenReturn(1L);
    Mockito.when(recordGroupSharingDao.getTemplatesSharedByUser(originalOwner))
        .thenReturn(List.of(template1));
    Mockito.when(recordGroupSharingDao.getRecordGroupSharingsForRecord(1L))
        .thenReturn(Collections.emptyList());

    Folder templateRoot = new Folder();
    templateRoot.setId(100L);
    templateRoot.setName("Templates");
    Mockito.when(folderManager.getTemplateFolderForUser(newOwner)).thenReturn(templateRoot);
    Mockito.when(folderManager.getSubFolders(templateRoot)).thenReturn(Collections.emptyList());

    Folder deletedUsersTemplates = new Folder();
    deletedUsersTemplates.setId(200L);
    deletedUsersTemplates.setName(TemplateTransferService.DELETED_USER_TEMPLATES_FOLDER);
    Mockito.when(
            folderManager.createNewFolder(
                Mockito.eq(templateRoot.getId().longValue()),
                Mockito.eq(TemplateTransferService.DELETED_USER_TEMPLATES_FOLDER),
                Mockito.eq(newOwner)))
        .thenReturn(deletedUsersTemplates);
    Mockito.when(folderManager.getSubFolders(deletedUsersTemplates))
        .thenReturn(Collections.emptyList());

    Folder deletedUserFolder = new Folder();
    deletedUserFolder.setId(300L);
    deletedUserFolder.setName(originalOwner.getUsername());
    Mockito.when(
            folderManager.createNewFolder(
                Mockito.eq(deletedUsersTemplates.getId().longValue()),
                Mockito.eq(originalOwner.getUsername()),
                Mockito.eq(newOwner)))
        .thenReturn(deletedUserFolder);

    templateTransferService.transferOwnership(originalOwner, newOwner);

    Mockito.verify(folderManager)
        .createNewFolder(
            templateRoot.getId().longValue(),
            TemplateTransferService.DELETED_USER_TEMPLATES_FOLDER,
            newOwner);
    Mockito.verify(folderManager)
        .createNewFolder(
            deletedUsersTemplates.getId().longValue(), originalOwner.getUsername(), newOwner);

    Mockito.verify(recordManager)
        .moveUsersRecordsToFolder(List.of(1L), originalOwner, deletedUserFolder);
    Mockito.verify(recordManager)
        .transferTemplates(
            originalOwner,
            newOwner,
            List.of(1L),
            deletedUserFolder,
            originalOwner.getUsername() + TemplateTransferService.DELETED_USER_NAME_SUFFIX);
  }

  private void assertAuditEvent(
      GenericEvent auditEvent,
      User expectedNewOwner,
      BaseRecord expectedTemplate,
      User originalOwner) {
    String expectedDescription =
        String.format(
            "Ownership of template transferred from %s to %s",
            originalOwner.getUsername(), expectedNewOwner.getUsername());
    assertEquals(expectedNewOwner, auditEvent.getSubject());
    assertEquals(expectedTemplate, auditEvent.getAuditedObject());
    assertEquals(AuditAction.TRANSFER, auditEvent.getAuditAction());
    assertEquals(expectedDescription, auditEvent.getDescription());
  }
}
