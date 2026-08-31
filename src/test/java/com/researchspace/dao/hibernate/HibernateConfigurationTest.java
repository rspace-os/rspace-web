package com.researchspace.dao.hibernate;

import static org.junit.Assert.assertTrue;

import com.researchspace.dao.BaseDaoTestCase;
import com.researchspace.model.booking.BookingDefaultAccessGrantee;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HibernateConfigurationTest extends BaseDaoTestCase {
  @Autowired SessionFactory sessionFactory;
  @Autowired EntityManagerFactory emf;

  @Test
  public void testColumnMapping() throws Exception {

    Session session = sessionFactory.getCurrentSession();
    try {
      Metamodel model = emf.getMetamodel();

      for (EntityType<?> o : model.getEntities()) {

        Class<?> className = o.getJavaType();
        String name = o.getName();
        // emf doesn't pick up hibernate-envers entities
        if (name.endsWith("AUD") || name.endsWith("DefaultRevisionEntity")) {
          continue;
        }
        try {
          log.info("Trying select * from: " + name);
          Query<?> q = session.createQuery("from " + name, className);
          q.list();
          log.debug("ok: " + name);
        } catch (Exception e) {
          log.warn("Failed for entity {} of class {}", name, className);
        }
      }
    } finally {
      //	session.close();
    }
  }

  @Test
  public void resourceAccessEntitiesAreMapped() {
    Metamodel model = emf.getMetamodel();

    assertTrue(
        model.getEntities().stream()
            .anyMatch(entity -> entity.getJavaType() == ResourceAccess.class));
    assertTrue(
        model.getEntities().stream()
            .anyMatch(entity -> entity.getJavaType() == ResourceRoleAssignment.class));
    assertTrue(
        model.getEntities().stream()
            .anyMatch(entity -> entity.getJavaType() == BookingDefaultAccessGrantee.class));
  }
}
