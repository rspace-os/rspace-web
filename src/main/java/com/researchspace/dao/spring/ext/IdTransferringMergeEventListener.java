package com.researchspace.dao.spring.ext;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.internal.DefaultMergeEventListener;
import org.hibernate.event.spi.MergeContext;
import org.hibernate.event.spi.MergeEvent;
import org.hibernate.persister.entity.EntityPersister;

/** Copied from Spring hibernate 3 support package to maintain this functionality */
@SuppressWarnings({"serial", "rawtypes"})
public class IdTransferringMergeEventListener extends DefaultMergeEventListener {

  /** Hibernate 3.1 implementation of ID transferral. */
  @Override
  protected void entityIsTransient(MergeEvent event, Object entity, MergeContext copyCache) {
    super.entityIsTransient(event, entity, copyCache);
    SharedSessionContractImplementor session = event.getSession();
    EntityPersister persister = session.getEntityPersister(event.getEntityName(), entity);
    // Extract id from merged copy (which is currently registered with Session).
    // seehttps://developer.jboss.org/wiki/HibernateCoreMigrationGuide40
    Object id = persister.getIdentifier(event.getResult(), session);
    // Set id on original object (which remains detached).
    persister.setIdentifier(event.getOriginal(), id, session);
  }
}
