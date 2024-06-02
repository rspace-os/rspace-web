package com.researchspace.linkedelements;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.IFieldLinkableElement;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FieldElementLinkPairsTest {
  private FieldElementLinkPairs<EcatImage> images = new FieldElementLinkPairs<>(EcatImage.class);
  private FieldElementLinkPairs<IFieldLinkableElement> all =
      new FieldElementLinkPairs<>(IFieldLinkableElement.class);

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupportsClass() {
    assertTrue(images.supportsClass(EcatImage.class));
    assertFalse(images.supportsClass(EcatMediaFile.class));
    assertFalse(images.supportsClass(IFieldLinkableElement.class));

    assertTrue(all.supportsClass(EcatImage.class));
    assertTrue(all.supportsClass(EcatMediaFile.class));
    assertTrue(all.supportsClass(IFieldLinkableElement.class));
  }
}
