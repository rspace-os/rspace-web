package com.researchspace.dao.spring.ext;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.internal.DefaultMergeEventListener;
import org.hibernate.event.spi.MergeContext;
import org.hibernate.event.spi.MergeEvent;
import org.hibernate.persister.entity.EntityPersister;

/**
 * Copied from Spring hibernate 3 support package to maintain this functionality. Transfers the
 * generated ID from the merged copy back to the original entity.
 *
 * <p>Updated for Hibernate 6: the second parameter of {@code entityIsTransient} changed from the
 * entity object to the entity identifier (id). Use {@code event.getEntity()} to get the actual
 * entity.
 */
@SuppressWarnings({"serial", "rawtypes"})
public class IdTransferringMergeEventListener extends DefaultMergeEventListener {

  /** Hibernate 6 compatible implementation of ID transferral. */
  @Override
  protected void entityIsTransient(MergeEvent event, Object id, MergeContext copyCache) {
    super.entityIsTransient(event, id, copyCache);
    Object entity = event.getEntity();
    if (entity == null || event.getResult() == null || event.getOriginal() == null) {
      return;
    }
    SharedSessionContractImplementor session = event.getSession();
    EntityPersister persister = session.getEntityPersister(event.getEntityName(), entity);
    // Extract id from merged copy (which is currently registered with Session).
    // see https://developer.jboss.org/wiki/HibernateCoreMigrationGuide40
    Object generatedId = persister.getIdentifier(event.getResult(), session);
    // Set id on original object (which remains detached).
    persister.setIdentifier(event.getOriginal(), generatedId, session);
  }
}
