package com.researchspace.service.archive.export;

import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.core.GlobalIdentifier;
import org.springframework.beans.factory.annotation.Autowired;

/*
 * Helper class for implementations of ArchiveDataHandler
 */
abstract class AbstractDataHandler implements ArchiveDataHandler {

  @Autowired UserDao userDao;
  @Autowired GroupDao grpDao;

  Long getDBId(IArchiveExportConfig aconfig) {
    GlobalIdentifier oid = aconfig.getUserOrGroupId();
    Long id = oid.getDbId();
    return id;
  }
}
