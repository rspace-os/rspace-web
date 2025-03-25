package com.researchspace.dao.hibernate;

import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import java.util.List;
import java.util.Optional;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.stereotype.Repository;

@Repository
public class DigitalObjectIdentifierDaoHibernate
    extends GenericDaoHibernate<DigitalObjectIdentifier, Long>
    implements DigitalObjectIdentifierDao {

  public DigitalObjectIdentifierDaoHibernate(Class<DigitalObjectIdentifier> persistentClass) {
    super(persistentClass);
  }

  public DigitalObjectIdentifierDaoHibernate() {
    super(DigitalObjectIdentifier.class);
  }

  private AuditReader getAuditReader() {
    return AuditReaderFactory.get(sessionFactory.getCurrentSession());
  }

  @Override
  public Optional<DigitalObjectIdentifier> getLastPublishedIdentifierByPublicLink(
      String publicLink) {

    Optional<DigitalObjectIdentifier> latestDoiOptional =
        getLatestIdentifierByPublicLink(publicLink);
    if (latestDoiOptional.isEmpty()) {
      return Optional.empty();
    }

    AuditReader ar = getAuditReader();
    Long id = latestDoiOptional.get().getId();

    AuditQuery q =
        ar.createQuery()
            .forRevisionsOfEntity(DigitalObjectIdentifier.class, false, false)
            .add(AuditEntity.id().eq(id));

    List<Object> genericResults = q.getResultList();
    if (genericResults.isEmpty()) {
      return Optional.empty();
    }

    // starting from most recent revisions, find oldest doi in "findable" state
    DigitalObjectIdentifier lastPublishedDoi = null;
    for (int i = genericResults.size() - 1; i >= 0; i--) {
      Object[] row = (Object[]) genericResults.get(i);
      DigitalObjectIdentifier doi = (DigitalObjectIdentifier) row[0];
      if ("findable".equals(doi.getState())) {
        lastPublishedDoi = doi;
      } else {
        break; // we've hit doi that's not published, stop here
      }
    }
    return lastPublishedDoi == null ? Optional.empty() : Optional.of(lastPublishedDoi);
  }

  @Override
  public List<DigitalObjectIdentifier> getActiveIdentifiersByOwner(User owner) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from DigitalObjectIdentifier where owner.id=:ownerId and deleted = false",
            DigitalObjectIdentifier.class)
        .setParameter("ownerId", owner.getId())
        .getResultList();
  }

  private Optional<DigitalObjectIdentifier> getLatestIdentifierByPublicLink(String publicLink) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from DigitalObjectIdentifier where publicLink=:publicLink ",
            DigitalObjectIdentifier.class)
        .setParameter("publicLink", publicLink)
        .getResultList()
        .stream()
        .findFirst();
  }
}
