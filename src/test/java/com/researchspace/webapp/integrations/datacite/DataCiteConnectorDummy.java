package com.researchspace.webapp.integrations.datacite;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.datacite.model.DataCiteDoi;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import org.apache.commons.lang3.RandomStringUtils;

public class DataCiteConnectorDummy implements DataCiteConnector {

  public static final String DUMMY_VALID_DOI = "10.82316/n1c0-t35t-";
  @Getter public DataCiteDoi doiSentToDatacite;
  @Getter private InventorySettingType lastSettingTypeUsed;

  private final Map<InventorySettingType, Boolean> enabled =
      new EnumMap<>(InventorySettingType.class);

  public void setEnabled(InventorySettingType settingType, boolean isEnabled) {
    enabled.put(settingType, isEnabled);
  }

  @Override
  public DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType) {
    lastSettingTypeUsed = settingType;
    doiSentToDatacite = dataCiteDoi;
    dataCiteDoi.getAttributes().setState("draft");
    dataCiteDoi.setId(DUMMY_VALID_DOI + RandomStringUtils.randomAlphabetic(4));
    return dataCiteDoi;
  }

  @Override
  public boolean deleteDoi(String s, InventorySettingType settingType) {
    lastSettingTypeUsed = settingType;
    return true;
  }

  @Override
  public DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType) {
    lastSettingTypeUsed = settingType;
    doiSentToDatacite = dataCiteDoi;
    dataCiteDoi.getAttributes().setState("findable");
    return dataCiteDoi;
  }

  @Override
  public DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType) {
    lastSettingTypeUsed = settingType;
    doiSentToDatacite = dataCiteDoi;
    dataCiteDoi.getAttributes().setState("registered");
    return dataCiteDoi;
  }

  @Override
  public void reloadDataCiteClient() {
    ;
  }

  @Override
  public boolean isDataCiteConfiguredAndEnabled(InventorySettingType settingType) {
    return enabled.getOrDefault(settingType, true);
  }

  @Override
  public boolean testDataCiteConnection(InventorySettingType settingType) {
    return true;
  }
}
