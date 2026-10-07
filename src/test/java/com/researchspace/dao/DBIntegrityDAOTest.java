package com.researchspace.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DBIntegrityDAOTest extends SpringTransactionalTest {

  @Autowired private DBIntegrityDAO dao;

  @Test
  public void testGetOrphanedRecords() {
    assertThat(dao.getOrphanedRecords()).isEmpty();
  }

  @Test
  public void testGetTemporaryFavouriteDocs() {
    assertThat(dao.getTemporaryFavouriteDocs()).isEmpty();
  }
}
