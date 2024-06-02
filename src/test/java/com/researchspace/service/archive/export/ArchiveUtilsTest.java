package com.researchspace.service.archive.export;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchiveUtils;
import com.researchspace.model.core.IRSpaceDoc;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ArchiveUtilsTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

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
