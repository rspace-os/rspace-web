package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchivalLinkRecord;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.User;
import com.researchspace.service.RecordContext;

/**
 * Strategy interface for actual importing from an XML Archive. XML archives can contain records,
 * folders, users and messages, and we don't always want to process everything. <br>
 * This interface is also needed to break a Spring bean cycle, see RSPAC-1812 and should remain
 * functional interface so it can be called as a lambda.
 */
@FunctionalInterface
public interface ImportStrategy {

  /**
   * @param importer the User subject
   * @param archiveModel
   * @param iconfig
   * @param linkRecord
   * @param report
   * @param context
   * @param monitor
   */
  void doImport(
      User importer,
      IArchiveModel archiveModel,
      ArchivalImportConfig iconfig,
      ArchivalLinkRecord linkRecord,
      ImportArchiveReport report,
      RecordContext context,
      ProgressMonitor monitor);
}
