package com.researchspace.admin.service;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;

public interface DevOpsManager {

  String fixRecord(GlobalIdentifier oid, User subject, boolean runFix);
}
