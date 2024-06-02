package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.api.v1.InventoryIdentifiersApi;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import javax.ws.rs.NotFoundException;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class InventoryIdentifiersApiController extends BaseApiInventoryController
    implements InventoryIdentifiersApi {

  @Autowired private InventoryIdentifierApiManager identifierMgr;

  @Autowired private DataCiteConnector dataCiteConnector;

  @Data
  @NoArgsConstructor
  public static class ApiInventoryIdentifierPost {
    @JsonProperty("parentGlobalId")
    private String parentGlobalId;
  }

  @Override
  public ApiInventoryDOI registerNewIdentifier(
      @RequestBody ApiInventoryIdentifierPost registerPost,
      @RequestAttribute(name = "user") User user) {

    assertDataCiteConnectorEnabled();
    String globalId = registerPost.getParentGlobalId();
    Validate.isTrue(GlobalIdentifier.isValid(globalId), "not a valid global id: " + globalId);
    GlobalIdentifier oid = new GlobalIdentifier(globalId);
    assertUserCanEditInventoryRecord(oid, user);

    ApiInventoryDOI mintedDoi =
        identifierMgr.registerNewIdentifier(oid, user).getIdentifiers().get(0);
    return mintedDoi;
  }

  @Override
  public boolean deleteIdentifier(
      @PathVariable Long identifierId, @RequestAttribute(name = "user") User user) {

    assertDataCiteConnectorEnabled();
    InventoryRecord invRec = retrieveInvRecByIdentifierId(identifierId, user);
    return identifierMgr.deleteIdentifier(invRec.getOid(), user).getIdentifiers().isEmpty();
  }

  @Override
  public ApiInventoryDOI publishIdentifier(
      @PathVariable Long identifierId, @RequestAttribute(name = "user") User user) {

    assertDataCiteConnectorEnabled();
    InventoryRecord invRec = retrieveInvRecByIdentifierId(identifierId, user);
    return identifierMgr.publishIdentifier(invRec.getOid(), user).getIdentifiers().get(0);
  }

  @Override
  public ApiInventoryDOI retractIdentifier(
      @PathVariable Long identifierId, @RequestAttribute(name = "user") User user) {

    assertDataCiteConnectorEnabled();
    InventoryRecord invRec = retrieveInvRecByIdentifierId(identifierId, user);
    return identifierMgr.retractIdentifier(invRec.getOid(), user).getIdentifiers().get(0);
  }

  @Override
  public boolean testDataCiteConnection(User user) {
    return dataCiteConnector.testDataCiteConnection();
  }

  private void assertDataCiteConnectorEnabled() {
    if (dataCiteConnector == null || !dataCiteConnector.isDataCiteConfiguredAndEnabled()) {
      throw new UnsupportedOperationException(
          "IGSN integration is not enabled on this RSpace instance.");
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
