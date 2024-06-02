package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.repository.spi.RepositoryConfig;
import com.researchspace.service.IRepositoryConfigFactory;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.webapp.controller.repositories.RSpaceRepoConnectionConfig;
import java.net.MalformedURLException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class RepositoryConfigFactoryImpl implements IRepositoryConfigFactory {

  private @Autowired UserConnectionManager userConnectionManager;

  public RepositoryConfig createRepositoryConfigFromAppCfg(
      RSpaceRepoConnectionConfig cfg, User subject) throws MalformedURLException {
    return new RepositoryConfig(
        cfg.getRepositoryURL().orElse(null),
        cfg.getApiKey(),
        cfg.getPassword().orElse(null),
        cfg.getRepoName().orElse(null));
  }

  @Override
  public Optional<String> getDisplayLabelForAppConfig(AppConfigElementSet set, User subject) {
    String rc = null;
    if (App.APP_DATAVERSE.equals(set.getUserAppConfig().getApp().getName())) {
      rc = set.findElementByPropertyName("DATAVERSE_ALIAS").getValue();
    }
    return Optional.ofNullable(rc);
  }
}
