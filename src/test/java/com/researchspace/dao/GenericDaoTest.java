package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import com.researchspace.testutils.TestFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;

public class GenericDaoTest extends BaseDaoTestCase {
  Logger log = LoggerFactory.getLogger(GenericDaoTest.class);
  GenericDao<User, Long> genericDao;
  @Autowired SessionFactory sessionFactory;

  @BeforeEach
  public void setUp() {
    genericDao = new GenericDaoHibernate<User, Long>(User.class, sessionFactory);
  }

  @Test
  public void getUser() {
    User user = genericDao.get(-1L);
    assertNotNull(user);
    assertEquals("user1a", user.getUsername());
  }

  @Test
  public void testGetThrowsExceptionIfObjectNotFound() {
    final Long UNKNOWNID = -1234556L;
    assertFalse(genericDao.getSafeNull(UNKNOWNID).isPresent());
    assertThrows(ObjectRetrievalFailureException.class, () -> genericDao.get(-UNKNOWNID));
  }

  // save() now routes a transient entity through persist() rather than merge(). persist()
  // manages the supplied instance itself (assertSame guards this: a merge-only implementation
  // returns a managed copy), and the returned instance must carry the generated id.
  @Test
  public void saveTransientEntityReturnsInstanceWithGeneratedId() {
    User newUser = TestFactory.createAnyUser(getRandomAlphabeticString("gd"));
    User saved = genericDao.save(newUser);
    assertSame(newUser, saved);
    assertNotNull(saved.getId());
    assertEquals(newUser.getUsername(), genericDao.get(saved.getId()).getUsername());
  }

  // Detached-entity updates must keep merge semantics: saving a modified detached
  // copy applies the change without throwing. Flush and clear before the reload so the
  // assertion reads the database state, not the merge-managed instance still in the session.
  @Test
  public void saveDetachedEntityMergesChanges() {
    User detached = genericDao.get(-1L);
    sessionFactory.getCurrentSession().evict(detached);
    detached.setLastName("merged-" + getRandomAlphabeticString("x"));
    User saved = genericDao.save(detached);
    assertEquals(detached.getLastName(), saved.getLastName());
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();
    assertEquals(detached.getLastName(), genericDao.get(-1L).getLastName());
  }
}
