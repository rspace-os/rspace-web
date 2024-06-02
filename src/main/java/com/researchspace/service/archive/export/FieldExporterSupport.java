package com.researchspace.service.archive.export;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.AuditManager;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.NfsFileHandler;
import com.researchspace.service.NfsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Container for Spring-injected services used by Field export classes */
@Component
class FieldExporterSupport {

  private RichTextUpdater richTextUpdater;
  private AuditManager auditManager;
  private IPermissionUtils permUtils;
  private ResourceToArchiveCopier copier;
  private RecordDao recordDao;
  private FolderDao folderDao;
  private IPropertyHolder properties;
  private NfsManager nfsManager;
  private NfsFileHandler nfsFileHandler;
  private FileStore fileStore;
  private DiskSpaceChecker diskSpaceChecker;

  FieldExporterSupport(
      @Autowired RichTextUpdater richTextUpdater,
      @Autowired AuditManager auditManager,
      @Autowired IPermissionUtils permUtils,
      @Autowired ResourceToArchiveCopier copier,
      @Autowired RecordDao recordDao,
      @Autowired FolderDao folderDao,
      @Autowired IPropertyHolder properties,
      @Autowired NfsManager nfsManager,
      @Autowired NfsFileHandler nfsFileHandler,
      @Autowired @Qualifier("compositeFileStore") FileStore fileStore,
      DiskSpaceChecker diskSpaceChecker) {

    this.richTextUpdater = richTextUpdater;
    this.auditManager = auditManager;
    this.permUtils = permUtils;
    this.copier = copier;
    this.recordDao = recordDao;
    this.folderDao = folderDao;
    this.properties = properties;
    this.nfsManager = nfsManager;
    this.nfsFileHandler = nfsFileHandler;
    this.fileStore = fileStore;
    this.diskSpaceChecker = diskSpaceChecker;
  }

  RichTextUpdater getRichTextUpdater() {
    return richTextUpdater;
  }

  AuditManager getAuditManager() {
    return auditManager;
  }

  ResourceToArchiveCopier getResourceCopier() {
    return copier;
  }

  public NfsManager getNfsManager() {
    return nfsManager;
  }

  public NfsFileHandler getNfsFileHandler() {
    return nfsFileHandler;
  }

  public FileStore getFileStore() {
    return fileStore;
  }

  public DiskSpaceChecker getDiskSpaceChecker() {
    return diskSpaceChecker;
  }

  <T> AuditedEntity<T> getObjectForRevision(Class<T> cls, Long primaryKey, Number revision) {
    return auditManager.getObjectForRevision(cls, primaryKey, revision);
  }

  boolean isRecord(Long recordId) {
    return recordDao.isRecord(recordId);
  }

  StructuredDocument getDocumentById(Long recordId) {
    return recordDao.get(recordId).asStrucDoc();
  }

  Folder getFolderById(Long folderId) {
    return folderDao.get(folderId);
  }

  String getServerUrl() {
    return properties.getServerUrl();
  }
}
