package com.researchspace.webapp.integrations.datacite;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.datacite.client.DataCiteClient;
import com.researchspace.datacite.client.DataCiteClientImpl;
import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public class DataCiteConnectorImpl implements DataCiteConnector {

  @Autowired private SystemPropertyManager sysPropertyMgr;

  private final Map<InventorySettingType, DataCiteClient> dataCiteClients =
      new EnumMap<>(InventorySettingType.class);

  private final Map<InventorySettingType, Boolean> dataCiteEnabled =
      new EnumMap<>(InventorySettingType.class);

  @EventListener(ContextRefreshedEvent.class)
  @Transactional(readOnly = true)
  public void reloadDataCiteClient() {
    Map<String, SystemPropertyValue> propertiesMap = sysPropertyMgr.getAllSysadminPropertiesAsMap();
    reloadClientForType(
        InventorySettingType.IGSN,
        propertiesMap,
        SystemPropertyName.IGSN_DATACITE_ENABLED,
        SystemPropertyName.IGSN_DATACITE_SERVER_URL,
        SystemPropertyName.IGSN_DATACITE_USERNAME,
        SystemPropertyName.IGSN_DATACITE_PASSWORD,
        SystemPropertyName.IGSN_DATACITE_REPOSITORY_PREFIX);
    reloadClientForType(
        InventorySettingType.PIDINST,
        propertiesMap,
        SystemPropertyName.PIDINST_DATACITE_ENABLED,
        SystemPropertyName.PIDINST_DATACITE_SERVER_URL,
        SystemPropertyName.PIDINST_DATACITE_USERNAME,
        SystemPropertyName.PIDINST_DATACITE_PASSWORD,
        SystemPropertyName.PIDINST_DATACITE_REPOSITORY_PREFIX);
  }

  private void reloadClientForType(
      InventorySettingType settingType,
      Map<String, SystemPropertyValue> propertiesMap,
      SystemPropertyName enabledProperty,
      SystemPropertyName serverUrlProperty,
      SystemPropertyName usernameProperty,
      SystemPropertyName passwordProperty,
      SystemPropertyName repositoryPrefixProperty) {

    dataCiteClients.remove(settingType);
    dataCiteEnabled.put(
        settingType, Boolean.valueOf(getPropertyValue(propertiesMap, enabledProperty)));
    String serverUrl = getPropertyValue(propertiesMap, serverUrlProperty);
    String username = getPropertyValue(propertiesMap, usernameProperty);
    String password = getPropertyValue(propertiesMap, passwordProperty);
    String repositoryPrefix = getPropertyValue(propertiesMap, repositoryPrefixProperty);

    if (StringUtils.isEmpty(serverUrl)
        || StringUtils.isEmpty(username)
        || StringUtils.isEmpty(password)
        || StringUtils.isEmpty(repositoryPrefix)) {
      log.info("{} configuration incomplete, skipping DataCite client initialization", settingType);
      return;
    }

    try {
      URI dataciteUri = new URI(serverUrl);
      log.info(
          "Configuring {} datacite client for server {} with user {} and repository prefix {} ",
          settingType,
          dataciteUri,
          username,
          repositoryPrefix);
      dataCiteClients.put(
          settingType, new DataCiteClientImpl(dataciteUri, username, password, repositoryPrefix));
    } catch (URISyntaxException e) {
      log.warn("Cannot parse dataciteUri {}: {}", serverUrl, e.getMessage());
    }
  }

  private String getPropertyValue(
      Map<String, SystemPropertyValue> propertiesMap, SystemPropertyName property) {
    SystemPropertyValue value = propertiesMap.get(property.getPropertyName());
    return value == null ? null : value.getValue();
  }

  private DataCiteClient getClient(InventorySettingType settingType) {
    DataCiteClient client = dataCiteClients.get(settingType);
    if (client == null) {
      throw new DataCiteConnectionException(
          "DataCite client not initialized. Are all DataCite settings provided?", null);
    }
    return client;
  }

  @Override
  public boolean isDataCiteConfiguredAndEnabled(InventorySettingType settingType) {
    return Boolean.TRUE.equals(dataCiteEnabled.get(settingType))
        && dataCiteClients.get(settingType) != null;
  }

  @Override
  public boolean testDataCiteConnection(InventorySettingType settingType) {
    return getClient(settingType).testConnectionToDataCite();
  }

  @Override
  public DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType) {
    return getClient(settingType).registerDoi(dataCiteDoi);
  }

  @Override
  public boolean deleteDoi(String s, InventorySettingType settingType) {
    return getClient(settingType).deleteDoi(s);
  }

  @Override
  public DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType) {
    return getClient(settingType).publishDoi(dataCiteDoi);
  }

  @Override
  public DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType) {
    return getClient(settingType).retractDoi(dataCiteDoi);
  }
}
