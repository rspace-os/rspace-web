package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryLinkDaoHibernate extends GenericDaoHibernate<InventoryLink, Long>
    implements InventoryLinkDao {

  public InventoryLinkDaoHibernate() {
    super(InventoryLink.class);
  }

  @Override
  public List<InventoryLink> findByTargetGlobalId(String targetGlobalId) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from InventoryLink where targetGlobalId=:gid and deleted=false", InventoryLink.class)
        .setParameter("gid", targetGlobalId)
        .list();
  }

  @Override
  public List<ExtraLinkField> findReferencingLinkFields(
      GlobalIdPrefix targetPrefix, Long targetDbId) {
    List<InventoryLink> links =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from InventoryLink where targetPrefix=:prefix and targetDbId=:dbId and"
                    + " deleted=false",
                InventoryLink.class)
            .setParameter("prefix", targetPrefix)
            .setParameter("dbId", targetDbId)
            .list();
    if (links.isEmpty()) {
      return Collections.emptyList();
    }
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from ExtraLinkField ef where ef.link in :links and ef.deleted = false",
            ExtraLinkField.class)
        .setParameterList("links", links)
        .list();
  }
}
