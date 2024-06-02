package com.researchspace.service.impl;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * Only runs a unit test if has been annotated with a RunIfSystemPropertyDefined annotation
 * specifying a system property that must be defined for the test to run. <e
 */
public class ConditionalTestRunnerNotSpring extends BlockJUnit4ClassRunner {
  public ConditionalTestRunnerNotSpring(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  public void runChild(FrameworkMethod method, RunNotifier notifier) {
    RunIfSystemPropertyDefined condition = method.getAnnotation(RunIfSystemPropertyDefined.class);

    if (condition != null) {
      if (System.getProperty(condition.value()) != null) {
        super.runChild(method, notifier);
      } else {
        notifier.fireTestIgnored(describeChild(method));
      }

    } else {
      super.runChild(method, notifier);
    }
  }
}
