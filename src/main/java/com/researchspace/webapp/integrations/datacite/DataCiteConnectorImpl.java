package com.researchspace.webapp.integrations.datacite;

import com.researchspace.datacite.client.DataCiteClient;
import com.researchspace.datacite.client.DataCiteClientImpl;
import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class DataCiteConnectorImpl implements DataCiteConnector {

  @Autowired private SystemPropertyManager sysPropertyMgr;

  private DataCiteClient dataCiteClient;

  private boolean dataCiteEnabled;

  @PostConstruct
  public void reloadDataCiteClient() {
    dataCiteClient = null;

    Map<String, SystemPropertyValue> propertiesMap = sysPropertyMgr.getAllSysadminPropertiesAsMap();
    dataCiteEnabled = Boolean.valueOf(propertiesMap.get("datacite.enabled").getValue());
    String dataciteServerUrl = propertiesMap.get("datacite.server.url").getValue();
    String dataciteUsername = propertiesMap.get("datacite.username").getValue();
    String datacitePassword = propertiesMap.get("datacite.password").getValue();
    String repositoryPrefix = propertiesMap.get("datacite.repositoryPrefix").getValue();

    if (StringUtils.isEmpty(dataciteServerUrl)
        || StringUtils.isEmpty(dataciteUsername)
        || StringUtils.isEmpty(datacitePassword)
        || StringUtils.isEmpty(repositoryPrefix)) {
      log.info("IGSN configuration incomplete, skipping DataCite client initialization");
      return;
    }

    try {
      URI dataciteUri = new URI(dataciteServerUrl);
      log.info(
          "Configuring datacite client for server {} with user {} and repository prefix {} ",
          dataciteUri,
          dataciteUsername,
          repositoryPrefix);
      dataCiteClient =
          new DataCiteClientImpl(dataciteUri, dataciteUsername, datacitePassword, repositoryPrefix);
    } catch (URISyntaxException e) {
      log.warn("Cannot parse dataciteUri {}: {}", dataciteServerUrl, e.getMessage());
    }
  }

  @Override
  public boolean isDataCiteConfiguredAndEnabled() {
    return dataCiteEnabled && dataCiteClient != null;
  }

  @Override
  public boolean testDataCiteConnection() {
    if (dataCiteClient == null) {
      throw new DataCiteConnectionException(
          "DataCite client not initialized. Are all DataCite settings provided?", null);
    }
    return dataCiteClient.testConnectionToDataCite();
  }

  @Override
  public DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi) {
    return dataCiteClient.registerDoi(dataCiteDoi);
  }

  @Override
  public boolean deleteDoi(String s) {
    return dataCiteClient.deleteDoi(s);
  }

  @Override
  public DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi) {
    return dataCiteClient.publishDoi(dataCiteDoi);
  }

  @Override
  public DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi) {
    return dataCiteClient.retractDoi(dataCiteDoi);
  }
}
