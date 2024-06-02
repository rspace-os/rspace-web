package com.researchspace.testutils;

import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class SimpleJUnitsOnly implements TestRule {

  @Override
  public Statement apply(Statement base, Description description) {
    if (description.getTestClass().isAssignableFrom(BaseManagerTestCaseBase.class)) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          Assume.assumeTrue(1 != 1);
        }
      };
    } else {
      return base;
    }
  }
}
