package com.axiope.dao.hibernate.audit;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.envers.boot.internal.EnversService;
import org.hibernate.envers.event.spi.EnversListenerDuplicationStrategy;
import org.hibernate.envers.event.spi.EnversPostCollectionRecreateEventListenerImpl;
import org.hibernate.envers.event.spi.EnversPreCollectionRemoveEventListenerImpl;
import org.hibernate.envers.event.spi.EnversPreCollectionUpdateEventListenerImpl;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

@Slf4j
public class RSEnversIntegrator implements Integrator {

  static class RSEnversPostCollectionRecreateEventListenerImpl
      extends EnversPostCollectionRecreateEventListenerImpl {

    private static final long serialVersionUID = 1L;

    protected RSEnversPostCollectionRecreateEventListenerImpl(EnversService enversConfiguration) {
      super(enversConfiguration);
    }
  }

  static class RSEnversPreCollectionRemoveEventListenerImpl
      extends EnversPreCollectionRemoveEventListenerImpl {

    private static final long serialVersionUID = 1L;

    protected RSEnversPreCollectionRemoveEventListenerImpl(EnversService enversConfiguration) {
      super(enversConfiguration);
    }
  }

  static class RSEnversPostCollectionRemoveEventListenerImpl
      extends EnversPostCollectionRecreateEventListenerImpl {

    private static final long serialVersionUID = 1L;

    protected RSEnversPostCollectionRemoveEventListenerImpl(EnversService enversConfiguration) {
      super(enversConfiguration);
    }
  }

  static class RSEnversPreCollectionUpdateListenerImpl
      extends EnversPreCollectionUpdateEventListenerImpl {

    private static final long serialVersionUID = 1L;

    protected RSEnversPreCollectionUpdateListenerImpl(EnversService enversConfiguration) {
      super(enversConfiguration);
    }
  }

  @Override
  public void disintegrate(
      SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
    log.info("Disintegrating event listener integrator, nothing to do here");
  }

  @Override
  public void integrate(
      Metadata configuration,
      SessionFactoryImplementor arg1,
      SessionFactoryServiceRegistry serviceRegistry) {
    EventListenerRegistry listenerRegistry =
        serviceRegistry.getService(EventListenerRegistry.class);
    listenerRegistry.addDuplicationStrategy(EnversListenerDuplicationStrategy.INSTANCE);

    final EnversService enversService = serviceRegistry.getService(EnversService.class);
    if (!enversService.isInitialized()) {
      throw new HibernateException(
          "Expecting EnversService to have been initialized prior to call to"
              + " EnversIntegrator#integrate");
    }

    if (enversService.getEntitiesConfigurations().hasAuditedEntities()) {
      listenerRegistry.appendListeners(
          EventType.POST_DELETE, new RSEnversPostDeleteListener(enversService));
      listenerRegistry.appendListeners(
          EventType.POST_INSERT, new RSEnversPostInsertListener(enversService));
      listenerRegistry.appendListeners(
          EventType.POST_UPDATE, new RSEnversPostUpdateListener(enversService));
      listenerRegistry.appendListeners(
          EventType.POST_COLLECTION_RECREATE,
          new RSEnversPostCollectionRecreateEventListenerImpl(enversService));
      listenerRegistry.appendListeners(
          EventType.PRE_COLLECTION_REMOVE,
          new RSEnversPreCollectionRemoveEventListenerImpl(enversService));
      listenerRegistry.appendListeners(
          EventType.PRE_COLLECTION_UPDATE,
          new RSEnversPreCollectionUpdateListenerImpl(enversService));
    }
  }
}
