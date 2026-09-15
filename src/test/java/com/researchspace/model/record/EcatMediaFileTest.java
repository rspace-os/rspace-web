package com.researchspace.model.record;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatVideo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EcatMediaFileTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
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
