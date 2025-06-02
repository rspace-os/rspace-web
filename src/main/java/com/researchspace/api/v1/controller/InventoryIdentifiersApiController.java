package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.api.v1.InventoryIdentifiersApi;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.util.List;
import javax.naming.InvalidNameException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
public class InventoryIdentifiersApiController extends BaseApiInventoryController
    implements InventoryIdentifiersApi {

  @Autowired private InventoryIdentifierApiManager identifierMgr;

  @Autowired private DataCiteConnector dataCiteConnector;

  private @Autowired SystemPropertyPermissionManager systemPropertyManager;

  @Data
  @NoArgsConstructor
  public static class ApiInventoryIdentifierPost {

    @JsonProperty("parentGlobalId")
    private String parentGlobalId;
  }

  @Override
  public List<ApiInventoryDOI> getUserIdentifiers(
      @RequestParam(value = "state", required = false) String state,
      @RequestParam(value = "isAssociated", required = false) Boolean isAssociated,
      @RequestParam(value = "identifier", required = false) String identifier,
      @RequestAttribute(name = "user") User user)
      throws InvalidNameException {
    assertInventoryAndDataciteEnabled(user);

    return identifierMgr.findIdentifiers(state, isAssociated, identifier, user);
  }

  @Override
  public ApiInventoryDOI registerNewIdentifier(
      @RequestBody ApiInventoryIdentifierPost registerPost,
      @RequestAttribute(name = "user") User user) {

    assertInventoryAndDataciteEnabled(user);
    String globalId = registerPost.getParentGlobalId();
    Validate.isTrue(GlobalIdentifier.isValid(globalId), "not a valid global id: " + globalId);
    GlobalIdentifier oid = new GlobalIdentifier(globalId);
    assertUserCanEditInventoryRecord(oid, user);

    ApiInventoryRecordInfo result = identifierMgr.registerNewIdentifier(oid, user);
    return result.getIdentifiers().get(0);
  }

  @Override
  public List<ApiInventoryDOI> bulkAllocateIdentifiers(
      @PathVariable Integer count, @RequestAttribute(name = "user") User user) {
    assertInventoryAndDataciteEnabled(user);

    Validate.isTrue(
        count > 0,
        "not a valid number to IGSN to allocate: \""
            + count
            + "\""
            + " The number must be greater than 0");
    List<ApiInventoryDOI> result = identifierMgr.registerBulkIdentifiers(count, user);
    if (!count.equals(result.size())) {
      log.error(
          "Requested registration of {} draft IGSNs, but only managed to register {}",
          count,
          result.size());
      throw new InternalServerErrorException(
          "Requested registration of "
              + count
              + " draft IGSNs, but only managed to register "
              + result.size());
    }
    return result;
  }

  @Override
  public ApiInventoryDOI assignIdentifier(
      @PathVariable Long identifierId,
      @RequestBody ApiInventoryIdentifierPost inventoryItem,
      @RequestAttribute(name = "user") User user) {
    assertInventoryAndDataciteEnabled(user);

    String globalId = inventoryItem.getParentGlobalId();
    Validate.isTrue(GlobalIdentifier.isValid(globalId), "not a valid global id: " + globalId);
    Validate.isTrue(identifierId != null, "identifier must not be null");
    GlobalIdentifier inventoryOid = new GlobalIdentifier(globalId);
    assertUserCanEditInventoryRecord(inventoryOid, user);

    ApiInventoryRecordInfo result =
        identifierMgr.assignIdentifier(inventoryOid, identifierId, user);
    return result.getIdentifiers().get(0);
  }

  @Override
  public boolean deleteIdentifier(
      @PathVariable Long identifierId, @RequestAttribute(name = "user") User user) {
    assertInventoryAndDataciteEnabled(user);

    boolean result = false;
    ApiInventoryDOI identifier = identifierMgr.getIdentifierById(identifierId);
    Validate.isTrue(
        identifier.getState().equals("draft"),
        "you can only delete identifiers " + "in \"draft\" status");
    if (identifier.isAssociated()) {
      InventoryRecord invRec = retrieveInvRecByIdentifierId(identifierId, user);
      result =
          identifierMgr
              .deleteAssociatedIdentifier(invRec.getOid(), user)
              .getIdentifiers()
              .isEmpty();
    } else {
      result = identifierMgr.deleteUnassociatedIdentifier(identifier, user);
    }
    return result;
  }

  @Override
  public ApiInventoryDOI publishIdentifier(
      @PathVariable Long identifierId, @RequestAttribute(name = "user") User user) {
    assertInventoryAndDataciteEnabled(user);

    InventoryRecord invRec = retrieveInvRecByIdentifierId(identifierId, user);
    return identifierMgr.publishIdentifier(invRec.getOid(), user).getIdentifiers().get(0);
  }

  @Override
  public ApiInventoryDOI retractIdentifier(
      @PathVariable Long identifierId, @RequestAttribute(name = "user") User user) {
    assertInventoryAndDataciteEnabled(user);

    InventoryRecord invRec = retrieveInvRecByIdentifierId(identifierId, user);
    return identifierMgr.retractIdentifier(invRec.getOid(), user).getIdentifiers().get(0);
  }

  @Override
  public boolean testDataCiteConnection(User user) {
    return dataCiteConnector.testDataCiteConnection();
  }

  private void assertInventoryAndDataciteEnabled(User user){
    assertDataCiteConnectorEnabled();
    assertInventoryAvailable(user);
  }

  private void assertDataCiteConnectorEnabled() {
    if (dataCiteConnector == null || !dataCiteConnector.isDataCiteConfiguredAndEnabled()) {
      throw new UnsupportedOperationException(
          "IGSN integration is not enabled on this RSpace instance.");
    }
  }

  private void assertInventoryAvailable(User user) {
    if(!systemPropertyManager.isPropertyAllowed(user, SystemPropertyName.INVENTORY_AVAILABLE)){
      throw new UnsupportedOperationException(
          "Inventory is not enabled on this RSpace instance.");
    }
  }

  private InventoryRecord retrieveInvRecByIdentifierId(Long identifierId, User user) {
    InventoryRecord invRec = identifierMgr.getInventoryRecordByIdentifierId(identifierId);
    if (invRec == null) {
      throw new NotFoundException(createNotFoundMessage("identifier", identifierId));
    }
    assertUserCanEditInventoryRecord(invRec.getOid(), user);
    return invRec;
  }


}
