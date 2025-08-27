package com.researchspace.admin.service;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;

public interface DevOpsManager {

  /**
   * Checks the record pointed to by globalId parameter, and if it has a known fixable problem, runs
   * the fix (or only describes a fix, if 'runFix' param is false)
   *
   * @param oid globalId of record to check
   * @param subject user attempting to run the fix (usually sysadmin)
   * @param runFix whether the fix should be applied, or just described
   * @return long message summarizing what was done, for displaying to the user who runs the fix
   */
  String fixRecord(GlobalIdentifier oid, User subject, boolean runFix);
}
