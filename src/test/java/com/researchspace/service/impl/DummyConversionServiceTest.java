package com.researchspace.service.impl;

import static org.junit.Assert.assertNotNull;

import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;

public class DummyConversionServiceTest extends SpringTransactionalTest {

  @Test
  public void test() {
    DummyConversionService converter = new DummyConversionService();
    converter.setPdfPath(RSpaceTestUtils.getResource("smartscotland3.pdf").getAbsolutePath());
    assertNotNull(converter.convert(null, "pdf"));

    converter.setImagePath(
        RSpaceTestUtils.getResource("GalleryThumbnail54x76.png").getAbsolutePath());
    assertNotNull(converter.convert(null, "png"));
  }
}
