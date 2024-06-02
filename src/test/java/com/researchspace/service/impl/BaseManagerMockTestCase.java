package com.researchspace.service.impl;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A mock class for testing using JMock. This test class can be moved to the test tree. */
public abstract class BaseManagerMockTestCase {
  /** A logger */
  protected final Logger log = LoggerFactory.getLogger(getClass());

  /** The resourceBundle */
  protected ResourceBundle rb;

  /** Default constructor will set the ResourceBundle if needed. */
  public BaseManagerMockTestCase() {
    // Since a ResourceBundle is not required for each class, just
    // do a simple check to see if one exists
    String className = this.getClass().getName();

    try {
      rb = ResourceBundle.getBundle(className);
    } catch (MissingResourceException mre) {
      // log.debug("No resource bundle found for: " + className);
    }
  }
}
