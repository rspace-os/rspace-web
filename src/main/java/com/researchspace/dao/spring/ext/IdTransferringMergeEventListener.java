package com.researchspace.dao.spring.ext;

import java.io.Serializable;
import java.util.Map;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.event.internal.DefaultMergeEventListener;
import org.hibernate.event.spi.MergeEvent;
import org.hibernate.persister.entity.EntityPersister;

/** Copied from Spring hibernate 3 support package to maintain this functionality */
@SuppressWarnings({"serial", "rawtypes"})
public class IdTransferringMergeEventListener extends DefaultMergeEventListener {

  /** Hibernate 3.1 implementation of ID transferral. */
  @Override
  protected void entityIsTransient(MergeEvent event, Map copyCache) {
    super.entityIsTransient(event, copyCache);
    SessionImplementor session = event.getSession();
    EntityPersister persister =
        session.getEntityPersister(event.getEntityName(), event.getEntity());
    // Extract id from merged copy (which is currently registered with Session).
    // seehttps://developer.jboss.org/wiki/HibernateCoreMigrationGuide40
    Serializable id = persister.getIdentifier(event.getResult(), session);
    // Set id on original object (which remains detached).
    persister.setIdentifier(event.getOriginal(), id, session);
  }
}
