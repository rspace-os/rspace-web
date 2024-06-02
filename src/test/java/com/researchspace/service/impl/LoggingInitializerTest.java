package com.researchspace.service.impl;

import static org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LoggingInitializerTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();
  LoggingInitializer loggingInit;

  @Before
  public void setUp() throws Exception {
    loggingInit = new LoggingInitializer();
  }

  @Test
  public void testInit() {
    loggingInit.init();
    assertEquals(".", loggingInit.getLoggingDir());
    // ok set
    loggingInit.setLoggingDir(folder.getRoot().getAbsolutePath());
    loggingInit.init();
    assertEquals(folder.getRoot().getAbsolutePath(), loggingInit.getLoggingDir());
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
