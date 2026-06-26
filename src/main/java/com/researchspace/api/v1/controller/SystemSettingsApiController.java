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
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
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
  @Autowired private B2instConnector b2instConnector;

  /**
   * The system-property names backing the five {@link IdentifierSettings} slots of one provider.
   * For {@code PIDINST_B2INST} the generic {@code username}/{@code password} slots map to {@code
   * community.id}/{@code token} and {@code repositoryPrefix} is unused ({@code null}).
   */
  private record SettingsProps(
      SystemPropertyName enabled,
      SystemPropertyName serverUrl,
      SystemPropertyName username,
      SystemPropertyName password,
      SystemPropertyName repositoryPrefix) {}

  private SettingsProps propsFor(IdentifierType provider) {
    switch (provider) {
      case IGSN_DATACITE:
        return new SettingsProps(
            SystemPropertyName.IGSN_DATACITE_ENABLED,
            SystemPropertyName.IGSN_DATACITE_SERVER_URL,
            SystemPropertyName.IGSN_DATACITE_USERNAME,
            SystemPropertyName.IGSN_DATACITE_PASSWORD,
            SystemPropertyName.IGSN_DATACITE_REPOSITORY_PREFIX);
      case PIDINST_DATACITE:
        return new SettingsProps(
            SystemPropertyName.PIDINST_DATACITE_ENABLED,
            SystemPropertyName.PIDINST_DATACITE_SERVER_URL,
            SystemPropertyName.PIDINST_DATACITE_USERNAME,
            SystemPropertyName.PIDINST_DATACITE_PASSWORD,
            SystemPropertyName.PIDINST_DATACITE_REPOSITORY_PREFIX);
      case PIDINST_B2INST:
        return new SettingsProps(
            SystemPropertyName.PIDINST_B2INST_ENABLED,
            SystemPropertyName.PIDINST_B2INST_SERVER_URL,
            SystemPropertyName.PIDINST_B2INST_COMMUNITY_ID,
            SystemPropertyName.PIDINST_B2INST_TOKEN,
            null);
      default:
        throw new IllegalArgumentException("Unsupported identifier provider: " + provider);
    }
  }

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
    settings.addSetting(
        InventorySettingType.IGSN, buildSettings(IdentifierType.IGSN_DATACITE, propertiesMap));
    settings.addSetting(
        InventorySettingType.PIDINST,
        buildSettings(IdentifierType.PIDINST_DATACITE, propertiesMap));
    settings.addSetting(
        InventorySettingType.PIDINST, buildSettings(IdentifierType.PIDINST_B2INST, propertiesMap));
    return settings;
  }

  private IdentifierSettings buildSettings(
      IdentifierType provider, Map<String, SystemPropertyValue> propertiesMap) {
    SettingsProps p = propsFor(provider);
    IdentifierSettings settings = new IdentifierSettings();
    settings.setProvider(provider);
    settings.setEnabled(getPropertyValue(propertiesMap, p.enabled()));
    settings.setServerUrl(getPropertyValue(propertiesMap, p.serverUrl()));
    settings.setUsername(getPropertyValue(propertiesMap, p.username()));
    settings.setPassword(getPropertyValue(propertiesMap, p.password()));
    settings.setRepositoryPrefix(
        p.repositoryPrefix() == null
            ? null
            : getPropertyValue(propertiesMap, p.repositoryPrefix()));
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

    IdentifierType provider = incomingSettings.getProvider();
    InventorySettingType settingType = settingTypeForProvider(provider);
    ApiInventorySystemSettings before = getCurrentSettings();
    IdentifierSettings current = before.findByProvider(provider).orElseGet(IdentifierSettings::new);

    boolean settingsUpdated = saveProviderSettings(provider, incomingSettings, current, subject);

    // mutual exclusivity: enabling one PIDINST provider disables the other PIDINST provider
    if (InventorySettingType.PIDINST.equals(settingType)
        && isEnabled(incomingSettings.getEnabled())) {
      IdentifierType sibling = siblingPidinstProvider(provider);
      IdentifierSettings siblingCurrent =
          before.findByProvider(sibling).orElseGet(IdentifierSettings::new);
      settingsUpdated |=
          saveIfChanged(propsFor(sibling).enabled(), "false", siblingCurrent.getEnabled(), subject);
    }

    if (settingsUpdated) {
      dataCiteConnector.reloadDataCiteClient();
      b2instConnector.reloadClient();
    }
    return getCurrentSettings();
  }

  private boolean saveProviderSettings(
      IdentifierType provider,
      IdentifierSettings incoming,
      IdentifierSettings current,
      User subject) {
    SettingsProps p = propsFor(provider);
    boolean updated = false;
    updated |= saveIfChanged(p.enabled(), incoming.getEnabled(), current.getEnabled(), subject);
    updated |=
        saveIfChanged(p.serverUrl(), incoming.getServerUrl(), current.getServerUrl(), subject);
    updated |= saveIfChanged(p.username(), incoming.getUsername(), current.getUsername(), subject);
    updated |= saveIfChanged(p.password(), incoming.getPassword(), current.getPassword(), subject);
    if (p.repositoryPrefix() != null) {
      updated |=
          saveIfChanged(
              p.repositoryPrefix(),
              incoming.getRepositoryPrefix(),
              current.getRepositoryPrefix(),
              subject);
    }
    return updated;
  }

  /**
   * The provider selects which configuration is updated: an {@code IGSN_DATACITE} provider targets
   * the IGSN configuration, any PIDINST provider ({@code PIDINST_DATACITE} / {@code
   * PIDINST_B2INST}) targets the PIDINST configuration.
   */
  private InventorySettingType settingTypeForProvider(IdentifierType provider) {
    return IdentifierType.IGSN_DATACITE.equals(provider)
        ? InventorySettingType.IGSN
        : InventorySettingType.PIDINST;
  }

  private IdentifierType siblingPidinstProvider(IdentifierType provider) {
    return IdentifierType.PIDINST_B2INST.equals(provider)
        ? IdentifierType.PIDINST_DATACITE
        : IdentifierType.PIDINST_B2INST;
  }

  private boolean isEnabled(String enabled) {
    return "true".equalsIgnoreCase(enabled);
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
