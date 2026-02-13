package com.researchspace.service.archive.export;

import static com.researchspace.archive.ArchiveUtils.getUniqueName;

import com.researchspace.archive.ArchiveFileNameData;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.BaseRecordManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;

/** RSPAC-1408 Generate a formatted name with useful information about archive type in the name */
public class ArchiveNamingStrategy {

  private static final String NME_FORMAT_STRING = "RSpace-%s-%s-%s-";
  private static final String DATE_FORMAT = "yyyy-MM-dd-HH-mm";
  static final Pattern NAME_PATTERN = Pattern.compile("RSpace\\-[0-9-]{16}\\-\\S+\\-\\S+");

  private @Autowired GroupDao grpDao;
  private @Autowired UserDao userDao;
  private @Autowired BaseRecordManager baseRecordManager;

  String generateArchiveName(IArchiveExportConfig aconfig, ExportContext context) {

    String exportScope = "SELECTION";
    if (aconfig.isUserScope()) {
      exportScope = userDao.get(aconfig.getUserOrGroupId().getDbId()).getUsername();
    } else if (aconfig.isGroupScope()) {
      exportScope = grpDao.get(aconfig.getUserOrGroupId().getDbId()).getDisplayName();
    } else if (context.getExportRecordList().getRecordsToExportSize() == 1) {
      BaseRecord record =
          baseRecordManager.get(
              context.getExportRecordList().getFirstRecordToExport().getDbId(),
              aconfig.getExporter());
      exportScope = new ArchiveFileNameData(record, null).getName();
    }

    return generateArchiveName(aconfig.getArchiveType(), exportScope);
  }

  public String generateArchiveName(String archiveType, String exportScope) {
    final SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
    String archivePrefix =
        String.format(NME_FORMAT_STRING, sdf.format(new Date()), archiveType, exportScope);
    return getUniqueName(archivePrefix);
  }
}
