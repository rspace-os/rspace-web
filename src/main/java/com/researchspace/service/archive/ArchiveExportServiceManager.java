package com.researchspace.service.archive;

import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.ArchivalCheckSum;
import java.util.List;

/** Performs the mechanics of an export */
public interface ArchiveExportServiceManager {

  /**
   * Exports a set of use-selected folders or records to RSpace archive format. <br>
   * This will export all descendant records and folders of a chosen folder
   *
   * @param manifest
   * @param rcdList A permission-checked immutable list of items to export
   * @param expCfg
   * @return An {@link ArchiveResult} object
   * @throws Exception
   */
  ArchiveResult exportArchive(
      ArchiveManifest manifest, ImmutableExportRecordList rcdList, IArchiveExportConfig expCfg)
      throws Exception;

  /**
   * Defines type of export ( defined as constant in {@link ArchiveExportConfig}} supported by an
   * implementation.
   *
   * @param archiveType
   * @return
   */
  boolean isArchiveType(String archiveType);

  List<ArchivalCheckSum> getCurrentArchiveMetadatas();

  ArchivalCheckSum save(ArchivalCheckSum csum);
}
