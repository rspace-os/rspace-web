package com.researchspace.export.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestRunnerController;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SvgToPngConverterTest {

  public static final String SIMPLEST_SVG =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"50\" height=\"50\"><circle cx=\"25\""
          + " cy=\"25\" r=\"21\" fill=\"#fff\" stroke=\"#000\" stroke-width=\"1\"/></svg>";

  public static final int SIMPLEST_SVG_CONVERTED_LENGTH = 1495;

  @BeforeAll
  public static void skipFastRuns() throws Exception {
    TestRunnerController.ignoreIfFastRun();
  }

  @Test
  public void convertSimplestSvg() throws Exception {
    File tempFile = File.createTempFile("svgconversiontest", ".png");
    OutputStream ostream = new FileOutputStream(tempFile);
    new SvgToPngConverter().convert(SIMPLEST_SVG, ostream);
    assertThat(tempFile.length())
        .as("unexpected .png file size after conversion from .svg")
        .isGreaterThanOrEqualTo((long) SIMPLEST_SVG_CONVERTED_LENGTH);
  }

  @Test
  public void convertSimpleMathjaxSvg() throws Exception {
    String svg = RSpaceTestUtils.loadTextResourceFromPdfDir("simpleEquation.svg");
    File tempFile = File.createTempFile("svgconversiontest", ".png");

    OutputStream ostream = new FileOutputStream(tempFile);
    new SvgToPngConverter().convert(svg, ostream);
    assertThat(tempFile.length())
        .as("unexpected .png file size after conversion from .svg")
        .isGreaterThanOrEqualTo(475L);
  }

  @Test
  public void convertLongMathjaxSvg() throws Exception {
    String svg = RSpaceTestUtils.loadTextResourceFromPdfDir("longEquation.svg");
    File tempFile = File.createTempFile("svglongconversiontest", ".png");

    OutputStream ostream = new FileOutputStream(tempFile);
    new SvgToPngConverter().convert(svg, ostream);
    assertThat(tempFile.length())
        .as("unexpected .png file size after conversion from .svg")
        .isGreaterThanOrEqualTo(7883L);
  }

  @Test
  public void testReplaceSvgObjectWithImg() throws Exception {

    String html = RSpaceTestUtils.loadTextResourceFromPdfDir("basicWithSvgObject.html");
    String expectedImg = "<img src=\"simpleEquation.png\" width=\"216\" height=\"42\" />\n  </div>";
    assertThat(html).doesNotContain(expectedImg);

    html = new SvgToPngConverter().replaceSvgObjectWithImg(html);
    assertThat(html).as("expected img tag, but was: " + html).contains(expectedImg);
  }
}
