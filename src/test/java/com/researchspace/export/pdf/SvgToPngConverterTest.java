package com.researchspace.export.pdf;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestRunnerController;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import org.junit.BeforeClass;
import org.junit.Test;

public class SvgToPngConverterTest {

  public static final String SIMPLEST_SVG =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"50\" height=\"50\"><circle cx=\"25\""
          + " cy=\"25\" r=\"21\" fill=\"#fff\" stroke=\"#000\" stroke-width=\"1\"/></svg>";

  public static final int SIMPLEST_SVG_CONVERTED_LENGTH = 1495;

  @BeforeClass
  public static void BeforeClass() throws Exception {
    TestRunnerController.ignoreIfFastRun();
  }

  @Test
  public void convertSimplestSvg() throws Exception {
    File tempFile = File.createTempFile("svgconversiontest", ".png");
    OutputStream ostream = new FileOutputStream(tempFile);
    new SvgToPngConverter().convert(SIMPLEST_SVG, ostream);
    assertTrue(
        "unexpected .png file size after conversion from .svg",
        SIMPLEST_SVG_CONVERTED_LENGTH <= tempFile.length());
  }

  @Test
  public void convertSimpleMathjaxSvg() throws Exception {
    String svg = RSpaceTestUtils.loadTextResourceFromPdfDir("simpleEquation.svg");
    File tempFile = File.createTempFile("svgconversiontest", ".png");

    OutputStream ostream = new FileOutputStream(tempFile);
    new SvgToPngConverter().convert(svg, ostream);
    assertTrue("unexpected .png file size after conversion from .svg", 475 <= tempFile.length());
  }

  @Test
  public void convertLongMathjaxSvg() throws Exception {
    String svg = RSpaceTestUtils.loadTextResourceFromPdfDir("longEquation.svg");
    File tempFile = File.createTempFile("svglongconversiontest", ".png");

    OutputStream ostream = new FileOutputStream(tempFile);
    new SvgToPngConverter().convert(svg, ostream);
    assertTrue("unexpected .png file size after conversion from .svg", 7883 <= tempFile.length());
  }

  @Test
  public void testReplaceSvgObjectWithImg() throws Exception {

    String html = RSpaceTestUtils.loadTextResourceFromPdfDir("basicWithSvgObject.html");
    String expectedImg = "<img src=\"simpleEquation.png\" width=\"216\" height=\"42\" />\n  </div>";
    assertFalse(html.contains(expectedImg));

    html = new SvgToPngConverter().replaceSvgObjectWithImg(html);
    assertTrue("expected img tag, but was: " + html, html.contains(expectedImg));
  }
}
