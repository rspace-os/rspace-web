package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.repository.spi.RepositoryConfig;
import com.researchspace.webapp.controller.repositories.RSpaceRepoConnectionConfig;
import java.net.MalformedURLException;
import java.util.Optional;

/**
 * Creates a {@link RepositoryConfig} to send to repository module from information stored in an
 * {@link AppConfigElementSet}. Each repository type should implement how this information should be
 * configured.
 *
 * <h4>Implementation notes </h3>
 *
 * This interface is an attempt to encapsulate the conversion between AppConfigs in RSpace and
 * repository-specific code that would be better off in a RepositoryAdapter module, except we don't
 * want these modules to depend on AppConfigElements.
 */
public interface IRepositoryConfigFactory {

  RepositoryConfig createRepositoryConfigFromAppCfg(RSpaceRepoConnectionConfig cfg, User subject)
      throws MalformedURLException;

  /**
   * For a given {@link AppConfigElementSet}, optionally gets a property value that be used as a
   * display label.
   *
   * @param set an {@link AppConfigElementSet} containing configuration for an individual app
   *     instance.
   * @param subject
   * @return An Optional<String>
   */
  Optional<String> getDisplayLabelForAppConfig(AppConfigElementSet set, User subject);
}
