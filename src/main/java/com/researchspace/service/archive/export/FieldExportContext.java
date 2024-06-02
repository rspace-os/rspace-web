package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.IArchiveExportConfig;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

/**
 * Read-only Container for export configuration and values specific for an execution of an export
 */
@Value
@Setter(value = AccessLevel.PACKAGE)
@Getter(value = AccessLevel.PACKAGE)
@AllArgsConstructor
class FieldExportContext {

  IArchiveExportConfig config;
  ArchivalField archiveField;
  File recordFolder;
  File exportFolder;
  Number revision;
  NfsExportContext nfsContext;
  ImmutableExportRecordList exportRecordList;
  private Map<String, String> fieldExportedFileNames = new HashMap<>();

  // for testing
  FieldExportContext(
      File recordFolder,
      File exportFolder,
      IArchiveExportConfig cfg,
      ImmutableExportRecordList toExportList) {
    this.recordFolder = recordFolder;
    this.exportFolder = exportFolder;
    this.config = cfg;
    this.archiveField = null;
    this.revision = null;
    this.nfsContext = null;
    this.exportRecordList = toExportList;
  }
}
