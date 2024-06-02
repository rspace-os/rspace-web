package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.SystemPropertyDao;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import java.util.List;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

@Repository("systemPropertyDao")
public class SystemPropertyDaoImpl extends GenericDaoHibernate<SystemPropertyValue, Long>
    implements SystemPropertyDao {

  public SystemPropertyDaoImpl() {
    super(SystemPropertyValue.class);
  }

  @Override
  public SystemPropertyValue findByPropertyName(String systemPropertyName) {
    SystemPropertyValue result =
        getSession()
            .createQuery(
                "from SystemPropertyValue spv where"
                    + " spv.property.descriptor.name=:systemPropertyName",
                SystemPropertyValue.class)
            .setParameter("systemPropertyName", systemPropertyName)
            .uniqueResult();
    return result;
  }

  @Override
  public SystemPropertyValue findByPropertyNameAndCommunity(
      String systemPropertyName, Long communityId) {
    Session session = getSession();
    SystemPropertyValue result = null;

    if (communityId != null) {
      result =
          getSession()
              .createQuery(
                  "from SystemPropertyValue spv where"
                      + " spv.property.descriptor.name=:systemPropertyName and"
                      + " spv.community.id=:communityId",
                  SystemPropertyValue.class)
              .setParameter("systemPropertyName", systemPropertyName)
              .setParameter("communityId", communityId)
              .uniqueResult();
    } else {
      result =
          session
              .createQuery(
                  "from SystemPropertyValue spv where"
                      + " spv.property.descriptor.name=:systemPropertyName and spv.community is"
                      + " null",
                  SystemPropertyValue.class)
              .setParameter("systemPropertyName", systemPropertyName)
              .uniqueResult();
    }
    return result;
  }

  @Override
  public SystemProperty findPropertyByPropertyName(String systemPropertyName) {
    SystemProperty result =
        getSession()
            .createQuery(
                "from SystemProperty spv where spv.descriptor.name=:systemPropertyName",
                SystemProperty.class)
            .setParameter("systemPropertyName", systemPropertyName)
            .uniqueResult();
    return result;
  }

  @Override
  public List<SystemProperty> listProperties() {
    return getSession()
        .createQuery("from SystemProperty order by descriptor.name asc", SystemProperty.class)
        .list();
  }

  @Override
  public List<SystemPropertyValue> getAllSysadminProperties() {
    return getSession()
        .createQuery(
            "from SystemPropertyValue spv where spv.community is null", SystemPropertyValue.class)
        .list();
  }

  @Override
  public List<SystemPropertyValue> getAllByCommunity(Long communityId) {
    return getSession()
        .createQuery(
            "from SystemPropertyValue spv where spv.community.id=:communityId",
            SystemPropertyValue.class)
        .setParameter("communityId", communityId)
        .list();
  }

  @Override
  public int deleteSystemPropertyValueByCommunityId(Long communityId) {
    return getSession()
        .createQuery("delete from SystemPropertyValue spv where spv.community.id=:communityId")
        .setParameter("communityId", communityId)
        .executeUpdate();
  }
}
