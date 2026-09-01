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

    /*
     * Newest revision first: it is the only one this reads. Envers gives no ordering guarantee of
     * its own, so it is asked for explicitly rather than inferred from the order rows happen to
     * come back in.
     */
    List<Object> revisions =
        ar.createQuery()
            .forRevisionsOfEntity(DigitalObjectIdentifier.class, false, false)
            .add(AuditEntity.id().eq(id))
            .addOrder(AuditEntity.revisionNumber().desc())
            .getResultList();
    if (revisions.isEmpty()) {
      return Optional.empty();
    }

    /*
     * The page is only ever open while the identifier is published *now*, whichever provider it
     * belongs to: a retracted identifier must take its page with it.
     */
    DigitalObjectIdentifier newestDoi = doiOf(revisions.get(0));
    if (!DigitalObjectIdentifier.isPublishedState(newestDoi.getState())) {
      return Optional.empty();
    }

    /*
     * Both providers serve the newest revision.
     *
     * Envers resolves the linked record as of the revision an identifier snapshot came from, so
     * returning the newest one shows the record as it stood at the last change to the IDENTIFIER
     * row. Identifier-row changes made while published - the customFieldsOnPublicPage toggle, a
     * refresh re-persisting "accepted" - therefore reach the page, which B2INST needs because it
     * has no republish operation to push them out with (RSDEV-1260).
     *
     * DataCite used to differ: it served the oldest revision of the trailing published run, holding
     * the page at the publication-time snapshot, picked out by walking back from the newest revision
     * until the first unpublished one. That distinction is gone deliberately - both providers now
     * serve the newest revision, so this needs neither the walk-back nor the provider test that
     * chose between them.
     *
     * A null type is covered by the same rule, which is right either way: identifiers persisted
     * before the type column was populated load with a null type and predate PIDINST, so they are
     * IGSN (the same default InventoryIdentifierApiManagerImpl.settingTypeFor applies).
     */
    return Optional.of(newestDoi);
  }

  /** The entity out of an Envers revision row, whose first element is the entity itself. */
  private DigitalObjectIdentifier doiOf(Object revisionRow) {
    return (DigitalObjectIdentifier) ((Object[]) revisionRow)[0];
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
