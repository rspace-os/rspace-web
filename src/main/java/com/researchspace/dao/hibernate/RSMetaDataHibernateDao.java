package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.RSMetaDataDao;
import com.researchspace.model.ArchiveVersionToAppVersion;
import com.researchspace.model.RSMetaData;
import com.researchspace.model.Version;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository("rSMetaDataHibernateDao")
public class RSMetaDataHibernateDao extends GenericDaoHibernate<RSMetaData, Long>
    implements RSMetaDataDao {

  public RSMetaDataHibernateDao() {
    super(RSMetaData.class);
  }

  @Override
  public List<ArchiveVersionToAppVersion> getArchiveVersionsToAppVersion() {
    return getSession()
        .createQuery("from ArchiveVersionToAppVersion", ArchiveVersionToAppVersion.class)
        .list();
  }

  @Override
  public ArchiveVersionToAppVersion getAppVersionForArchiveVersion(
      Version archiveVersion, String name) {
    // Use schemaVersion.version (the embedded Long field) instead of comparing the embedded
    // object directly — Hibernate 6 doesn't support embedded-object equality in JPQL queries.
    return getSession()
        .createQuery(
            "from ArchiveVersionToAppVersion where schemaName=:name and"
                + " schemaVersion.version=:versionNum",
            ArchiveVersionToAppVersion.class)
        .setParameter("versionNum", archiveVersion.getVersion())
        .setParameter("name", name)
        .uniqueResult();
  }
}
