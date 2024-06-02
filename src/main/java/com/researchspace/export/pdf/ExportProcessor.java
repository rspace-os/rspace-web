package com.researchspace.export.pdf;

import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.core.IRSpaceDoc;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Processor for generating an export of RSpace document. */
public interface ExportProcessor {

  /**
   * Compose all generated files into one and saves to file Store.
   *
   * @param finalExportFile the File into which the concatenated files will be written
   * @param tmpExportedFiles A List of Files of individual exports to be concatenated
   * @param fileStore A {@link FileStore}
   * @param fp A {@link FileProperty} object set category: fileExtension, group: user: version:
   * @param config an {@link ExportToFileConfig}
   * @return the modified and persisted {@link FileProperty}
   * @throws IOException
   */
  FileProperty concatenateExportedFilesIntoOne(
      File finalExportFile,
      List<File> tmpExportedFiles,
      FileStore fileStore,
      FileProperty fp,
      ExportToFileConfig config)
      throws IOException;

  /**
   * Compose all generated files into <code>finalExportFile</code>. <br>
   * Does <em>not</em> put <code>finalExportFile</code> in the file store
   *
   * @param finalExportFile
   * @param tmpExportedFiles
   * @param config
   * @throws IOException
   */
  void concatenateExportedFilesIntoOne(
      File finalExportFile, List<File> tmpExportedFiles, ExportToFileConfig config)
      throws IOException;

  /**
   * Boolean test for whether this processor supports export to the given export format
   *
   * @param exportFormat
   * @return <code>true</code> if format is supported, <code>false</code> otherwise.
   */
  boolean supportsFormat(ExportFormat exportFormat);

  /**
   * Generates exported representation of an RSpace record in the supplied file
   *
   * @param tempExportFile a writable File to put the export
   * @param exportInput ExportProcesserInput representation of RSpace document
   * @param rspaceDocument an {@link IRSpaceDoc} to export
   * @param exportConfig A {@link ExportToFileConfig} of user-supplied configuration
   * @throws IOException
   */
  void makeExport(
      File tempExportFile,
      ExportProcesserInput exportInput,
      IRSpaceDoc rspaceDocument,
      ExportToFileConfig exportConfig)
      throws IOException;
}
