package com.researchspace.api.v1.config;

import com.researchspace.api.v1.throttling.APIRequestThrottler;
import com.researchspace.testutils.TestRunnerController;
import com.researchspace.testutils.WithSpringContext;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = ProdAPIConfig.class)
@WithSpringContext
public abstract class APIProdConfigTestBase {

  @Autowired APIRequestThrottler userThrottler;
  @Autowired APIRequestThrottler globalThrottler;

  @BeforeAll
  public static void skipFastRuns() throws Exception {
    TestRunnerController.ignoreIfFastRun();
  }

  long globalMinInteval() {
    return globalThrottler.getMinIntervalMillis();
  }

  long userMinInterval() {
    return userThrottler.getMinIntervalMillis();
  }
}
