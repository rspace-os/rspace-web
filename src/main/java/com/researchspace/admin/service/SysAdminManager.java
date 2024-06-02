package com.researchspace.admin.service;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import java.io.IOException;
import java.util.List;

/** Services specific for sysadmin user */
public interface SysAdminManager {

  /** possible value of orderBy parameter in pagination criteria */
  String ORDER_BY_FILE_USAGE = "fileUsage()";

  /** possible value of orderBy parameter in pagination criteria */
  String ORDER_BY_RECORD_COUNT = "recordCount()";

  /**
   * Gets user info/fileUsage/ record count for users
   *
   * @param sysadmin A sysadmin user
   * @param pgCrit non-null
   * @return
   */
  ISearchResults<UserUsageInfo> getUserUsageInfo(User sysadmin, PaginationCriteria<User> pgCrit);

  List<String> getLastNLinesLogs(int maxLines) throws IOException;
}
