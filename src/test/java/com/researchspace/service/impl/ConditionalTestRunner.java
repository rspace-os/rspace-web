package com.researchspace.service.impl;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Only runs a unit test if has been annotated with a RunIfSystemPropertyDefined annotation
 * specifying a system property that must be defined for the test to run. Extends
 * SpringJUnit4ClassRunner so can be used in Spring tests
 */
public class ConditionalTestRunner extends SpringJUnit4ClassRunner {
  public ConditionalTestRunner(Class<?> klass) throws InitializationError {
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
