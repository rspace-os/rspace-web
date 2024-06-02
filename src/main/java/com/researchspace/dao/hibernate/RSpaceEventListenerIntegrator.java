package com.researchspace.dao.hibernate;

import com.researchspace.dao.spring.ext.IdTransferringMergeEventListener;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

@Slf4j
public class RSpaceEventListenerIntegrator implements Integrator {
  @Override
  public void disintegrate(SessionFactoryImplementor arg0, SessionFactoryServiceRegistry arg1) {
    log.info("Disintegrating RSpaceEventListenerIntegrator, nothing to do here");
  }

  @Override
  public void integrate(
      Metadata arg0,
      SessionFactoryImplementor arg1,
      SessionFactoryServiceRegistry serviceRegistry) {
    EventListenerRegistry listenerRegistry =
        serviceRegistry.getService(EventListenerRegistry.class);
    listenerRegistry.appendListeners(EventType.POST_LOAD, new StructuredDocumentLoadListener());
    // we have to set this, to ensure the default merge listener is not also included
    // else we get duplicated inserts
    listenerRegistry.setListeners(EventType.MERGE, new IdTransferringMergeEventListener());
  }
}
