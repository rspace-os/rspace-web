package com.researchspace.api.v1.config;

import com.researchspace.api.v1.throttling.APIRequestThrottler;
import com.researchspace.testutils.TestRunnerController;
import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration(classes = ProdAPIConfig.class)
public abstract class APIProdConfigTestBase extends AbstractJUnit4SpringContextTests {

  @Autowired APIRequestThrottler userThrottler;
  @Autowired APIRequestThrottler globalThrottler;

  @BeforeClass
  public static void BeforeClass() throws Exception {
    TestRunnerController.ignoreIfFastRun();
  }

  long globalMinInteval() {
    return globalThrottler.getMinIntervalMillis();
  }

  long userMinInterval() {
    return userThrottler.getMinIntervalMillis();
  }
}
