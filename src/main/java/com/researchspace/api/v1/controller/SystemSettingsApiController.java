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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletRequest;
import javax.validation.Valid;
import org.apache.commons.lang3.EnumUtils;
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

  private static final Map<InventorySettingType, Set<IdentifierType>> ALLOWED_PROVIDERS =
      new EnumMap<>(InventorySettingType.class);

  static {
    ALLOWED_PROVIDERS.put(InventorySettingType.IGSN, EnumSet.of(IdentifierType.DATACITE_IGSN));
    ALLOWED_PROVIDERS.put(
        InventorySettingType.PDINST,
        EnumSet.of(IdentifierType.DATACITE_PDINST, IdentifierType.B2INST_PDINST));
  }

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
    igsnSettings.setProvider(IdentifierType.DATACITE_IGSN);
    igsnSettings.setServerUrl(
        getPropertyValue(propertiesMap, SystemPropertyName.DATACITE_SERVER_URL));
    igsnSettings.setUsername(getPropertyValue(propertiesMap, SystemPropertyName.DATACITE_USERNAME));
    igsnSettings.setPassword(getPropertyValue(propertiesMap, SystemPropertyName.DATACITE_PASSWORD));
    igsnSettings.setRepositoryPrefix(
        getPropertyValue(propertiesMap, SystemPropertyName.DATACITE_REPOSITORY_PREFIX));
    igsnSettings.setEnabled(getPropertyValue(propertiesMap, SystemPropertyName.DATACITE_ENABLED));

    IdentifierSettings pdinstSettings = settings.getOrCreate(InventorySettingType.PDINST);
    pdinstSettings.setProvider(
        EnumUtils.getEnum(
            IdentifierType.class,
            getPropertyValue(propertiesMap, SystemPropertyName.PDINST_PROVIDER)));
    pdinstSettings.setServerUrl(
        getPropertyValue(propertiesMap, SystemPropertyName.PDINST_SERVER_URL));
    pdinstSettings.setUsername(getPropertyValue(propertiesMap, SystemPropertyName.PDINST_USERNAME));
    pdinstSettings.setPassword(getPropertyValue(propertiesMap, SystemPropertyName.PDINST_PASSWORD));
    pdinstSettings.setRepositoryPrefix(
        getPropertyValue(propertiesMap, SystemPropertyName.PDINST_REPOSITORY_PREFIX));
    pdinstSettings.setEnabled(getPropertyValue(propertiesMap, SystemPropertyName.PDINST_ENABLED));

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
      @RequestBody @Valid ApiInventorySystemSettings incomingSettings,
      BindingResult errors,
      @RequestAttribute(name = "user") User subject)
      throws BindException {

    assertIsSysadmin(subject, req);
    validateProviders(incomingSettings, errors);

    ApiInventorySystemSettings currentSettings = getCurrentSettings();
    boolean settingsUpdated = false;

    for (Map.Entry<InventorySettingType, IdentifierSettings> entry :
        incomingSettings.getIdentifiersSettings().entrySet()) {
      InventorySettingType settingType = entry.getKey();
      IdentifierSettings incoming = entry.getValue();
      if (incoming == null) {
        continue;
      }
      IdentifierSettings current = currentSettings.getOrCreate(settingType);
      boolean igsn = InventorySettingType.IGSN.equals(settingType);

      if (!igsn) {
        settingsUpdated |=
            saveIfChanged(
                SystemPropertyName.PDINST_PROVIDER,
                incoming.getProvider() == null ? null : incoming.getProvider().name(),
                current.getProvider() == null ? null : current.getProvider().name(),
                subject);
      }

      settingsUpdated |=
          saveIfChanged(
              igsn ? SystemPropertyName.DATACITE_ENABLED : SystemPropertyName.PDINST_ENABLED,
              incoming.getEnabled(),
              current.getEnabled(),
              subject);
      settingsUpdated |=
          saveIfChanged(
              igsn ? SystemPropertyName.DATACITE_SERVER_URL : SystemPropertyName.PDINST_SERVER_URL,
              incoming.getServerUrl(),
              current.getServerUrl(),
              subject);
      settingsUpdated |=
          saveIfChanged(
              igsn ? SystemPropertyName.DATACITE_USERNAME : SystemPropertyName.PDINST_USERNAME,
              incoming.getUsername(),
              current.getUsername(),
              subject);
      settingsUpdated |=
          saveIfChanged(
              igsn ? SystemPropertyName.DATACITE_PASSWORD : SystemPropertyName.PDINST_PASSWORD,
              incoming.getPassword(),
              current.getPassword(),
              subject);
      settingsUpdated |=
          saveIfChanged(
              igsn
                  ? SystemPropertyName.DATACITE_REPOSITORY_PREFIX
                  : SystemPropertyName.PDINST_REPOSITORY_PREFIX,
              incoming.getRepositoryPrefix(),
              current.getRepositoryPrefix(),
              subject);
    }

    if (settingsUpdated) {
      dataCiteConnector.reloadDataCiteClient();
    }
    return getCurrentSettings();
  }

  private boolean saveIfChanged(
      SystemPropertyName property, String incomingValue, String currentValue, User subject) {
    if (incomingValue != null && !incomingValue.equals(currentValue)) {
      sysPropertyMgr.save(property, incomingValue, subject);
      return true;
    }
    return false;
  }

  private void validateProviders(ApiInventorySystemSettings incomingSettings, BindingResult errors)
      throws BindException {
    for (Map.Entry<InventorySettingType, IdentifierSettings> entry :
        incomingSettings.getIdentifiersSettings().entrySet()) {
      IdentifierSettings incoming = entry.getValue();
      if (incoming == null || incoming.getProvider() == null) {
        continue;
      }
      InventorySettingType settingType = entry.getKey();
      Set<IdentifierType> allowedProviders = ALLOWED_PROVIDERS.get(settingType);
      if (!allowedProviders.contains(incoming.getProvider())) {
        errors.reject(
            "errors.inventory.settings.provider.invalid",
            messages.getMessage(
                "errors.inventory.settings.provider.invalid",
                new Object[] {incoming.getProvider(), settingType, allowedProviders}));
      }
    }
    throwBindExceptionIfErrors(errors);
  }
}
