package com.researchspace.model.record;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FilterDeletedRecordsTest {

  RecordFilter filter;

  @BeforeEach
  public void setUp() throws Exception {
    filter = new FilterDeletedRecords();
  }

  @Test
  public void testFilter() {
    StructuredDocument sd = TestFactory.createAnySD(TestFactory.createAnyForm());
    assertTrue(filter.filter(sd));
    sd.setDeleted(true);
    assertFalse(filter.filter(sd));
  }
}
