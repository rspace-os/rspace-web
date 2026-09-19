package com.researchspace.service.archive.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchiveUtils;
import com.researchspace.model.core.IRSpaceDoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ArchiveUtilsTest {

  @Test
  public void testGetGlobalId() {

    IRSpaceDoc doc = Mockito.mock(IRSpaceDoc.class);
    when(doc.getGlobalIdentifier()).thenReturn("SD1234");

    final String EXPECTED_URL = "http:/a.b.c.com/globalId/SD1234";
    assertEquals(
        EXPECTED_URL,
        ArchiveUtils.getAbsoluteGlobalLink(doc.getGlobalIdentifier(), "http:/a.b.c.com"));
    assertEquals(
        EXPECTED_URL,
        ArchiveUtils.getAbsoluteGlobalLink(doc.getGlobalIdentifier(), "http:/a.b.c.com/"));
  }
}
