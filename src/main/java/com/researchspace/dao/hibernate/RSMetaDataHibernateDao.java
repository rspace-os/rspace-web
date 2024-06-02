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
    return getSession()
        .createQuery(
            "from ArchiveVersionToAppVersion where schemaName=:name and schemaVersion=:version",
            ArchiveVersionToAppVersion.class)
        .setParameter("version", archiveVersion)
        .setParameter("name", name)
        .uniqueResult();
  }
}
