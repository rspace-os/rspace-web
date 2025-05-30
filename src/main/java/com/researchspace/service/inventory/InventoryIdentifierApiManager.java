package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.util.List;
import javax.naming.InvalidNameException;

/** Handles API actions around inventory DOI identifiers */
public interface InventoryIdentifierApiManager {

  InventoryRecord getInventoryRecordByIdentifierId(Long id);

  ApiInventoryDOI getIdentifierById(Long id);

  ApiInventoryRecordInfo findPublishedItemVersionByPublicLink(String publicLink);

  List<ApiInventoryDOI> findIdentifiers(
      String state,
      Boolean isAssociated,
      String identifier,
      boolean allowSubstringIdentifier,
      User owner)
      throws InvalidNameException;

  ApiInventoryRecordInfo registerNewIdentifier(GlobalIdentifier invRecOid, User user);

  ApiInventoryRecordInfo assignIdentifier(
      GlobalIdentifier inventoryOid, Long identifierId, User user);

  List<ApiInventoryDOI> registerBulkIdentifiers(Integer igsnsToAllocate, User user);

  ApiInventoryRecordInfo deleteAssociatedIdentifier(GlobalIdentifier invRecOid, User user);

  boolean deleteUnassociatedIdentifier(ApiInventoryDOI identifier, User user);

  ApiInventoryRecordInfo publishIdentifier(GlobalIdentifier invRecOid, User user);

  ApiInventoryRecordInfo retractIdentifier(GlobalIdentifier invRecOid, User user);

  /* for testing */
  void setDataCiteConnector(DataCiteConnector dataCiteConnector);
}
