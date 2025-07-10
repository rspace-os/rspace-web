package com.researchspace.service.impl;

import static com.researchspace.CacheNames.INTEGRATION_INFO;
import static com.researchspace.model.dto.IntegrationInfo.getAppNameFromIntegrationName;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_ALIAS;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_APIKEY;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_CONFIGURED_SERVERS;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.join;

import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.model.preference.BoxLinkType;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IRepositoryConfigFactory;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.integrations.pyrat.PyratClient;
import com.researchspace.webapp.integrations.pyrat.PyratServerDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;

/**
 *
 *
 * <h3>Caching notes</h3>
 *
 * <p>IntegrationInfo objects are cached using Spring cache abstraction backed by ehcache. The cache
 * key is a compound key of {username}{preferencename}, e.g. 'user5ECAT'. <br>
 * These keys are used in this class, in {@link SystemPropertyManagerImpl} and and {@link
 * UserManagerImpl} as the underlying system properties and preferences are also cached.
 *
 * <p>Whenever a system property is changed, all IntegrationInfo cached items are evicted, as the
 * system property of availability has a global effect on all users.
 *
 * @see {@link IntegrationsHandler}
 */
@Slf4j
public class IntegrationsHandlerImpl implements IntegrationsHandler {

  private @Autowired SystemPropertyManager sysPropMgr;
  private @Autowired SystemPropertyPermissionManager systemPropertyPermissionUtils;
  private @Autowired UserAppConfigManager appConfigMgr;
  private @Autowired UserManager userMgr;
  private @Autowired IRepositoryConfigFactory repositoryConfigFactory;
  private @Autowired UserConnectionManager userConnManager;
  private @Autowired IPropertyHolder propertyHolder;
  private @Autowired PyratClient pyratClient;

  private final Map<SystemProperty, List<SystemProperty>> parent2ChildMap = new HashMap<>();

  public void init() {
    List<SystemProperty> sysPropertyLookup = sysPropMgr.listSystemPropertyDefinitions();
    for (SystemProperty sysProp : sysPropertyLookup) {
      SystemProperty parent = sysProp.getDependent();
      if (parent != null) {
        parent2ChildMap.computeIfAbsent(parent, k -> new ArrayList<>());
        List<SystemProperty> children = parent2ChildMap.get(parent);
        if (!children.contains(sysProp)) {
          children.add(sysProp);
        }
      }
    }
  }

  public static final EnumSet<Preference> booleanIntegrationPrefs =
      EnumSet.of(
          Preference.BOX,
          Preference.DROPBOX,
          Preference.GOOGLEDRIVE,
          Preference.CHEMISTRY,
          Preference.ONEDRIVE);

  // for testing, shouldn't be exposed in interface
  public Map<SystemProperty, List<SystemProperty>> getParent2ChildMap() {
    return Collections.unmodifiableMap(parent2ChildMap);
  }

  @Override
  public boolean isValidIntegration(String integrationName) {
    if (isBlank(integrationName)) {
      return false;
    }
    if (isAppConfigIntegration(integrationName)) {
      return true;
    }
    try {
      Preference pref = Preference.valueOf(integrationName.toUpperCase());
      return booleanIntegrationPrefs.contains(pref);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Override
  // if method arguments change, remember to update the 'key' attribute
  @Cacheable(value = INTEGRATION_INFO, key = "#user.username + #integrationName")
  public IntegrationInfo getIntegration(User user, String integrationName) {

    checkValidIntegration(integrationName);
    IntegrationInfo info = new IntegrationInfo();
    info.setName(integrationName);

    String integrationSystemPropertyName = getSysPropertyFromIntegrationName(integrationName);
    info.setAvailable(
        systemPropertyPermissionUtils.isPropertyAllowed(user, integrationSystemPropertyName));
    SystemPropertyValue property = sysPropMgr.findByName(integrationSystemPropertyName);

    if (isAppConfigIntegration(integrationName)) {
      populateIntegrationInfoFromUserAppConfig(info, user);
    } else {
      UserPreference userpref =
          userMgr.getPreferenceForUser(user, Preference.valueOf(integrationName));
      info.setEnabled(Boolean.parseBoolean(userpref.getValue()));
      info.setOptions(getOptions(user, property, userpref));
      info.setDisplayName(userpref.getPreference().name());
    }
    postProcessInfo(info, user);
    return info;
  }

  // perform any app specific modifications
  void postProcessInfo(IntegrationInfo info, User user) {
    switch (info.getName()) {
      case PROTOCOLS_IO_APP_NAME:
        setOAuthConnectionStatus(info, user, PROTOCOLS_IO_APP_NAME);
        return;
      case CLUSTERMARKET_APP_NAME:
        setOAuthConnectionStatus(info, user, CLUSTERMARKET_APP_NAME);
        return;
      case DMPTOOL_APP_NAME:
        setOAuthConnectionStatus(info, user, DMPTOOL_APP_NAME);
        return;
      case OWNCLOUD_APP_NAME:
        setOAuthConnectionStatus(info, user, OWNCLOUD_APP_NAME);
        return;
      case NEXTCLOUD_APP_NAME:
        setOAuthConnectionStatus(info, user, NEXTCLOUD_APP_NAME);
        return;
      case FIGSHARE_APP_NAME:
        setOAuthConnectionStatus(info, user, FIGSHARE_APP_NAME);
        return;
      case DRYAD_APP_NAME:
        setOAuthConnectionStatus(info, user, DRYAD_APP_NAME);
        return;
      case PYRAT_APP_NAME:
        setMultipleUserTokens(
            info, user, PYRAT_APP_NAME, PYRAT_CONFIGURED_SERVERS, PYRAT_ALIAS, PYRAT_APIKEY);
        return;
      case ZENODO_APP_NAME:
        setSingleUserToken(info, user, ZENODO_APP_NAME, ZENODO_USER_TOKEN);
        return;
      case FIELDMARK_APP_NAME:
        setSingleUserToken(info, user, FIELDMARK_APP_NAME, FIELDMARK_USER_TOKEN);
        return;
      case GALAXY_APP_NAME:
        setSingleUserToken(info, user, GALAXY_APP_NAME, GALAXY_API_KEY);
        return;
      case DIGITAL_COMMONS_DATA_APP_NAME:
        setSingleUserToken(
            info, user, DIGITAL_COMMONS_DATA_APP_NAME, DIGITAL_COMMONS_DATA_USER_TOKEN);
        return;
      case DMPONLINE_APP_NAME:
        setSingleUserToken(info, user, DMPONLINE_APP_NAME, DMPONLINE_USER_TOKEN);
        return;
      case ASCENSCIA_APP_NAME:
        setSingleUserToken(info, user, ASCENSCIA_APP_NAME, ASCENSCIA_USER_TOKEN);
        return;
      default:
    }
  }

  private void setMultipleUserTokens(
      IntegrationInfo info,
      User user,
      String appName,
      String paramConfiguredServers,
      String paramAlias,
      String paramApiKey) {
    Map<String, String> apikeyByAlias = getTokenMapForProvider(user, appName);

    for (Entry<String, Object> infoElement : info.getOptions().entrySet()) {
      if (!paramConfiguredServers.equals(infoElement.getKey())) {
        Map<String, String> configElementMapSet = (Map<String, String>) infoElement.getValue();
        String aliasToConfigure = configElementMapSet.get(paramAlias); // i.e.: "mice server"
        String apiKey =
            apikeyByAlias.get(aliasToConfigure) == null ? "" : apikeyByAlias.get(aliasToConfigure);
        configElementMapSet.put(paramApiKey, apiKey);
      }
    }
  }

  // this is using UserConnection table to store OAuth token.
  private void setOAuthConnectionStatus(IntegrationInfo info, User user, String appName) {
    getTokenForProvider(user, appName).ifPresent(token -> updateInfoWithOAuthToken(info, token));
  }

  private String updateInfoWithOAuthToken(IntegrationInfo info, String token) {
    info.getOptions().put(ACCESS_TOKEN_SETTING, token);
    info.setOauthConnected(true);
    return token;
  }

  private Optional<String> getTokenForProvider(User user, String provider) {
    return userConnManager
        .findByUserNameProviderName(user.getUsername(), provider)
        .map(UserConnection::getAccessToken);
  }

  private Optional<String> getTokenForProvider(User user, String provider, String discriminant) {
    return userConnManager
        .findByUserNameProviderName(user.getUsername(), provider, discriminant)
        .map(UserConnection::getAccessToken);
  }

  // Map<alias, api-key>
  private Map<String, String> getTokenMapForProvider(User user, String provider) {
    List<UserConnection> userConnectionList =
        userConnManager.findListByUserNameProviderName(user.getUsername(), provider);
    Map<String, String> apikeyByAlias = new HashMap<>();
    if (userConnectionList != null && !userConnectionList.isEmpty()) {
      for (UserConnection currentUserConnection : userConnectionList) {
        apikeyByAlias.put(
            currentUserConnection.getId().getProviderUserId(),
            currentUserConnection.getAccessToken());
      }
    }
    return apikeyByAlias;
  }

  private void setSingleUserToken(
      IntegrationInfo info, User user, String appName, String tokenName) {
    Optional<String> userToken = getTokenForProvider(user, appName);
    userToken.ifPresent(t -> info.getOptions().put(tokenName, t));
  }

  private void populateIntegrationInfoFromUserAppConfig(IntegrationInfo info, User user) {
    UserAppConfig appConfig = getAppConfig(info.getName(), user);

    if (appConfig != null) {
      info.setEnabled(appConfig.isEnabled());
      info.setDisplayName(appConfig.getApp().getLabel());
      Map<String, Object> options = new HashMap<>();

      // For PyRAT we need to fetch the configured servers returned by the client
      if (info.getName().equals(PYRAT_APP_NAME)) {
        Map<String, PyratServerDTO> pyratServerByAliasMap = pyratClient.getServerByAlias();
        if (!pyratServerByAliasMap.isEmpty()) {
          List<PyratServerDTO> pyratConfiguredServers = new LinkedList<>();
          options.put(PYRAT_CONFIGURED_SERVERS, pyratConfiguredServers);

          // add the configured servers in the options
          for (Entry<String, PyratServerDTO> pyratServerByAlias :
              pyratServerByAliasMap.entrySet()) {
            pyratConfiguredServers.add(createServerEntry(pyratServerByAlias));
          }
        }
      }

      appConfig
          .getAppConfigElementSets()
          .forEach(
              set -> {
                Map<String, String> elementSetOptions = new HashMap<>();
                set.getConfigElements()
                    .forEach(
                        el -> {
                          elementSetOptions.put(
                              el.getAppConfigElementDescriptor().getDescriptor().getName(),
                              el.getValue());
                        });
                getLabelForElementSet(set, user)
                    .map(label -> elementSetOptions.put("_label", label));
                options.put("" + set.getId(), elementSetOptions);
              });
      info.setOptions(options);
    }
  }

  @NotNull
  private static PyratServerDTO createServerEntry(
      Entry<String, PyratServerDTO> pyratServerByAlias) {
    return new PyratServerDTO(pyratServerByAlias.getKey(), pyratServerByAlias.getValue().getUrl());
  }

  private UserAppConfig getAppConfig(String infoName, User user) {
    UserAppConfig appConfig = null;
    try {
      appConfig = appConfigMgr.getByAppName(getAppNameFromIntegrationName(infoName), user);
    } catch (DataAccessException e) {
      log.warn(e.getMessage());
      // try again. might have been a race condition to get the info object RSPAC-1638
      appConfig = appConfigMgr.getByAppName(getAppNameFromIntegrationName(infoName), user);
    }
    return appConfig;
  }

  // gets a property that can serve as a label to distinguish apps of the same type.
  private Optional<String> getLabelForElementSet(AppConfigElementSet set, User user) {
    return repositoryConfigFactory.getDisplayLabelForAppConfig(set, user);
  }

  private Map<String, Object> getOptions(
      User user, SystemPropertyValue property, UserPreference userpref) {
    Map<String, Object> options = new TreeMap<>();

    // options taken from user preferences
    Preference preference = userpref.getPreference();
    if (Preference.BOX.equals(preference)) {
      String linkTypePrefValue =
          userMgr.getPreferenceForUser(userpref.getUser(), Preference.BOX_LINK_TYPE).getValue();
      if (isEmpty(linkTypePrefValue)) {
        linkTypePrefValue = BoxLinkType.LIVE.toString();
      }
      options.put(Preference.BOX_LINK_TYPE.name(), linkTypePrefValue);
    }

    // options taken from system properties
    if (parent2ChildMap.get(property.getProperty()) != null) {
      for (SystemProperty child : parent2ChildMap.get(property.getProperty())) {
        try {
          options.put(
              child.getName(),
              systemPropertyPermissionUtils.isPropertyAllowed(user, child.getName()));
        } catch (IllegalArgumentException e) {
          // Value was not one of ALLOWED, DENIED_BY_DEFAULT or DENIED
          options.put(child.getName(), sysPropMgr.findByName(child.getName()).getValue());
        }
      }
    }
    return options;
  }

  private void checkValidIntegration(String name) {
    if (!isValidIntegration(name)) {
      throw new IllegalArgumentException(
          format(
              "Invalid integration name %s, must be one of [%s]",
              name, join(booleanIntegrationPrefs, ",")));
    }
  }

  @Override
  @CachePut(value = INTEGRATION_INFO, key = "#user.username + #newInfo.name")
  public IntegrationInfo updateIntegrationInfo(User user, IntegrationInfo newInfo) {

    String integrationName = newInfo.getName();
    checkValidIntegration(integrationName);

    if (isAppConfigIntegration(integrationName)) {
      saveEnablementInUserAppConfig(user, newInfo);
      // apps with multiple configs have the configs updated through other endpoint
      saveConfigOptionsForAppsWithSingleOptionSet(user, newInfo);
      populateIntegrationInfoFromUserAppConfig(
          newInfo, user); // so the cache is updated with latest options
    } else {
      Preference pref = Preference.valueOf(integrationName);
      userMgr.setPreference(pref, newInfo.isEnabled() + "", user.getUsername());
      savePreferenceAppOptions(pref, user, newInfo);
    }
    return getIntegration(user, integrationName);
  }

  private void saveConfigOptionsForAppsWithSingleOptionSet(User user, IntegrationInfo newInfo) {
    switch (newInfo.getName()) {
      case EGNYTE_APP_NAME:
        saveAppConfigWithSingleOptionSet(user, newInfo, EGNYTE_APP_NAME, EGNYTE_DOMAIN_SETTING);
        break;
      case ZENODO_APP_NAME:
        saveNewUserConnectionForSingleOptionApp(
            newInfo.getOptions().get(ZENODO_USER_TOKEN).toString(), user, ZENODO_APP_NAME);
        break;
      case FIELDMARK_APP_NAME:
        saveNewUserConnectionForSingleOptionApp(
            newInfo.getOptions().get(FIELDMARK_USER_TOKEN).toString(), user, FIELDMARK_APP_NAME);
        break;
      case GALAXY_APP_NAME:
        saveNewUserConnectionForSingleOptionApp(
            newInfo.getOptions().get(GALAXY_API_KEY).toString(), user, GALAXY_APP_NAME);
        break;
      case JOVE_APP_NAME:
        // Jove doesn't currently fit well into our existing integration handler, so we just get the
        // global api key from properties file
        saveNewUserConnectionForSingleOptionApp(
            propertyHolder.getJoveApiKey(), user, JOVE_APP_NAME);
        break;
      case ASCENSCIA_APP_NAME:
        //        saveNewUserConnectionForSingleOptionApp(
        //            newInfo.getOptions().get(ASCENSCIA_USER_TOKEN).toString(), user,
        // ASCENSCIA_APP_NAME);
        break;
      default:
        break;
    }
  }

  public void saveConfigOptionsForAppsWithMultipleOptionSet(
      User user, Long optionsId, String appName, Map<String, String> options) {
    if (PYRAT_APP_NAME.equals(appName) && optionsId != null) {
      saveNewUserConnectionForMultipleOptionApp(
          options.get(PYRAT_APIKEY), user, PYRAT_APP_NAME, options.get(PYRAT_ALIAS));
    }
  }

  private void saveNewUserConnectionForSingleOptionApp(String token, User user, String appName) {
    saveNewUserConnectionForMultipleOptionApp(token, user, appName, appName);
  }

  private void saveNewUserConnectionForMultipleOptionApp(
      String token, User user, String appName, String discriminant) {
    Optional<UserConnection> existingConnection =
        userConnManager.findByUserNameProviderName(user.getUsername(), appName, discriminant);
    UserConnection conn = existingConnection.orElse(new UserConnection());
    if (existingConnection.isEmpty()) {
      conn.setDisplayName(appName + " User Token");
      conn.setId(new UserConnectionId(user.getUsername(), appName, discriminant));
      Optional<Integer> existingMaxRank =
          userConnManager.findMaxRankByUserNameProviderName(user.getUsername(), appName);
      if (existingMaxRank.isPresent()) {
        conn.setRank(existingMaxRank.get() + 1);
      } else {
        conn.setRank(1);
      }
    }
    conn.setAccessToken(token);
    // These user tokens do not expire, and the expiry time isn't checked, i.e api keys
    conn.setExpireTime(0L);
    userConnManager.save(conn);
  }

  private void deleteConfigOptionsForAppsWithMultipleOptionSet(
      User user, String appName, String descriptorForDiscriminant, AppConfigElementSet configSet) {
    String aliasDiscriminant = "";
    for (AppConfigElement configElement : configSet.getConfigElements()) {
      if (descriptorForDiscriminant.equals(
          configElement.getAppConfigElementDescriptor().getDescriptor().getName())) {
        aliasDiscriminant = configElement.getValue();
      }
    }
    userConnManager.deleteByUserAndProvider(user.getUsername(), appName, aliasDiscriminant);
  }

  private void saveAppConfigWithSingleOptionSet(
      User user, IntegrationInfo newInfo, String appName, String optionName) {
    String currentId = getIntegration(user, appName).retrieveFirstOptionsId();
    Long optionIdToSave = currentId == null ? null : Long.valueOf(currentId);

    Map<String, String> newOptions = new HashMap<>();
    newOptions.put(optionName, (String) newInfo.getOptions().get(optionName));
    saveAppOptions(optionIdToSave, newOptions, appName, false, user);
  }

  private void savePreferenceAppOptions(Preference prefApp, User user, IntegrationInfo newInfo) {
    if (!MapUtils.isEmpty(newInfo.getOptions())) {
      if (Preference.BOX.equals(prefApp)) {
        String newBoxLinkType =
            (String) newInfo.getOptions().get(Preference.BOX_LINK_TYPE.toString());
        userMgr.setPreference(Preference.BOX_LINK_TYPE, newBoxLinkType, user.getUsername());
      }
    }
  }

  private void saveEnablementInUserAppConfig(User user, IntegrationInfo newInfo) {
    UserAppConfig userAppConfig = getAppConfig(newInfo.getName(), user);
    if (userAppConfig.isEnabled() != newInfo.isEnabled()) {
      userAppConfig.setEnabled(newInfo.isEnabled());
      appConfigMgr.save(userAppConfig);
    }
  }

  @Override
  @CacheEvict(value = INTEGRATION_INFO, key = "#user.username + #appName")
  public void saveAppOptions(
      Long optionsId,
      Map<String, String> options,
      String appName,
      boolean trustedOrigin,
      User user) {
    // remove the apiKey from the option otherwise it is saved in clear on the database
    if (PYRAT_APP_NAME.equals(appName)) {
      Map<String, String> safeMap = new LinkedHashMap<>();
      safeMap.putAll(options);
      safeMap.remove(PYRAT_APIKEY);
      appConfigMgr.saveAppConfigElementSet(safeMap, optionsId, trustedOrigin, user);
    } else {
      appConfigMgr.saveAppConfigElementSet(options, optionsId, trustedOrigin, user);
    }
    saveConfigOptionsForAppsWithMultipleOptionSet(user, optionsId, appName, options);
  }

  @Override
  @CacheEvict(value = INTEGRATION_INFO, key = "#user.username + #appName")
  public void deleteAppOptions(Long optionsId, String appName, User user) {
    AppConfigElementSet configSetBeforeRemoval = appConfigMgr.getAppConfigElementSetById(optionsId);
    appConfigMgr.deleteAppConfigSet(optionsId, user);
    if (PYRAT_APP_NAME.equals(appName)) {
      deleteConfigOptionsForAppsWithMultipleOptionSet(
          user, appName, PYRAT_ALIAS, configSetBeforeRemoval);
    }
  }

  private boolean isAppConfigIntegration(String integrationName) {
    switch (integrationName) {
      case SLACK_APP_NAME:
      case DATAVERSE_APP_NAME:
      case GITHUB_APP_NAME:
      case ORCID_APP_NAME:
      case FIGSHARE_APP_NAME:
      case OWNCLOUD_APP_NAME:
      case NEXTCLOUD_APP_NAME:
      case EVERNOTE_APP_NAME:
      case MSTEAMS_APP_NAME:
      case ONBOARDING_APP_NAME:
      case DRYAD_APP_NAME:
      case ARGOS_APP_NAME:
      case ZENODO_APP_NAME:
      case DIGITAL_COMMONS_DATA_APP_NAME:
      case DMPONLINE_APP_NAME:
      case FIELDMARK_APP_NAME:
      case GALAXY_APP_NAME:
      case ASCENSCIA_APP_NAME:
        return true;
    }
    return isSingleOptionSetAppConfigIntegration(integrationName);
  }

  private boolean isSingleOptionSetAppConfigIntegration(String integrationName) {
    switch (integrationName) {
      case CLUSTERMARKET_APP_NAME:
      case OMERO_APP_NAME:
      case EGNYTE_APP_NAME:
      case PROTOCOLS_IO_APP_NAME:
      case DMPTOOL_APP_NAME:
      case PYRAT_APP_NAME:
      case JOVE_APP_NAME:
        return true;
    }
    return false;
  }

  private String getSysPropertyFromIntegrationName(String name) {
    return name.toLowerCase() + ".available"; // see SystemProperty table
  }

  /* For test purposes */
  protected void setUserConnectionManager(UserConnectionManager userConnectionManager) {
    this.userConnManager = userConnectionManager;
  }
}
