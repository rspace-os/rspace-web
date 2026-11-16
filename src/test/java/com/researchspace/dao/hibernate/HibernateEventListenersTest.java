package com.researchspace.dao.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.axiope.dao.hibernate.audit.RSEnversPostDeleteListener;
import com.axiope.dao.hibernate.audit.RSEnversPostInsertListener;
import com.axiope.dao.hibernate.audit.RSEnversPostUpdateListener;
import com.researchspace.dao.BaseDaoTestCase;
import com.researchspace.dao.spring.ext.IdTransferringMergeEventListener;
import com.researchspace.model.User;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Checks the listeners that RSpace's Hibernate integrators register at startup. */
class HibernateEventListenersTest extends BaseDaoTestCase {

  private static final String ENVERS_INTEGRATOR =
      "com.axiope.dao.hibernate.audit.RSEnversIntegrator$";

  private EventListenerRegistry registry;

  @BeforeEach
  void setUpRegistry() {
    registry =
        sessionFactory
            .unwrap(SessionFactoryImplementor.class)
            .getServiceRegistry()
            .getService(EventListenerRegistry.class);
  }

  @Test
  void postLoadIncludesStructuredDocumentLoadListener() {
    assertThat(listenersFor(EventType.POST_LOAD))
        .hasAtLeastOneElementOfType(StructuredDocumentLoadListener.class);
  }

  @Test
  void mergeHasOnlyIdTransferringMergeEventListener() {
    List<?> merge = listenersFor(EventType.MERGE);
    assertThat(merge).hasSize(1);
    assertEquals(IdTransferringMergeEventListener.class, merge.get(0).getClass());
  }

  @Test
  void enversListenersAreRegistered() {
    assertThat(listenersFor(EventType.POST_INSERT))
        .hasAtLeastOneElementOfType(RSEnversPostInsertListener.class);
    assertThat(listenersFor(EventType.POST_UPDATE))
        .hasAtLeastOneElementOfType(RSEnversPostUpdateListener.class);
    assertThat(listenersFor(EventType.POST_DELETE))
        .hasAtLeastOneElementOfType(RSEnversPostDeleteListener.class);
    assertThat(classNames(listenersFor(EventType.POST_COLLECTION_RECREATE)))
        .contains(ENVERS_INTEGRATOR + "RSEnversPostCollectionRecreateEventListenerImpl");
    assertThat(classNames(listenersFor(EventType.PRE_COLLECTION_REMOVE)))
        .contains(ENVERS_INTEGRATOR + "RSEnversPreCollectionRemoveEventListenerImpl");
    assertThat(classNames(listenersFor(EventType.PRE_COLLECTION_UPDATE)))
        .contains(ENVERS_INTEGRATOR + "RSEnversPreCollectionUpdateListenerImpl");
  }

  @Test
  void mergeOfNewObjectCopiesIdBackToCallersObject() {
    User u = TestFactory.createAnyUser(getRandomAlphabeticString("merge"));
    User copy = sessionFactory.getCurrentSession().merge(u);
    assertNotNull(u.getId());
    assertEquals(copy.getId(), u.getId());
  }

  private <T> List<T> listenersFor(EventType<T> type) {
    List<T> result = new ArrayList<>();
    registry.getEventListenerGroup(type).listeners().forEach(result::add);
    return result;
  }

  private static List<String> classNames(List<?> listeners) {
    return listeners.stream().map(l -> l.getClass().getName()).toList();
  }
}
