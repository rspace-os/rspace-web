package com.researchspace.service.impl;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
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
  public void testImpossibleFileHandledOnNix() {
    // only runs properly on nix
    assumeFalse(IS_OS_WINDOWS);
    //	 impossible file
    loggingInit.setLoggingDir("../../../../../../../../test");
    loggingInit.init();
    assertEquals(".", loggingInit.getLoggingDir());
  }
}
