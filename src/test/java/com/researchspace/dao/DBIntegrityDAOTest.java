package com.researchspace.dao;

import static org.junit.Assert.assertEquals;

import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DBIntegrityDAOTest extends SpringTransactionalTest {

  @Autowired private DBIntegrityDAO dao;

  @Test
  public void testGetOrphanedRecords() {
    assertEquals(0, dao.getOrphanedRecords().size());
  }

  @Test
  public void testGetTemporaryFavouriteDocs() {
    assertEquals(0, dao.getTemporaryFavouriteDocs().size());
  }
}
