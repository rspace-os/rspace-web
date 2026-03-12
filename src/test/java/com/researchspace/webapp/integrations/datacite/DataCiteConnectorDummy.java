package com.researchspace.webapp.integrations.datacite;

import com.researchspace.datacite.model.DataCiteDoi;
import lombok.Getter;
import org.apache.commons.lang3.RandomStringUtils;

public class DataCiteConnectorDummy implements DataCiteConnector {

  public static final String DUMMY_VALID_DOI = "10.82316/n1c0-t35t-";
  @Getter public DataCiteDoi doiSentToDatacite;

  @Override
  public DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi) {
    doiSentToDatacite = dataCiteDoi;
    dataCiteDoi.getAttributes().setState("draft");
    dataCiteDoi.setId(DUMMY_VALID_DOI + RandomStringUtils.randomAlphabetic(4));
    return dataCiteDoi;
  }

  @Override
  public boolean deleteDoi(String s) {
    return true;
  }

  @Override
  public DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi) {
    doiSentToDatacite = dataCiteDoi;
    dataCiteDoi.getAttributes().setState("findable");
    return dataCiteDoi;
  }

  @Override
  public DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi) {
    doiSentToDatacite = dataCiteDoi;
    dataCiteDoi.getAttributes().setState("registered");
    return dataCiteDoi;
  }

  @Override
  public void reloadDataCiteClient() {
    ;
  }

  @Override
  public boolean isDataCiteConfiguredAndEnabled() {
    return true;
  }

  @Override
  public boolean testDataCiteConnection() {
    return true;
  }
}
