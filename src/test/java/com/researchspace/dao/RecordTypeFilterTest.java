package com.researchspace.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.core.RecordType;
import com.researchspace.model.views.RecordTypeFilter;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

public class RecordTypeFilterTest {

  @Test
  public void testRecordTypeFilterIncludeAll() {
    RecordTypeFilter rtf = new RecordTypeFilter(EnumSet.allOf(RecordType.class), true);
    assertThat(rtf.getWantedTypes()).hasSize(EnumSet.allOf(RecordType.class).size());
  }

  @Test
  public void testRecordTypeFilterIncludeSeveral() {
    RecordTypeFilter rtf =
        new RecordTypeFilter(EnumSet.of(RecordType.MEDIA_FILE, RecordType.NORMAL), true);
    assertThat(rtf.getWantedTypes()).hasSize(2);
  }

  @Test
  public void testHandlesEmptySet() {
    RecordTypeFilter rtf = new RecordTypeFilter(EnumSet.noneOf(RecordType.class), true);
    assertThat(rtf.getWantedTypes()).isEmpty();
  }

  @Test
  public void testHandlesExclude() {
    RecordTypeFilter rtf =
        new RecordTypeFilter(EnumSet.of(RecordType.MEDIA_FILE, RecordType.NORMAL), false);
    assertThat(rtf.getWantedTypes()).hasSize(EnumSet.allOf(RecordType.class).size() - 2);
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
