package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.SharingHandler;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class TemplateTransferServiceTest {

  @Mock MessageSource messageSource;

  @Mock AuditTrailService auditTrailService;

  @Mock RecordGroupSharingDao recordGroupSharingDao;

  @Mock RecordDao recordDao;

  @Mock FolderDao folderDao;

  @Mock FolderManager folderManager;

  @Mock RecordManager recordManager;

  @Mock SharingHandler recordSharingHandler;

  @InjectMocks TemplateTransferService templateTransferService;

  @BeforeEach
  void setUpMessageSourceStubs() {
    Mockito.lenient()
        .when(
            messageSource.getMessage(
                TemplateTransferService.DELETED_USER_TEMPLATES_FOLDER, null, null))
        .thenReturn("Deleted Users");
    Mockito.lenient()
        .when(
            messageSource.getMessage(TemplateTransferService.DELETED_USER_NAME_SUFFIX, null, null))
        .thenReturn(" (Deleted)");
    Mockito.lenient()
        .when(
            messageSource.getMessage(
                Mockito.eq("templates.transfer.audit.description"),
                Mockito.any(Object[].class),
                Mockito.isNull()))
        .thenAnswer(
            inv -> {
              Object[] args = inv.getArgument(1);
              return "Ownership of template transferred from " + args[0] + " to " + args[1];
            });
  }

  @Test
  void transferOwnershipDoesNothingWhenUserHasNoSharedTemplates() {
    User originalOwner = new User();
    originalOwner.setUsername("original");
    User newOwner = new User();
    newOwner.setUsername("new");

    Mockito.when(recordManager.getTemplatesOwnedByUserAndUsedByOtherUsers(originalOwner))
        .thenReturn(Collections.emptyList());

    templateTransferService.transferOwnership(originalOwner, newOwner);

    Mockito.verifyNoMoreInteractions(recordGroupSharingDao, recordManager);
    Mockito.verifyNoInteractions(folderManager, recordSharingHandler, auditTrailService);
  }

  @Test
  void transferOwnershipUnsharesMovesTransfersAndNotifiesAuditTrail_whenFoldersExist() {
    templateTransferService =
        new TemplateTransferService(
            messageSource,
            auditTrailService,
            recordGroupSharingDao,
            folderDao,
            folderManager,
            recordManager,
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
    Mockito.when(recordManager.getTemplatesOwnedByUserAndUsedByOtherUsers(originalOwner))
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
    deletedUsersTemplates.setName("Deleted Users");
    Folder deletedUserFolder = new Folder();
    deletedUserFolder.setId(300L);
    deletedUserFolder.setName(originalOwner.getUsername());

    Mockito.when(folderManager.getTemplateFolderForUser(newOwner)).thenReturn(templateRoot);
    Mockito.when(folderManager.getSubFolders(templateRoot))
        .thenReturn(List.of(deletedUsersTemplates));
    Mockito.when(folderManager.getSubFolders(deletedUsersTemplates))
        .thenReturn(List.of(deletedUserFolder));

    templateTransferService.transferOwnership(originalOwner, newOwner);

    Mockito.verify(recordManager).getTemplatesOwnedByUserAndUsedByOtherUsers(originalOwner);
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
            originalOwner.getUsername() + " (Deleted)");

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
            messageSource,
            auditTrailService,
            recordGroupSharingDao,
            folderDao,
            folderManager,
            recordManager,
            recordSharingHandler);

    User originalOwner = new User();
    originalOwner.setUsername("original");
    User newOwner = new User();
    newOwner.setUsername("new");

    BaseRecord template1 = Mockito.mock(BaseRecord.class);
    Mockito.when(template1.getId()).thenReturn(1L);
    Mockito.when(recordManager.getTemplatesOwnedByUserAndUsedByOtherUsers(originalOwner))
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
    deletedUsersTemplates.setName("Deleted Users");
    Mockito.when(
            folderManager.createNewFolder(
                Mockito.eq(templateRoot.getId().longValue()),
                Mockito.eq("Deleted Users"),
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
        .createNewFolder(templateRoot.getId().longValue(), "Deleted Users", newOwner);
    Mockito.verify(folderManager)
        .createNewFolder(
            deletedUsersTemplates.getId().longValue(), originalOwner.getUsername(), newOwner);

    Mockito.verify(recordManager)
        .moveUsersRecordsToFolder(List.of(1L), originalOwner, deletedUserFolder);
    Mockito.verify(recordManager)
        .transferTemplates(
            originalOwner, newOwner, List.of(1L), originalOwner.getUsername() + " (Deleted)");
  }

  @Test
  void transferOwnership_transfersGalleryItemsLinkedFromTemplates() {
    templateTransferService =
        new TemplateTransferService(
            messageSource,
            auditTrailService,
            recordGroupSharingDao,
            folderDao,
            folderManager,
            recordManager,
            recordSharingHandler);

    User originalOwner = new User();
    originalOwner.setUsername("original");
    User newOwner = new User();
    newOwner.setUsername("new");

    // one template with one gallery item in "Images" subfolder
    BaseRecord template1 = Mockito.mock(BaseRecord.class);
    Mockito.when(template1.getId()).thenReturn(1L);
    Mockito.when(recordManager.getTemplatesOwnedByUserAndUsedByOtherUsers(originalOwner))
        .thenReturn(List.of(template1));
    Mockito.when(recordGroupSharingDao.getRecordGroupSharingsForRecord(1L))
        .thenReturn(Collections.emptyList());

    // template folder setup (needed by determineDeletedTemplatesFolder)
    Folder templateRoot = new Folder();
    templateRoot.setId(100L);
    Mockito.when(folderManager.getTemplateFolderForUser(newOwner)).thenReturn(templateRoot);
    Mockito.when(folderManager.getSubFolders(templateRoot)).thenReturn(Collections.emptyList());
    Folder deletedUsersTemplates = new Folder();
    deletedUsersTemplates.setId(200L);
    Mockito.when(folderManager.createNewFolder(templateRoot.getId(), "Deleted Users", newOwner))
        .thenReturn(deletedUsersTemplates);
    Mockito.when(folderManager.getSubFolders(deletedUsersTemplates))
        .thenReturn(Collections.emptyList());
    Folder deletedUserTemplateFolder = new Folder();
    deletedUserTemplateFolder.setId(300L);
    Mockito.when(
            folderManager.createNewFolder(
                deletedUsersTemplates.getId(), originalOwner.getUsername(), newOwner))
        .thenReturn(deletedUserTemplateFolder);

    // gallery item linked from template
    EcatMediaFile image = Mockito.mock(EcatMediaFile.class);
    Mockito.when(image.getId()).thenReturn(10L);
    Mockito.when(recordManager.getGalleryItemsForTemplates(List.of(1L), originalOwner))
        .thenReturn(List.of(image));

    // original owner's gallery root (ID=50), item is in "Images" subfolder (ID=51)
    Folder originalGalleryRoot = new Folder();
    originalGalleryRoot.setId(50L);
    Folder imagesFolder = new Folder();
    imagesFolder.setId(51L);
    imagesFolder.setName("Images");
    Mockito.when(folderManager.getGalleryRootFolderForUser(originalOwner))
        .thenReturn(originalGalleryRoot);
    // item's parent is imagesFolder; imagesFolder's parent is gallery root → path = ["Images"]
    Mockito.when(folderDao.getParentFolders(10L)).thenReturn(List.of(imagesFolder));
    Mockito.when(folderDao.getParentFolders(51L)).thenReturn(List.of(originalGalleryRoot));

    // new owner's gallery root (ID=60); structure: Gallery/Images/Deleted Users/<username>/
    Folder newOwnerGalleryRoot = new Folder();
    newOwnerGalleryRoot.setId(60L);
    Mockito.when(folderManager.getGalleryRootFolderForUser(newOwner))
        .thenReturn(newOwnerGalleryRoot);
    Folder newOwnerImagesFolder = new Folder();
    newOwnerImagesFolder.setId(61L);
    Mockito.when(recordManager.getGalleryMediaFolderForUser("Images", newOwner))
        .thenReturn(newOwnerImagesFolder);
    Mockito.when(folderManager.getSubFolders(newOwnerImagesFolder))
        .thenReturn(Collections.emptyList());
    Folder deletedUsersGallery = new Folder();
    deletedUsersGallery.setId(62L);
    Mockito.when(
            folderManager.createNewFolder(newOwnerImagesFolder.getId(), "Deleted Users", newOwner))
        .thenReturn(deletedUsersGallery);
    Mockito.when(folderManager.getSubFolders(deletedUsersGallery))
        .thenReturn(Collections.emptyList());
    Folder userGalleryFolder = new Folder();
    userGalleryFolder.setId(63L);
    Mockito.when(
            folderManager.createNewFolder(
                deletedUsersGallery.getId(), originalOwner.getUsername(), newOwner))
        .thenReturn(userGalleryFolder);

    templateTransferService.transferOwnership(originalOwner, newOwner);

    // gallery item moved to Gallery/Images/Deleted Users/original (relPath[1:] is empty)
    Mockito.verify(recordManager)
        .moveUsersRecordsToFolder(List.of(10L), originalOwner, userGalleryFolder);
    // ownership transferred
    Mockito.verify(recordManager)
        .transferTemplates(
            originalOwner, newOwner, List.of(10L), originalOwner.getUsername() + " (Deleted)");
    // FileProperty owner updated
    Mockito.verify(recordManager)
        .updateFilePropertyOwnerForMediaFiles(List.of(10L), newOwner.getUsername());
  }

  @Test
  void transferOwnership_skipsGalleryTransferWhenNoItemsLinkedFromTemplates() {
    templateTransferService =
        new TemplateTransferService(
            messageSource,
            auditTrailService,
            recordGroupSharingDao,
            folderDao,
            folderManager,
            recordManager,
            recordSharingHandler);

    User originalOwner = new User();
    originalOwner.setUsername("original");
    User newOwner = new User();
    newOwner.setUsername("new");

    BaseRecord template1 = Mockito.mock(BaseRecord.class);
    Mockito.when(template1.getId()).thenReturn(1L);
    Mockito.when(recordManager.getTemplatesOwnedByUserAndUsedByOtherUsers(originalOwner))
        .thenReturn(List.of(template1));
    Mockito.when(recordGroupSharingDao.getRecordGroupSharingsForRecord(1L))
        .thenReturn(Collections.emptyList());

    Folder templateRoot = new Folder();
    templateRoot.setId(100L);
    Mockito.when(folderManager.getTemplateFolderForUser(newOwner)).thenReturn(templateRoot);
    Mockito.when(folderManager.getSubFolders(templateRoot)).thenReturn(Collections.emptyList());
    Folder deletedUsersTemplates = new Folder();
    deletedUsersTemplates.setId(200L);
    Mockito.when(folderManager.createNewFolder(templateRoot.getId(), "Deleted Users", newOwner))
        .thenReturn(deletedUsersTemplates);
    Mockito.when(folderManager.getSubFolders(deletedUsersTemplates))
        .thenReturn(Collections.emptyList());
    Folder deletedUserFolder = new Folder();
    deletedUserFolder.setId(300L);
    Mockito.when(
            folderManager.createNewFolder(
                deletedUsersTemplates.getId(), originalOwner.getUsername(), newOwner))
        .thenReturn(deletedUserFolder);

    // no gallery items linked from the template
    Mockito.when(recordManager.getGalleryItemsForTemplates(List.of(1L), originalOwner))
        .thenReturn(Collections.emptyList());

    templateTransferService.transferOwnership(originalOwner, newOwner);

    // no gallery-related folder or file operations should occur
    Mockito.verify(folderManager, Mockito.never()).getGalleryRootFolderForUser(Mockito.any());
    Mockito.verify(recordManager, Mockito.never())
        .updateFilePropertyOwnerForMediaFiles(Mockito.any(), Mockito.any());
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
