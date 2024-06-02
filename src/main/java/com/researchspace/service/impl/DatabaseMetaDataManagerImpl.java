package com.researchspace.service.impl;

import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.service.DatabaseMetaDataManager;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("mysqlmetadata")
public class DatabaseMetaDataManagerImpl implements DatabaseMetaDataManager {
  Logger log = LoggerFactory.getLogger(DatabaseMetaDataManagerImpl.class);

  @Autowired private SessionFactory sessionFactory;

  @Override
  public SemanticVersion getVersion() {
    String versionStr = getVersionFromDB();
    if (SemanticVersion.VERSION.matcher(versionStr).matches()) {
      return new SemanticVersion(versionStr);
    } else {
      log.warn("MySQL version string could not be parsed into an AppVersion  - {}", versionStr);
      return SemanticVersion.UNKNOWN_VERSION;
    }
  }

  @Override
  public String getVersionMessage() {
    String versionStr = getVersionFromDB();
    return versionStr;
  }

  private String getVersionFromDB() {
    return sessionFactory
        .getCurrentSession()
        .createSQLQuery("select version()")
        .uniqueResult()
        .toString();
  }

  @Override
  public String getDescription() {
    return "MySQL version";
  }
}
