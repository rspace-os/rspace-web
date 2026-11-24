package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
    return extraLinkFieldsHolding(liveLinksTargeting(targetPrefix, targetDbId));
  }

  @Override
  public List<ExtraLinkField> findLinkFieldsTargetingFilestorePaths(
      Long fileSystemId, Collection<String> absolutePaths) {
    return extraLinkFieldsHolding(liveLinksTargetingFilestorePaths(fileSystemId, absolutePaths));
  }

  private List<ExtraLinkField> extraLinkFieldsHolding(List<InventoryLink> links) {
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
    return structuredLinkFieldsHolding(liveLinksTargeting(targetPrefix, targetDbId));
  }

  @Override
  public List<InventoryLinkField> findStructuredLinkFieldsTargetingFilestorePaths(
      Long fileSystemId, Collection<String> absolutePaths) {
    return structuredLinkFieldsHolding(
        liveLinksTargetingFilestorePaths(fileSystemId, absolutePaths));
  }

  private List<InventoryLinkField> structuredLinkFieldsHolding(List<InventoryLink> links) {
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

  private List<InventoryLink> liveLinksTargetingFilestorePaths(
      Long fileSystemId, Collection<String> absolutePaths) {
    if (absolutePaths.isEmpty()) {
      return Collections.emptyList();
    }
    // the column's collation pads trailing spaces, which S3 keys keep: compare exactly here
    Set<String> exactPaths = Set.copyOf(absolutePaths);
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from InventoryLink where targetFileSystemId=:fsId and targetAbsolutePath in (:paths)"
                + " and deleted=false",
            InventoryLink.class)
        .setParameter("fsId", fileSystemId)
        .setParameterList("paths", absolutePaths)
        .list()
        .stream()
        .filter(link -> exactPaths.contains(link.getTargetAbsolutePath()))
        .toList();
  }
}
