package com.axiope.dao.hibernate.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;

public class AuditableDeltasFilterTest {

  ObjectAuditFilter filter = new AuditableDeltasFilter();

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
