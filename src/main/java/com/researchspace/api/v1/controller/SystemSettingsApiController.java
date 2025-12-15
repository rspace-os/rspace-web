package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.SystemSettingsApi;
import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.util.Map;
import javax.servlet.ServletRequest;
import javax.validation.Valid;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

/** All methods must assert that sysadmin user is making the call */
@ApiController
public class SystemSettingsApiController extends BaseApiController implements SystemSettingsApi {

  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  @Autowired private SystemPropertyManager sysPropertyMgr;

  @Autowired private WhiteListIPChecker ipWhiteListChecker;
  @Autowired private DataCiteConnector dataCiteConnector;

  void assertIsSysadmin(User subject, ServletRequest request) {
    if (!subject.hasRole(Role.SYSTEM_ROLE)
        || !ipWhiteListChecker.isRequestWhitelisted(request, subject, SECURITY_LOG)) {
      throw new AuthorizationException("Sysadmin role with valid IP required for admin operations");
    }
  }

  @Override
  public ApiInventorySystemSettings getInventorySettings(
      ServletRequest req, @RequestAttribute(name = "user") User subject) {

    assertIsSysadmin(subject, req);
    return getCurrentSettings();
  }

  private ApiInventorySystemSettings getCurrentSettings() {

    Map<String, SystemPropertyValue> propertiesMap = sysPropertyMgr.getAllSysadminPropertiesAsMap();
    log.info("loaded system properties, size: " + propertiesMap.size());

    ApiInventorySystemSettings settings = new ApiInventorySystemSettings();
    settings.getDatacite().setServerUrl(propertiesMap.get("datacite.server.url").getValue());
    settings.getDatacite().setUsername(propertiesMap.get("datacite.username").getValue());
    settings.getDatacite().setPassword(propertiesMap.get("datacite.password").getValue());
    settings
        .getDatacite()
        .setRepositoryPrefix(propertiesMap.get("datacite.repositoryPrefix").getValue());
    settings.getDatacite().setEnabled(propertiesMap.get("datacite.enabled").getValue());
    return settings;
  }

  @Override
  public ApiInventorySystemSettings updateInventorySettings(
      ServletRequest req,
      @RequestBody @Valid ApiInventorySystemSettings incomingSettings,
      BindingResult errors,
      @RequestAttribute(name = "user") User subject)
      throws BindException {

    assertIsSysadmin(subject, req);

    ApiInventorySystemSettings.DataCiteSettings currentSettings =
        getCurrentSettings().getDatacite();
    ApiInventorySystemSettings.DataCiteSettings incomingDataciteSettings =
        incomingSettings.getDatacite();
    boolean dataCiteSettingsUpdated = false;

    if (incomingDataciteSettings.getEnabled() != null
        && !incomingDataciteSettings.getEnabled().equals(currentSettings.getEnabled())) {
      sysPropertyMgr.save(
          SystemPropertyName.DATACITE_ENABLED, incomingDataciteSettings.getEnabled(), subject);
      dataCiteSettingsUpdated = true;
    }
    if (incomingDataciteSettings.getServerUrl() != null
        && !incomingDataciteSettings.getServerUrl().equals(currentSettings.getServerUrl())) {
      sysPropertyMgr.save(
          SystemPropertyName.DATACITE_SERVER_URL, incomingDataciteSettings.getServerUrl(), subject);
      dataCiteSettingsUpdated = true;
    }
    if (incomingDataciteSettings.getUsername() != null
        && !incomingDataciteSettings.getUsername().equals(currentSettings.getUsername())) {
      sysPropertyMgr.save(
          SystemPropertyName.DATACITE_USERNAME, incomingDataciteSettings.getUsername(), subject);
      dataCiteSettingsUpdated = true;
    }
    if (incomingDataciteSettings.getPassword() != null
        && !incomingDataciteSettings.getPassword().equals(currentSettings.getPassword())) {
      sysPropertyMgr.save(
          SystemPropertyName.DATACITE_PASSWORD, incomingDataciteSettings.getPassword(), subject);
      dataCiteSettingsUpdated = true;
    }
    if (incomingDataciteSettings.getRepositoryPrefix() != null
        && !incomingDataciteSettings
            .getRepositoryPrefix()
            .equals(currentSettings.getRepositoryPrefix())) {
      sysPropertyMgr.save(
          SystemPropertyName.DATACITE_REPOSITORY_PREFIX,
          incomingDataciteSettings.getRepositoryPrefix(),
          subject);
      dataCiteSettingsUpdated = true;
    }

    if (dataCiteSettingsUpdated) {
      dataCiteConnector.reloadDataCiteClient();
    }
    return getCurrentSettings();
  }
}
