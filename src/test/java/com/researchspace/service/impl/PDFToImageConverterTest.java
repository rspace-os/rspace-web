package com.researchspace.service.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.ConvertibleFile;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import org.junit.Before;
import org.junit.Test;

public class PDFToImageConverterTest {

  File pdf = RSpaceTestUtils.getAnyPdf();
  PDFToImageConverter converter = new PDFToImageConverter();
  Convertible convertible = null;

  @Before
  public void setup() {
    convertible = new ConvertibleFile(pdf);
  }

  @Test
  public void testSupportsConversion() {
    assertTrue(converter.supportsConversion(convertible, "png"));

    assertFalse(converter.supportsConversion(convertible, "doc"));
  }

  @Test
  public void testConvert() {
    assertTrue(converter.convert(convertible, "png").isSuccessful());
  }
}
