package com.researchspace.service.archive.export;

import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.model.ArchivalCheckSum;
import java.io.File;
import lombok.Data;

/** Holds internal state of export action, accumulating data as export process continues */
@Data
class ExportContext {

  private String zipFileName;
  private String zipName;
  private long zipSize;
  private String archiveId;
  private File archiveAssmblyFlder;
  private ArchivalCheckSum csum;
  private NfsExportContext nfsContext;
  private ImmutableExportRecordList exportRecordList;
}
