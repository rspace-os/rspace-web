package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class ZipCopyTest {

  private static final Path SRC_MAIN_RESOURCES_START_UP_DATA_C4_ZIP =
      Path.of("src/main/resources/StartUpData/chemical-data-sheet.zip");
  private static final String START_UP_DATA_C4_ZCIP = "/StartUpData/chemical-data-sheet.zip";

  // tests that maven filtering is not messing up zip file when copying from src to target
  // https://maven.apache.org/plugins/maven-resources-plugin/examples/binaries-filtering.html
  // https://stackoverflow.com/questions/50594360/gzipinputstream-works-with-fileinputstream-but-not-inputstream/50610474#50610474
  @Test
  public void classPathAndFileReadsAreTheSameBytes() throws IOException {
    // readAllBytes rather than a fixed-size buffer: a buffer larger than the zip zero-pads both
    // sides equally and a buffer smaller than it compares only a prefix, so either way a
    // difference in the tail passes unnoticed once the file outgrows the number.
    try (InputStream fromClasspath = fromClasspath()) {
      assertArrayEquals(
          fromClasspath.readAllBytes(),
          Files.readAllBytes(SRC_MAIN_RESOURCES_START_UP_DATA_C4_ZIP));
    }
  }

  private InputStream fromClasspath() {
    return getClass().getResourceAsStream(START_UP_DATA_C4_ZCIP);
  }
}
