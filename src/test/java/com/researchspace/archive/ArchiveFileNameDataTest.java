package com.researchspace.archive;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.record.BaseRecord;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ArchiveFileNameDataTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();

  @Mock IRSpaceDoc record;

  @Test
  public void testArchiveFileNameDataTruncatesTooLongName() {
    String too_long_name = RandomStringUtils.randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH);
    when(record.getName()).thenReturn(too_long_name);
    when(record.getId()).thenReturn(1L);
    when(record.isMediaRecord()).thenReturn(false);
    when(record.isStructuredDocument()).thenReturn(true);
    ArchiveFileNameData fname = new ArchiveFileNameData(record, null);
    assertEquals(53, fname.toFileName().length());
    assertThat(fname.toFileName(), not(containsString("."))); // ellipses truncated
  }
}
