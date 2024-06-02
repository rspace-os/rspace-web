package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.researchspace.model.User;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;

public class GenericDaoTest extends BaseDaoTestCase {
  Logger log = LoggerFactory.getLogger(GenericDaoTest.class);
  GenericDao<User, Long> genericDao;
  @Autowired SessionFactory sessionFactory;

  @Before
  public void setUp() {
    genericDao = new GenericDaoHibernate<User, Long>(User.class, sessionFactory);
  }

  @Test
  public void getUser() {
    User user = genericDao.get(-1L);
    assertNotNull(user);
    assertEquals("user1a", user.getUsername());
  }

  @Test(expected = ObjectRetrievalFailureException.class)
  public void testGetThrowsExceptionIfObjectNotFound() {
    final Long UNKNOWNID = -1234556L;
    // returns null
    assertFalse(genericDao.getSafeNull(UNKNOWNID).isPresent());
    // throws exception
    User user = genericDao.get(-UNKNOWNID); //
  }
}
