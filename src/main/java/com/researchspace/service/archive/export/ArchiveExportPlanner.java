package com.researchspace.service.archive.export;

import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.record.Record;
import java.util.List;
import javax.transaction.Transactional;

/** Plans export, calculating what should be exported and performing permissions checks */
public interface ArchiveExportPlanner {

  @Transactional
  ExportRecordList createExportRecordList(
      IArchiveExportConfig expCfg, ExportSelection exportSelection);

  void checkGroupExportPermissions(User exporter, Group grp);

  void getGroupMembersRootFolderIds(
      Group grp, User exporter, List<Long> ids, List<String> types, List<String> names);

  List<AuditedRecord> getVersionsToExportForRecord(
      IArchiveExportConfig aconfig, GlobalIdentifier rid, Record record);

  @Transactional
  void updateExportListWithLinkedRecords(ExportRecordList exportList, IArchiveExportConfig aconfig);
}
