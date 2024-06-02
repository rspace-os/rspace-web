package com.axiope.dao.hibernate.audit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AuditableDeltasFilterTest {

  ObjectAuditFilter filter = new AuditableDeltasFilter();

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testUnattachedFieldFilteredOut() {
    // creating a document is noteworthy and should be audited
    StructuredDocument sd = TestFactory.createAnySD();
    assertTrue(filter.filter(sd));
    // clear updates
    sd.clearDelta();
    // should now fail
    assertFalse(filter.filter(sd));

    // renaming is noteworthy
    sd.setName("name");
    assertTrue(filter.filter(sd));
    sd.clearDelta();
    assertFalse(filter.filter(sd));

    // so is updating a field's data
    sd.getFields().get(0).setFieldData("abc");
    assertTrue(filter.filter(sd));
    sd.clearDelta();
    assertFalse(filter.filter(sd));

    // but setting a temp record isn't;
    sd.setTempRecord(sd.copy());
    assertFalse(filter.filter(sd));
  }
}
