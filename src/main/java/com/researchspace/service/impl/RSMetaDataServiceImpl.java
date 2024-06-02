package com.researchspace.service.impl;

import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.dao.RSMetaDataDao;
import com.researchspace.model.AppVersion;
import com.researchspace.model.ArchiveVersionToAppVersion;
import com.researchspace.model.RSMetaData;
import com.researchspace.model.Version;
import com.researchspace.service.RSMetaDataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("rsMetaDataManager")
public class RSMetaDataServiceImpl extends GenericManagerImpl<RSMetaData, Long>
    implements RSMetaDataManager {

  private RSMetaDataDao metadao;

  @Value("${rsversion}")
  private String appVersion;

  @Autowired
  public void setUserDao(RSMetaDataDao dao) {
    this.metadao = dao;
    this.dao = dao;
  }

  @Override
  public AppVersion getDatabaseVersion() {
    return new AppVersion(new SemanticVersion(appVersion));
  }

  public boolean isArchiveImportable(String schemaName, Version version) {
    AppVersion currentDB = getDatabaseVersion();
    ArchiveVersionToAppVersion range = metadao.getAppVersionForArchiveVersion(version, schemaName);
    if (range != null
        && (currentDB.isOlderThan(range.getToExclusive())
          || (appVersion != null && appVersion.startsWith("2."))) // FIXME quick fix for open-source
        && currentDB.isSameOrNewerThan(range.getFromInclusive())) {
      return true;
    } else {
      return false;
    }
  }
}
