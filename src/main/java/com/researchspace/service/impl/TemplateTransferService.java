package com.researchspace.service.impl;

import com.researchspace.dao.FolderDao;
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
import com.researchspace.service.TransferService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TemplateTransferService implements TransferService {

  public static final String DELETED_USER_TEMPLATES_FOLDER = "Deleted Users";
  public static final String DELETED_USER_NAME_SUFFIX = " (Deleted)";

  private final AuditTrailService auditTrailService;
  private final RecordGroupSharingDao recordGroupSharingDao;
  private final FolderDao folderDao;
  private final FolderManager folderManager;
  private final RecordManager recordManager;
  private final SharingHandler recordSharingHandler;

  @Autowired
  public TemplateTransferService(
      AuditTrailService auditTrailService,
      RecordGroupSharingDao recordGroupSharingDao,
      FolderDao folderDao,
      FolderManager folderManager,
      RecordManager recordManager,
      SharingHandler recordSharingHandler) {
    this.auditTrailService = auditTrailService;
    this.recordGroupSharingDao = recordGroupSharingDao;
    this.folderDao = folderDao;
    this.folderManager = folderManager;
    this.recordManager = recordManager;
    this.recordSharingHandler = recordSharingHandler;
  }

  public void transferOwnership(User originalOwner, User newOwner) {
    List<BaseRecord> sharedRecords =
        recordManager.getTemplatesSharedByUserAndUsedByOtherUsers(originalOwner);

    if (!sharedRecords.isEmpty()) {

      for (BaseRecord template : sharedRecords) {
        List<RecordGroupSharing> sharings =
            recordGroupSharingDao.getRecordGroupSharingsForRecord(template.getId());
        for (RecordGroupSharing sharing : sharings) {
          recordSharingHandler.unshare(sharing.getId(), originalOwner);
        }
      }

      List<Long> templateIds =
          sharedRecords.stream().map(BaseRecord::getId).collect(Collectors.toList());

      Folder deletedUserFolder = determineDeletedTemplatesFolder(originalOwner, newOwner);

      recordManager.moveUsersRecordsToFolder(templateIds, originalOwner, deletedUserFolder);

      String deletedUserName = originalOwner.getUsername() + DELETED_USER_NAME_SUFFIX;
      recordManager.transferTemplates(originalOwner, newOwner, templateIds, deletedUserName);

      String description =
          String.format(
              "Ownership of template transferred from %s to %s",
              originalOwner.getUsername(), newOwner.getUsername());

      for (BaseRecord template : sharedRecords) {
        auditTrailService.notify(
            new GenericEvent(newOwner, template, AuditAction.TRANSFER, description));
      }

      transferGalleryItemsForTemplates(originalOwner, newOwner, templateIds);
    }
  }

  private void transferGalleryItemsForTemplates(
      User originalOwner, User newOwner, List<Long> templateIds) {
    List<EcatMediaFile> galleryItems =
        recordManager.getGalleryItemsForTemplates(templateIds, originalOwner);
    if (galleryItems.isEmpty()) {
      return;
    }

    List<Long> mediaIds = galleryItems.stream().map(BaseRecord::getId).collect(Collectors.toList());
    Folder galleryRoot = folderManager.getGalleryRootFolderForUser(originalOwner);
    Folder newOwnerGalleryRoot = folderManager.getGalleryRootFolderForUser(newOwner);

    Map<List<String>, List<Long>> itemsByPath = new LinkedHashMap<>();
    for (EcatMediaFile item : galleryItems) {
      List<String> relPath = buildRelativePath(item.getId(), galleryRoot.getId());
      itemsByPath.computeIfAbsent(relPath, k -> new ArrayList<>()).add(item.getId());
    }

    for (Map.Entry<List<String>, List<Long>> entry : itemsByPath.entrySet()) {
      List<String> relPath = entry.getKey();
      Folder destinationFolder;
      if (relPath.isEmpty()) {
        Folder deletedUsersFolder =
            getOrCreateSubfolder(newOwnerGalleryRoot, DELETED_USER_TEMPLATES_FOLDER, newOwner);
        destinationFolder =
            getOrCreateSubfolder(deletedUsersFolder, originalOwner.getUsername(), newOwner);
      } else {
        String category = relPath.get(0);
        Folder categoryFolder = recordManager.getGalleryMediaFolderForUser(category, newOwner);
        Folder deletedUsersFolder =
            getOrCreateSubfolder(categoryFolder, DELETED_USER_TEMPLATES_FOLDER, newOwner);
        Folder userFolder =
            getOrCreateSubfolder(deletedUsersFolder, originalOwner.getUsername(), newOwner);
        destinationFolder =
            getOrCreateFolderPath(userFolder, relPath.subList(1, relPath.size()), newOwner);
      }
      recordManager.moveUsersRecordsToFolder(entry.getValue(), originalOwner, destinationFolder);
    }

    String deletedUserName = originalOwner.getUsername() + DELETED_USER_NAME_SUFFIX;
    recordManager.transferTemplates(originalOwner, newOwner, mediaIds, deletedUserName);
    recordManager.updateFilePropertyOwnerForMediaFiles(mediaIds, newOwner.getUsername());
  }

  private List<String> buildRelativePath(Long recordId, Long galleryRootId) {
    Folder parent = folderDao.getParentFolder(recordId);
    if (parent == null || parent.getId().equals(galleryRootId)) {
      return new ArrayList<>();
    }
    List<String> path = buildRelativePath(parent.getId(), galleryRootId);
    path.add(parent.getName());
    return path;
  }

  private Folder getOrCreateSubfolder(Folder parent, String name, User owner) {
    List<Folder> subfolders = folderManager.getSubFolders(parent);
    for (Folder subfolder : subfolders) {
      if (name.equals(subfolder.getName())) {
        return subfolder;
      }
    }
    return folderManager.createNewFolder(parent.getId(), name, owner);
  }

  private Folder getOrCreateFolderPath(Folder base, List<String> pathComponents, User owner) {
    Folder current = base;
    for (String component : pathComponents) {
      current = getOrCreateSubfolder(current, component, owner);
    }
    return current;
  }

  private Folder determineDeletedTemplatesFolder(User originalOwner, User newOwner) {
    Folder templateFolder = folderManager.getTemplateFolderForUser(newOwner);
    Folder deletedUsersTemplates =
        getOrCreateSubfolder(templateFolder, DELETED_USER_TEMPLATES_FOLDER, newOwner);
    return getOrCreateSubfolder(deletedUsersTemplates, originalOwner.getUsername(), newOwner);
  }
}
