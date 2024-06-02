package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ArchiveDaoTest extends SpringTransactionalTest {

  @Autowired private ArchiveDao dao;

  @Before
  public void setUp() throws Exception {}

  @After
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

    assertEquals(1, dao.getUnexpiredArchives().size());
    csum.setDownloadTimeExpired(true);
    dao.save(csum);
    assertEquals(0, dao.getUnexpiredArchives().size());
  }
}
