package com.researchspace.webapp.integrations.datacite;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.datacite.model.DataCiteDoi;

/**
 * Connects to DataCite for registering identifiers. Holds one client per {@link
 * InventorySettingType}: IGSN (configured from datacite.* system properties) and PDINST (from
 * pdinst.* system properties). The no-arg variants operate on the IGSN client.
 */
public interface DataCiteConnector {

  DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType);

  boolean deleteDoi(String s, InventorySettingType settingType);

  DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType);

  DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType);

  void reloadDataCiteClient();

  boolean isDataCiteConfiguredAndEnabled(InventorySettingType settingType);

  boolean testDataCiteConnection(InventorySettingType settingType);

  default DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi) {
    return registerDoi(dataCiteDoi, InventorySettingType.IGSN);
  }

  default boolean deleteDoi(String s) {
    return deleteDoi(s, InventorySettingType.IGSN);
  }

  default DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi) {
    return publishDoi(dataCiteDoi, InventorySettingType.IGSN);
  }

  default DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi) {
    return retractDoi(dataCiteDoi, InventorySettingType.IGSN);
  }

  default boolean isDataCiteConfiguredAndEnabled() {
    return isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN);
  }

  default boolean testDataCiteConnection() {
    return testDataCiteConnection(InventorySettingType.IGSN);
  }
}
