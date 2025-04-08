package com.researchspace.webapp.integrations.datacite;

import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import lombok.Getter;

public class DataCiteConnectorDummyError implements DataCiteConnector {

  @Getter public DataCiteDoi doiSentToDatacite;

  @Override
  public DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public boolean deleteDoi(String s) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi) {
    throw new DataCiteConnectionException("Error when contacting DataCite", null);
  }

  @Override
  public void reloadDataCiteClient() {}

  @Override
  public boolean isDataCiteConfiguredAndEnabled() {
    return false;
  }

  @Override
  public boolean testDataCiteConnection() {
    return false;
  }
}
