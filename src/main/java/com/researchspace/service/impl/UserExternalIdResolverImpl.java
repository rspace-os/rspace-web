package com.researchspace.service.impl;

import static com.researchspace.repository.spi.IdentifierScheme.ORCID;
import static com.researchspace.service.IntegrationsHandler.ORCID_APP_NAME;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserExternalIdResolver;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

public class UserExternalIdResolverImpl implements UserExternalIdResolver {

  @Autowired private IntegrationsHandler appHandler;

  public void setAppHandler(IntegrationsHandler appHandler) {
    this.appHandler = appHandler;
  }

  @Override
  public Optional<ExternalId> getExternalIdForUser(User user, IdentifierScheme scheme) {
    ExternalId rc = null;
    if (ORCID.equals(scheme)) {
      IntegrationInfo orcidIntegration = appHandler.getIntegration(user, ORCID_APP_NAME);
      if (orcidIntegration != null
          && orcidIntegration.isAvailable()
          && orcidIntegration.retrieveFirstOptionValue() != null) {
        rc = new ExternalId(scheme, orcidIntegration.retrieveFirstOptionValue());
      }
    }
    return Optional.ofNullable(rc);
  }

  private boolean isOrcidAvailable(User user) {
    return appHandler.getIntegration(user, ORCID_APP_NAME).isAvailable();
  }

  @Override
  public boolean isIdentifierSchemeAvailable(User user, IdentifierScheme scheme) {
    boolean rc = false;
    if (ORCID.equals(scheme)) {
      rc = isOrcidAvailable(user);
    }
    return rc;
  }
}
