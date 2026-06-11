package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
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
  public List<ExtraLinkField> findReferencingLinkFields(
      GlobalIdPrefix targetPrefix, Long targetDbId) {
    List<InventoryLink> links = liveLinksTargeting(targetPrefix, targetDbId);
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

  @Override
  public List<InventoryLinkField> findReferencingStructuredLinkFields(
      GlobalIdPrefix targetPrefix, Long targetDbId) {
    List<InventoryLink> links = liveLinksTargeting(targetPrefix, targetDbId);
    if (links.isEmpty()) {
      return Collections.emptyList();
    }
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from InventoryLinkField f where f.link in :links and f.deleted = false",
            InventoryLinkField.class)
        .setParameterList("links", links)
        .list();
  }

  private List<InventoryLink> liveLinksTargeting(GlobalIdPrefix targetPrefix, Long targetDbId) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from InventoryLink where targetPrefix=:prefix and targetDbId=:dbId and"
                + " deleted=false",
            InventoryLink.class)
        .setParameter("prefix", targetPrefix)
        .setParameter("dbId", targetDbId)
        .list();
  }
}
