package com.researchspace.service.archive;

import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Service method to export to PDF/Word format rather than archive. */
public interface PdfWordExportManager {

  EcatDocumentFile doExport(
      User ownerOfWork,
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User exporter)
      throws IOException;

  File doExportForSigning(
      User ownerOfWork,
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User exporter)
      throws IOException;

  List<Long> getExportIDsForTagRetrievalFromFilesAndFolders(
      List<Long> recordIds, ExportToFileConfig exportConfig, User user);
}
