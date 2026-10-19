package com.researchspace.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ArchiveDaoTest extends SpringTransactionalTest {

  @Autowired private ArchiveDao dao;

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSave() {
    ArchivalCheckSum csum = TestFactory.createAnArchivalChecksum();
    dao.save(csum);

    ArchivalCheckSum csumLoaded = dao.get(csum.getUid());
    assertNotNull(csumLoaded);
  }

  @Test
  public void testGetNonExpiredQueries() {
    ArchivalCheckSum csum = TestFactory.createAnArchivalChecksum();
    dao.save(csum);

    assertThat(dao.getUnexpiredArchives()).hasSize(1);
    csum.setDownloadTimeExpired(true);
    dao.save(csum);
    assertThat(dao.getUnexpiredArchives()).isEmpty();
  }
}
