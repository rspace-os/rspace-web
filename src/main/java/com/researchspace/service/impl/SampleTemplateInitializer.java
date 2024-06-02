package com.researchspace.service.impl;

import com.researchspace.model.User;

/** Creates SampleTemplates if they don't already exist. */
public interface SampleTemplateInitializer {

  /**
   * Creates default SampleTemplates, if none already exist
   *
   * @param user
   * @return The number of templates created. Zero if none were created (because sample templates
   *     already exist)
   */
  int createSampleTemplates(User user);
}
