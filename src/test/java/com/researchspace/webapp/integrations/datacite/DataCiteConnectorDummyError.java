package com.researchspace.webapp.integrations.datacite;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiSearchResult;
import java.util.Optional;
import lombok.Getter;

public class DataCiteConnectorDummyError implements DataCiteConnector {

  @Getter public DataCiteDoi doiSentToDatacite;

  @Override
  public DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public boolean deleteDoi(String s, InventorySettingType settingType) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public DataCiteDoi updateDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public Optional<DataCiteDoi> findDoi(String doiId, InventorySettingType settingType) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public DataCiteDoiSearchResult searchInstrumentDois(
      String query, int pageSize, InventorySettingType settingType) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public void reloadDataCiteClient() {}

  @Override
  public boolean isDataCiteConfiguredAndEnabled(InventorySettingType settingType) {
    return false;
  }

  @Override
  public boolean testDataCiteConnection(InventorySettingType settingType) {
    return false;
  }
}
