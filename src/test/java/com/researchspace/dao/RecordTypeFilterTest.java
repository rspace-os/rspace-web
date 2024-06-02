package com.researchspace.dao;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.core.RecordType;
import com.researchspace.model.views.RecordTypeFilter;
import java.util.EnumSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RecordTypeFilterTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testRecordTypeFilterIncludeAll() {
    RecordTypeFilter rtf = new RecordTypeFilter(EnumSet.allOf(RecordType.class), true);
    assertEquals(EnumSet.allOf(RecordType.class).size(), rtf.getWantedTypes().size());
  }

  @Test
  public void testRecordTypeFilterIncludeSeveral() {
    RecordTypeFilter rtf =
        new RecordTypeFilter(EnumSet.of(RecordType.MEDIA_FILE, RecordType.NORMAL), true);
    assertEquals(2, rtf.getWantedTypes().size());
  }

  @Test
  public void testHandlesEmptySet() {
    RecordTypeFilter rtf = new RecordTypeFilter(EnumSet.noneOf(RecordType.class), true);
    assertEquals(0, rtf.getWantedTypes().size());
  }

  @Test
  public void testHandlesExclude() {
    // exclude these 2
    RecordTypeFilter rtf =
        new RecordTypeFilter(EnumSet.of(RecordType.MEDIA_FILE, RecordType.NORMAL), false);
    assertEquals(EnumSet.allOf(RecordType.class).size() - 2, rtf.getWantedTypes().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoNullArgs() {
    // exclude these 2
    new RecordTypeFilter(null, false);
  }
}
