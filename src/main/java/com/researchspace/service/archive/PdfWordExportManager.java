package com.researchspace.service.archive;

import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.User;
import com.researchspace.service.archive.export.ExportEcatDocumentResult;
import com.researchspace.service.archive.export.ExportFileResult;
import java.io.IOException;
import java.util.List;

/** Service method to export to PDF/Word format rather than archive. */
public interface PdfWordExportManager {

  ExportEcatDocumentResult doExport(
      User ownerOfWork,
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User exporter)
      throws IOException;

  ExportFileResult doExportForSigning(
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
