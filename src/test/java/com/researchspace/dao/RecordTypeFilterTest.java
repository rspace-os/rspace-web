package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.core.RecordType;
import com.researchspace.model.views.RecordTypeFilter;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

public class RecordTypeFilterTest {

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
    RecordTypeFilter rtf =
        new RecordTypeFilter(EnumSet.of(RecordType.MEDIA_FILE, RecordType.NORMAL), false);
    assertEquals(EnumSet.allOf(RecordType.class).size() - 2, rtf.getWantedTypes().size());
  }

  @Test
  public void testNoNullArgs() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new RecordTypeFilter(null, false);
        });
  }
}
