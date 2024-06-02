package com.axiope.service.cfg;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.service.RepositoryFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RepositoryFactoryTest extends SpringTransactionalTest {

  @Autowired private RepositoryFactory repoFactory;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testIsPrototype() {
    assertTrue(repoFactory.getRepository() != null);
    assertFalse(repoFactory.getRepository() == repoFactory.getRepository());
  }
}
