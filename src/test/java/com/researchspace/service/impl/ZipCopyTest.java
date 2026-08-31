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

  // Guards against Maven resource filtering corrupting the binary fixture.
  @Test
  public void classPathAndFileReadsAreTheSameBytes() throws IOException {
    // Fixed buffers can compare only a prefix or include equal zero padding.
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
