package com.researchspace.testsandbox;

import static org.junit.Assert.assertEquals;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ImageProcessor;
import java.util.regex.Pattern;
import org.junit.Test;

public class ImagejTest {

  @Test
  public void test() {
    ImagePlus imp = IJ.openImage("src/test/resources/TestResources/IS1.jpg");
    String info = imp.getFileInfo().info;
    System.err.println(info);
  }

  @Test
  public void test2() {
    ImagePlus imp = IJ.openImage("src/test/resources/TestResources/Picture1.tiff");
    ImageProcessor pcssr = imp.getProcessor().resize(10, 20);
    imp = new ImagePlus("Capture3-1.tif", pcssr);
    new FileSaver(imp).saveAsJpeg("out.jpg");
  }

  @Test
  public void test3() {
    String s1 = "hello\nworld";
    String s2 = "hello\rworld";
    String s3 = "hello\r\nworld";

    Pattern p = Pattern.compile("\r\n|\n|\r");
    assertEquals("hello<br/>world", s1.replaceAll(p.pattern(), "<br/>"));
    assertEquals("hello<br/>world", s2.replaceAll(p.pattern(), "<br/>"));
    assertEquals("hello<br/>world", s3.replaceAll(p.pattern(), "<br/>"));
  }
}
