package com.researchspace.dao;

// import org.compass.core.CompassTemplate;
// import org.compass.gps.CompassGps;
import static org.junit.Assert.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

public class FieldDaoTest extends SpringTransactionalTest {

  @Autowired private FieldDao dao;

  @Test(expected = DataAccessException.class)
  public void testGetFieldInvalid() {
    // should throw DataAccessException
    dao.get(1000L);
  }

  @Test
  public void testgetFieldByContent() {
    User u = createAndSaveUserIfNotExists("xasas");
    initialiseContentWithEmptyContent(u);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(u, "text");

    assertEquals(1, dao.findByTextContent("tex").size());
    assertEquals(1, dao.findByTextContent("ext").size());
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }
}
