package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LoggingInitializerTest {

  @TempDir public File folder;
  LoggingInitializer loggingInit;

  @BeforeEach
  public void setUp() throws Exception {
    loggingInit = new LoggingInitializer();
  }

  @Test
  public void testInit() {
    loggingInit.init();
    assertEquals(".", loggingInit.getLoggingDir());
    // ok set
    loggingInit.setLoggingDir(folder.getAbsolutePath());
    loggingInit.init();
    assertEquals(folder.getAbsolutePath(), loggingInit.getLoggingDir());
  }

  @Test
  public void testImpossibleFileHandled() throws IOException {
    File regularFile = new File(folder, "not-a-directory");
    assertTrue(regularFile.createNewFile());
    loggingInit.setLoggingDir(new File(regularFile, "child").getPath());
    loggingInit.init();
    assertEquals(".", loggingInit.getLoggingDir());
  }
}
