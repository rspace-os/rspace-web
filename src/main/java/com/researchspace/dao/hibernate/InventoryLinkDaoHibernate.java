package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.netfiles.LeadingSlashes;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
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
      Long fileSystemId,
      Collection<String> absolutePaths,
      User filestoreOwner,
      Set<LeadingSlashes> spellings) {
    return liveFieldsTargetingFilestorePaths(
        ExtraLinkField.class,
        ExtraLinkField::getLink,
        fileSystemId,
        absolutePaths,
        filestoreOwner,
        spellings);
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
      Long fileSystemId,
      Collection<String> absolutePaths,
      User filestoreOwner,
      Set<LeadingSlashes> spellings) {
    return liveFieldsTargetingFilestorePaths(
        InventoryLinkField.class,
        InventoryLinkField::getLink,
        fileSystemId,
        absolutePaths,
        filestoreOwner,
        spellings);
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

  private <T> List<T> liveFieldsTargetingFilestorePaths(
      Class<T> fieldType,
      Function<T, InventoryLink> linkOf,
      Long fileSystemId,
      Collection<String> absolutePaths,
      User filestoreOwner,
      Set<LeadingSlashes> spellings) {
    if (absolutePaths.isEmpty()) {
      return Collections.emptyList();
    }
    // the column's collation pads trailing spaces, which S3 keys keep: compare exactly here
    Set<String> exactPaths = Set.copyOf(absolutePaths);
    String hql =
        "select f from "
            + fieldType.getSimpleName()
            + " f join fetch f.link l where l.targetFileSystemId=:fsId"
            + " and l.targetAbsolutePath in (:paths) and l.deleted=false and f.deleted=false";
    if (filestoreOwner != null) {
      hql +=
          " and l.filestoreId in (select s.id from NfsFileStore s where s.user = :owner"
              + LeadingSlashes.hqlCondition(
                  "concat(coalesce(s.path, ''), l.targetRemotePath)", spellings)
              + ")";
    }
    var query =
        sessionFactory
            .getCurrentSession()
            .createQuery(hql, fieldType)
            .setParameter("fsId", fileSystemId)
            .setParameterList("paths", absolutePaths);
    if (filestoreOwner != null) {
      query.setParameter("owner", filestoreOwner);
    }
    return query.list().stream()
        .filter(field -> exactPaths.contains(linkOf.apply(field).getTargetAbsolutePath()))
        .toList();
  }
}
