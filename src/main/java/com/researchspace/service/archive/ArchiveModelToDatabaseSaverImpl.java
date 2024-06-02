package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchivalLinkRecord;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.dao.InternalLinkDao;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.service.FieldManager;
import com.researchspace.service.ImportContext;
import com.researchspace.service.RecordContext;
import com.researchspace.service.UserManager;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Defines methods for saving a parsed archive to the database. */
public class ArchiveModelToDatabaseSaverImpl implements ArchiveModelToDatabaseSaver {

  @Autowired private UserManager userMgr;
  @Autowired private InternalLinkDao internalLinkDao;
  @Autowired private FieldManager fieldManager;

  @Autowired private RichTextUpdater richTextUpdater;

  private static final Logger log = LoggerFactory.getLogger(ArchiveModelToDatabaseSaverImpl.class);

  /**
   * Takes a parsed archive and saves to the database.
   *
   * @param iconfig
   * @param report
   * @param archiveModel
   */
  @Override
  public void saveArchiveToDB(
      ArchivalImportConfig iconfig,
      ImportArchiveReport report,
      IArchiveModel archiveModel,
      ProgressMonitor monitor,
      ImportStrategy importStrategy) {
    ArchivalLinkRecord linkRecord = new ArchivalLinkRecord();
    User importer = userMgr.getUserByUsername(iconfig.getUser());
    RecordContext context = new ImportContext(true);
    try {
      importStrategy.doImport(
          importer, archiveModel, iconfig, linkRecord, report, context, monitor);
    } catch (Exception e) {
      String msg = "Error attempting to insert content into database: " + e.getMessage();
      log.error(msg);
      report.getErrorList().addErrorMsg(msg);
      report.setComplete(true);
      return;
    }
    resetLinkedDocument(importer, linkRecord);
    report.setComplete(true);
  }

  private void resetLinkedDocument(User user, ArchivalLinkRecord linkRecord) {
    List<String> sourceFieldIds = linkRecord.getSourceFieldIds();
    for (String fldId : sourceFieldIds) {
      Field fdx = fieldManager.get(Long.parseLong(fldId), user).get();
      ArrayList<Long> linkedIds = new ArrayList<>();
      String updatedFieldData =
          richTextUpdater.updateLinkedDocument(
              fdx.getFieldData(), linkRecord.getOldDocIdToNewDocId(), linkedIds);
      fdx.setFieldData(updatedFieldData);
      fieldManager.save(fdx, user);
      for (Long targetId : linkedIds) {
        internalLinkDao.saveInternalLink(fdx.getStructuredDocument().getId(), targetId);
      }
    }
  }
}
