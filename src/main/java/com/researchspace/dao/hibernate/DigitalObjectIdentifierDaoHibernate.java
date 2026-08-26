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

    /*
     * The page serves the identifier's most recent revision, and only while that revision is in a
     * published state. Envers resolves the linked record as of that revision, so the page still
     * shows the state from the moment of the last write that touched the identifier row (ordinary
     * record edits do not). Deliberately the newest revision rather than the first of the
     * published run: identifier-row changes made while published (the customFieldsOnPublicPage
     * toggle, a B2INST refresh re-persisting "accepted") must reach the page, and a B2INST
     * identifier has no republish operation to push them out with (RSDEV-1260).
     */
    Object[] newestRow = (Object[]) genericResults.get(genericResults.size() - 1);
    DigitalObjectIdentifier newestDoi = (DigitalObjectIdentifier) newestRow[0];
    return DigitalObjectIdentifier.isPublishedState(newestDoi.getState())
        ? Optional.of(newestDoi)
        : Optional.empty();
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
