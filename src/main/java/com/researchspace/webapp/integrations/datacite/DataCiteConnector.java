package com.researchspace.webapp.integrations.datacite;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.datacite.model.DataCiteDoi;

/**
 * Connects to DataCite for registering identifiers. Holds one client per {@link
 * InventorySettingType}: IGSN (configured from datacite.* system properties) and PIDINST (from
 * pidinst.datacite.* system properties). The no-arg variants operate on the IGSN client.
 */
public interface DataCiteConnector {

  DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType);

  boolean deleteDoi(String s, InventorySettingType settingType);

  DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType);

  /**
   * Rewrites a DOI's metadata in place, leaving its state alone. Unlike publish and retract, which
   * are the same call carrying an {@code event}, this sends no event, so a {@code draft} DOI stays
   * a draft; that is what makes it the external metadata update for a draft (RSDEV-1251, ADR 0008).
   * A findable DOI is refreshed through the existing Republish instead.
   */
  DataCiteDoi updateDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType);

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
