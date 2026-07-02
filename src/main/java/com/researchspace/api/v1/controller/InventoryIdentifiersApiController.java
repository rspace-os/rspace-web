package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.api.v1.InventoryIdentifiersApi;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import javax.naming.InvalidNameException;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
public class InventoryIdentifiersApiController extends BaseApiInventoryController
    implements InventoryIdentifiersApi {

  @Autowired private InventoryIdentifierApiManager identifierMgr;
  @Autowired private DataCiteConnector dataCiteConnector;
  @Autowired private B2instConnector b2instConnector;
  @Autowired private ApiAvailabilityHandler apiHandler;

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
    apiHandler.assertInventoryAndDataciteEnabled(user);
    return identifierMgr.findIdentifiers(state, isAssociated, identifier, true, user);
  }

  @Override
  public ApiInventoryDOI registerNewIdentifier(
      @RequestBody ApiInventoryIdentifierPost registerPost,
      @RequestAttribute(name = "user") User user) {
    String globalId = registerPost.getParentGlobalId();
    Validate.isTrue(GlobalIdentifier.isValid(globalId), "not a valid global id: " + globalId);
    GlobalIdentifier oid = new GlobalIdentifier(globalId);
    apiHandler.assertInventoryAndIdentifierTypeEnabled(user, settingTypeForOid(oid));
    assertUserCanEditInventoryRecord(oid, user);

    ApiInventoryRecordInfo result = identifierMgr.registerNewIdentifier(oid, user);
    return result.getIdentifiers().get(0);
  }

  @Override
  public List<ApiInventoryDOI> bulkAllocateIdentifiers(
      @PathVariable Integer count, @RequestAttribute(name = "user") User user) {
    apiHandler.assertInventoryAndDataciteEnabled(user);

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
    String globalId = inventoryItem.getParentGlobalId();
    Validate.isTrue(GlobalIdentifier.isValid(globalId), "not a valid global id: " + globalId);
    Validate.isTrue(identifierId != null, "identifier must not be null");
    GlobalIdentifier inventoryOid = new GlobalIdentifier(globalId);
    apiHandler.assertInventoryAndIdentifierTypeEnabled(user, settingTypeForOid(inventoryOid));
    assertUserCanEditInventoryRecord(inventoryOid, user);

    ApiInventoryRecordInfo result =
        identifierMgr.assignIdentifier(inventoryOid, identifierId, user);
    return result.getIdentifiers().get(0);
  }

  @Override
  public boolean deleteIdentifier(
      @PathVariable Long identifierId, @RequestAttribute(name = "user") User user) {
    boolean result = false;
    ApiInventoryDOI identifier = getIdentifierByIdOr404(identifierId);
    apiHandler.assertInventoryAndIdentifierTypeEnabled(
        user, settingTypeForDoiType(identifier.getDoiType()));
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
    assertInventoryAndIdentifierEnabledForId(identifierId, user);

    InventoryRecord invRec = retrieveInvRecByIdentifierId(identifierId, user);
    return identifierMgr.publishIdentifier(invRec.getOid(), user).getIdentifiers().get(0);
  }

  @Override
  public ApiInventoryDOI retractIdentifier(
      @PathVariable Long identifierId, @RequestAttribute(name = "user") User user) {
    assertInventoryAndIdentifierEnabledForId(identifierId, user);

    InventoryRecord invRec = retrieveInvRecByIdentifierId(identifierId, user);
    return identifierMgr.retractIdentifier(invRec.getOid(), user).getIdentifiers().get(0);
  }

  @Override
  public boolean testIgsnConnection(User user) {
    return dataCiteConnector.testDataCiteConnection(InventorySettingType.IGSN);
  }

  @Override
  public boolean testPidinstConnection(User user) {
    // route to whichever PIDINST provider is currently enabled
    if (b2instConnector.isConfiguredAndEnabled()) {
      return b2instConnector.testConnection();
    }
    return dataCiteConnector.testDataCiteConnection(InventorySettingType.PIDINST);
  }

  private InventoryRecord retrieveInvRecByIdentifierId(Long identifierId, User user) {
    InventoryRecord invRec = identifierMgr.getInventoryRecordByIdentifierId(identifierId);
    if (invRec == null) {
      throw new NotFoundException(createNotFoundMessage("identifier", identifierId));
    }
    assertUserCanEditInventoryRecord(invRec.getOid(), user);
    return invRec;
  }

  private void assertInventoryAndIdentifierEnabledForId(Long identifierId, User user) {
    ApiInventoryDOI identifier = getIdentifierByIdOr404(identifierId);
    apiHandler.assertInventoryAndIdentifierTypeEnabled(
        user, settingTypeForDoiType(identifier.getDoiType()));
  }

  /**
   * Loads the identifier, translating the DAO's {@link ObjectRetrievalFailureException} for an
   * unknown id into a {@link NotFoundException} so the API returns 404 rather than 500. {@code
   * getIdentifierById} delegates to {@code GenericDaoHibernate#get}, which throws (never returns
   * null) when the id does not exist.
   */
  private ApiInventoryDOI getIdentifierByIdOr404(Long identifierId) {
    try {
      return identifierMgr.getIdentifierById(identifierId);
    } catch (ObjectRetrievalFailureException e) {
      throw new NotFoundException(createNotFoundMessage("identifier", identifierId));
    }
  }

  private InventorySettingType settingTypeForOid(GlobalIdentifier oid) {
    return GlobalIdPrefix.IN.equals(oid.getPrefix())
        ? InventorySettingType.PIDINST
        : InventorySettingType.IGSN;
  }

  private InventorySettingType settingTypeForDoiType(String doiType) {
    boolean pidinst =
        IdentifierType.PIDINST_DATACITE.name().equals(doiType)
            || IdentifierType.PIDINST_B2INST.name().equals(doiType);
    return pidinst ? InventorySettingType.PIDINST : InventorySettingType.IGSN;
  }
}
