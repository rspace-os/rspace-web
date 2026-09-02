package com.researchspace.dao.hibernate;

import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
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
     * Newest revision first, so both rules below can be read off one ordered list. Envers gives no
     * ordering guarantee of its own, so it is asked for explicitly rather than inferred from the
     * order rows happen to come back in.
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
     * B2INST serves the newest revision. Identifier-row changes made while published (the
     * customFieldsOnPublicPage toggle, a refresh re-persisting "accepted") must reach the page, and
     * a B2INST identifier has no republish operation to push them out with (RSDEV-1260).
     */
    // when it is "B2inst" Provider then
    if (IdentifierType.PIDINST_B2INST.equals(latestDoiOptional.get().getType())) {
      return Optional.of(newestDoi);
    }

    /*
     * DataCite keeps the publication-time snapshot: the oldest revision of the trailing published
     * run. Envers resolves the linked record as of that revision, so edits made after publishing
     * stay private until the user deliberately republishes. Walking back from the newest revision
     * and stopping at the first unpublished one is what picks that revision out; an order and a row
     * limit cannot, because the oldest revision overall is the draft the identifier started as.
     *
     * A null type lands here too, which is right: identifiers persisted before the type column was
     * populated load with a null type and predate PIDINST, so they are IGSN (the same default
     * InventoryIdentifierApiManagerImpl.settingTypeFor applies).
     */
    // when it is "DataCite" Provider then
    DigitalObjectIdentifier firstOfPublishedRun = newestDoi;
    for (int i = 1; i < revisions.size(); i++) {
      DigitalObjectIdentifier candidate = doiOf(revisions.get(i));
      if (!DigitalObjectIdentifier.isPublishedState(candidate.getState())) {
        break;
      }
      firstOfPublishedRun = candidate;
    }
    return Optional.of(firstOfPublishedRun);
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
