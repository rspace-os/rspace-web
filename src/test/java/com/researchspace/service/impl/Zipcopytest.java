package com.researchspace.service.impl;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

@Slf4j
public class Zipcopytest {

  private static final String SRC_MAIN_RESOURCES_START_UP_DATA_C4_ZIP =
      "src/main/resources/StartUpData/chemical-data-sheet.zip";
  private static final String START_UP_DATA_C4_ZCIP = "/StartUpData/chemical-data-sheet.zip";

  // tests that maven filtering is not messing up zip file when copying from src to target
  // https://maven.apache.org/plugins/maven-resources-plugin/examples/binaries-filtering.html
  // https://stackoverflow.com/questions/50594360/gzipinputstream-works-with-fileinputstream-but-not-inputstream/50610474#50610474
  @Test
  public void classPathAndFileReadsAreTheSameBytes() throws IOException {
    File in = new File(SRC_MAIN_RESOURCES_START_UP_DATA_C4_ZIP);
    log.info(in.length() + "");
    File out2 = File.createTempFile("fileRead", ".zip");

    try (InputStream is3 = fromClasspath();
        FileOutputStream fos2 = new FileOutputStream(out2)) {
      IOUtils.copy(is3, fos2);
      log.info(out2.length() + "");
    }

    File out = File.createTempFile("fileRead", ".zip");

    try (InputStream is = new FileInputStream(in);
        FileOutputStream fos = new FileOutputStream(out)) {
      IOUtils.copy(is, fos);
      log.info(out.length() + "");
    }

    byte[] bytesFromFile = new byte[305_000];
    try (InputStream is2 = new FileInputStream(in)) {
      IOUtils.read(is2, bytesFromFile);
      log.info(bytesFromFile.length + "");
    }
    byte[] bytesFromClasspath = new byte[305_000];
    try (InputStream is2 = fromClasspath()) {
      IOUtils.read(is2, bytesFromClasspath);
    }

    assertTrue(Arrays.equals(bytesFromClasspath, bytesFromFile));
  }

  private InputStream fromClasspath() {
    return getClass().getResourceAsStream(START_UP_DATA_C4_ZCIP);
  }
}
