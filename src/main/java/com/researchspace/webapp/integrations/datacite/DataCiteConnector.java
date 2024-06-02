package com.researchspace.webapp.integrations.datacite;

import com.researchspace.datacite.model.DataCiteDoi;

public interface DataCiteConnector {

  DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi);

  boolean deleteDoi(String s);

  DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi);

  DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi);

  void reloadDataCiteClient();

  boolean isDataCiteConfiguredAndEnabled();

  boolean testDataCiteConnection();
}
