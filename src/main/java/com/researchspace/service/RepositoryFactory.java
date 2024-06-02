package com.researchspace.service;

import com.researchspace.repository.spi.IRepository;

/**
 * Service-locator interface to get fresh instances of Repository objects. See @Configuration
 * annotated classes for factory bean definitions.
 */
public interface RepositoryFactory {
  /**
   * Gets the single, or first-found {@link IRepository}
   *
   * @return
   */
  IRepository getRepository();

  /**
   * Gets repository by a bean name
   *
   * @param id
   * @return
   */
  IRepository getRepository(String id);
}
