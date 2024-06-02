package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;

/** Handles API actions around inventory DOI identifiers */
public interface InventoryIdentifierApiManager {

  InventoryRecord getInventoryRecordByIdentifierId(Long id);

  ApiInventoryRecordInfo findPublishedItemVersionByPublicLink(String publicLink);

  ApiInventoryRecordInfo registerNewIdentifier(GlobalIdentifier invRecOid, User user);

  ApiInventoryRecordInfo deleteIdentifier(GlobalIdentifier invRecOid, User user);

  ApiInventoryRecordInfo publishIdentifier(GlobalIdentifier invRecOid, User user);

  ApiInventoryRecordInfo retractIdentifier(GlobalIdentifier invRecOid, User user);

  /* for testing */
  void setDataCiteConnector(DataCiteConnector dataCiteConnector);
}
