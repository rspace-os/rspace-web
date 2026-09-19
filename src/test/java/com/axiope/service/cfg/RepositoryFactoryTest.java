package com.axiope.service.cfg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.service.RepositoryFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RepositoryFactoryTest extends SpringTransactionalTest {

  @Autowired private RepositoryFactory repoFactory;

  @Test
  public void testIsPrototype() {
    assertTrue(repoFactory.getRepository() != null);
    assertFalse(repoFactory.getRepository() == repoFactory.getRepository());
  }
}
