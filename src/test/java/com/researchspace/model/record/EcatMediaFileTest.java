package com.researchspace.model.record;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatVideo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EcatMediaFileTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testBasicASsertionsAboutType() {
    assertTrue(new EcatVideo().isMediaRecord());
    assertTrue(new EcatAudio().isMediaRecord());
    assertTrue(new EcatDocumentFile().isMediaRecord());
    assertTrue(new EcatChemistryFile().isMediaRecord());

    assertTrue(new EcatVideo().isAV());
    assertTrue(new EcatAudio().isAV());
    assertTrue(new EcatChemistryFile().isChemistryFile());
    assertFalse(new EcatDocumentFile().isAV());
  }
}
