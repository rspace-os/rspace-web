package com.researchspace.archive;

import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.record.Record;
import java.io.File;
import java.util.List;
import java.util.Set;
import lombok.Data;

/** DTO class reporting what records were exported, and where to, and who requested it. */
@AuditTrailData(auditDomain = AuditDomain.RECORD)
@Data
public class ArchiveResult {

  private List<Record> archivedRecords;
  private List<ArchiveFolder> archivedFolders;
  private Set<ArchivalNfsFile> archivedNfsFiles;

  private File exportFile;
  private IArchiveExportConfig archiveConfig;
  private ArchivalCheckSum checksum;

  @AuditTrailProperty(
      properties = {"id", "name"},
      name = "exported")
  public List<Record> getArchivedRecords() {
    return archivedRecords;
  }

  @AuditTrailProperty(name = "exportPath", properties = "name")
  public File getExportFile() {
    return exportFile;
  }

  @AuditTrailProperty(
      name = "configuration",
      properties = {"archiveType", "exportScope"})
  public IArchiveExportConfig getArchiveConfig() {
    return archiveConfig;
  }
}
