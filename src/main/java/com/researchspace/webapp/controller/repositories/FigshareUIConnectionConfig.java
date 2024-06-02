package com.researchspace.webapp.controller.repositories;

import com.researchspace.model.User;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserConnectionManager;

public class FigshareUIConnectionConfig implements RSpaceRepoConnectionConfig {

  private User subject;
  private UserConnectionManager source;

  public FigshareUIConnectionConfig(UserConnectionManager source, User subject) {
    this.subject = subject;
    this.source = source;
  }

  @Override
  public String getApiKey() {
    return source
        .findByUserNameProviderName(subject.getUsername(), IntegrationsHandler.FIGSHARE_APP_NAME)
        .get()
        .getAccessToken();
  }
}
