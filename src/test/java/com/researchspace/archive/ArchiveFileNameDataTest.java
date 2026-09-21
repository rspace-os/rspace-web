package com.researchspace.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.record.BaseRecord;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ArchiveFileNameDataTest {

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
    assertFalse(fname.toFileName().contains("."), fname.toFileName()); // ellipses truncated
  }
}
