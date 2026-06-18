package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.SystemSettingsApi;
import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
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

    IdentifierSettings igsnSettings = settings.getOrCreate(InventorySettingType.IGSN);
    igsnSettings.setProvider(IdentifierType.IGSN_DATACITE);
    igsnSettings.setServerUrl(
        getPropertyValue(propertiesMap, SystemPropertyName.IGSN_DATACITE_SERVER_URL));
    igsnSettings.setUsername(
        getPropertyValue(propertiesMap, SystemPropertyName.IGSN_DATACITE_USERNAME));
    igsnSettings.setPassword(
        getPropertyValue(propertiesMap, SystemPropertyName.IGSN_DATACITE_PASSWORD));
    igsnSettings.setRepositoryPrefix(
        getPropertyValue(propertiesMap, SystemPropertyName.IGSN_DATACITE_REPOSITORY_PREFIX));
    igsnSettings.setEnabled(
        getPropertyValue(propertiesMap, SystemPropertyName.IGSN_DATACITE_ENABLED));

    IdentifierSettings pidinstSettings = settings.getOrCreate(InventorySettingType.PIDINST);
    // the PIDINST provider is implicit (always PIDINST_DATACITE) and has no persisted property
    pidinstSettings.setProvider(IdentifierType.PIDINST_DATACITE);
    pidinstSettings.setServerUrl(
        getPropertyValue(propertiesMap, SystemPropertyName.PIDINST_DATACITE_SERVER_URL));
    pidinstSettings.setUsername(
        getPropertyValue(propertiesMap, SystemPropertyName.PIDINST_DATACITE_USERNAME));
    pidinstSettings.setPassword(
        getPropertyValue(propertiesMap, SystemPropertyName.PIDINST_DATACITE_PASSWORD));
    pidinstSettings.setRepositoryPrefix(
        getPropertyValue(propertiesMap, SystemPropertyName.PIDINST_DATACITE_REPOSITORY_PREFIX));
    pidinstSettings.setEnabled(
        getPropertyValue(propertiesMap, SystemPropertyName.PIDINST_DATACITE_ENABLED));

    return settings;
  }

  private String getPropertyValue(
      Map<String, SystemPropertyValue> propertiesMap, SystemPropertyName property) {
    SystemPropertyValue value = propertiesMap.get(property.getPropertyName());
    return value == null ? null : value.getValue();
  }

  @Override
  public ApiInventorySystemSettings updateInventorySettings(
      ServletRequest req,
      @RequestBody @Valid IdentifierSettings incomingSettings,
      BindingResult errors,
      @RequestAttribute(name = "user") User subject)
      throws BindException {

    assertIsSysadmin(subject, req);
    if (incomingSettings.getProvider() == null) {
      errors.reject(
          "errors.inventory.settings.provider.required",
          messages.getMessage("errors.inventory.settings.provider.required"));
    }
    throwBindExceptionIfErrors(errors);

    InventorySettingType settingType = settingTypeForProvider(incomingSettings.getProvider());
    boolean igsn = InventorySettingType.IGSN.equals(settingType);
    IdentifierSettings current = getCurrentSettings().getOrCreate(settingType);
    boolean settingsUpdated = false;

    // both IGSN and PIDINST providers are implicit (IGSN_DATACITE / PIDINST_DATACITE) and have no
    // persisted property; only the credentials below are stored
    settingsUpdated |=
        saveIfChanged(
            igsn
                ? SystemPropertyName.IGSN_DATACITE_ENABLED
                : SystemPropertyName.PIDINST_DATACITE_ENABLED,
            incomingSettings.getEnabled(),
            current.getEnabled(),
            subject);
    settingsUpdated |=
        saveIfChanged(
            igsn
                ? SystemPropertyName.IGSN_DATACITE_SERVER_URL
                : SystemPropertyName.PIDINST_DATACITE_SERVER_URL,
            incomingSettings.getServerUrl(),
            current.getServerUrl(),
            subject);
    settingsUpdated |=
        saveIfChanged(
            igsn
                ? SystemPropertyName.IGSN_DATACITE_USERNAME
                : SystemPropertyName.PIDINST_DATACITE_USERNAME,
            incomingSettings.getUsername(),
            current.getUsername(),
            subject);
    settingsUpdated |=
        saveIfChanged(
            igsn
                ? SystemPropertyName.IGSN_DATACITE_PASSWORD
                : SystemPropertyName.PIDINST_DATACITE_PASSWORD,
            incomingSettings.getPassword(),
            current.getPassword(),
            subject);
    settingsUpdated |=
        saveIfChanged(
            igsn
                ? SystemPropertyName.IGSN_DATACITE_REPOSITORY_PREFIX
                : SystemPropertyName.PIDINST_DATACITE_REPOSITORY_PREFIX,
            incomingSettings.getRepositoryPrefix(),
            current.getRepositoryPrefix(),
            subject);

    if (settingsUpdated) {
      dataCiteConnector.reloadDataCiteClient();
    }
    return getCurrentSettings();
  }

  /**
   * The provider selects which configuration is updated: a {@code IGSN_DATACITE} provider targets
   * the IGSN configuration, any PIDINST provider ({@code PIDINST_DATACITE} / {@code
   * PIDINST_B2INST}) targets the PIDINST configuration.
   */
  private InventorySettingType settingTypeForProvider(IdentifierType provider) {
    return IdentifierType.IGSN_DATACITE.equals(provider)
        ? InventorySettingType.IGSN
        : InventorySettingType.PIDINST;
  }

  private boolean saveIfChanged(
      SystemPropertyName property, String incomingValue, String currentValue, User subject) {
    if (incomingValue != null && !incomingValue.equals(currentValue)) {
      sysPropertyMgr.save(property, incomingValue, subject);
      return true;
    }
    return false;
  }
}
