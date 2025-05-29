package com.researchspace.service.inventory.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.RoRService;
import com.researchspace.service.inventory.ApiIdentifiersHelper;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.InventoryRecordRetriever;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.time.Year;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.naming.InvalidNameException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.UrlValidator;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service("inventoryIdentifierApiManager")
public class InventoryIdentifierApiManagerImpl implements InventoryIdentifierApiManager {

  private @Autowired SampleApiManager sampleApiMgr;
  private @Autowired SubSampleApiManager subSampleApiMgr;
  private @Autowired ContainerApiManager containerApiMgr;
  private @Autowired DigitalObjectIdentifierDao doiDao;
  private @Autowired ApiIdentifiersHelper apiIdentifiersHelper;

  @Autowired private RoRService rorService;

  private @Autowired InventoryRecordRetriever invRecRetriever;
  private @Autowired IPropertyHolder properties;

  private DataCiteConnector dataCiteConnector;

  private String dataCitePublicUrlPrefix = "https://doi.org/";

  @Override
  public InventoryRecord getInventoryRecordByIdentifierId(Long doiId) {
    Optional<DigitalObjectIdentifier> doiOptional = doiDao.getSafeNull(doiId);
    if (doiOptional.isEmpty()) {
      return null;
    }
    return doiOptional.get().getInventoryRecord();
  }

  @Override
  public ApiInventoryDOI getIdentifierById(Long id) {
    return new ApiInventoryDOI(doiDao.get(id));
  }

  @Override
  public ApiInventoryRecordInfo findPublishedItemVersionByPublicLink(String publicLink) {
    Optional<DigitalObjectIdentifier> doiOptional =
        doiDao.getLastPublishedIdentifierByPublicLink(publicLink);
    if (doiOptional.isEmpty()) {
      return null;
    }
    return ApiInventoryRecordInfo.fromInventoryRecordToFullApiRecord(
        doiOptional.get().getInventoryRecord());
  }

  @Override
  public List<ApiInventoryDOI> findIdentifiers(
      String state, Boolean isAssociated, String identifier, User owner)
      throws InvalidNameException {
    String finalIdentifier;
    if (isNotBlank(identifier) && (isValidURL(identifier) || isValidURL("https://" + identifier))
        && isValidIdentifier(identifier)) {
      finalIdentifier = getIdentifierSuffix(identifier);
    } else {
      finalIdentifier = identifier;
    }
    return doiDao.getActiveIdentifiersByOwner(owner).stream()
        .filter(r -> isBlank(finalIdentifier) || r.getIdentifier().contains(finalIdentifier))
        .filter(r -> (isAssociated == null) || isAssociated.equals(r.isAssociated()))
        .filter(r -> isBlank(state) || state.equals(r.getState()))
        .map(ApiInventoryDOI::new)
        .collect(Collectors.toList());
  }

  private String getIdentifierSuffix(String identifierUrl) throws InvalidNameException {
    return "10." + identifierUrl.split("/10.")[1];
  }

  private boolean isValidIdentifier(String identifier) throws InvalidNameException {
    // DOI always starts with "10."
    // as per specs on https://www.doi.org/#:~:text=TRY%20RESOLVING%20A%20DOI
    if (!identifier.contains("10.")) {
      throw new IllegalArgumentException(
          "Identifier [" + identifier + "] it is not recognized as valid DOI");
    }
    return true;
  }

  private boolean isValidURL(String url) {
    UrlValidator validator = new UrlValidator();
    return validator.isValid(url);
  }

  @Override
  public ApiInventoryRecordInfo registerNewIdentifier(GlobalIdentifier invRecOid, User user) {
    InventoryRecord invRec = getInventoryRecordIfNotAlreadyAssociated(invRecOid);
    return updateInventoryRecordWithDoiUpdate(user, invRec, createUpdateWithNewDoi(invRec, user));
  }

  @Override
  public ApiInventoryRecordInfo assignIdentifier(
      GlobalIdentifier inventoryOid, Long identifierId, User user) {
    InventoryRecord inventoryItem = getInventoryRecordIfNotAlreadyAssociated(inventoryOid);

    if (!inventoryItem.getOwner().equals(user)) {
      throw new IllegalArgumentException(
          "You can only assign an identifier that is owned by the current logged user");
    }
    DigitalObjectIdentifier identifierToAssign = doiDao.get(identifierId);
    if (!identifierToAssign.canBeAssigned()) {
      throw new IllegalArgumentException(
          "You can only assign an active unassigned identifier in \"draft\" state");
    }

    return updateInventoryRecordWithDoiUpdate(
        user, inventoryItem, assignUpdateWithNewDoi(inventoryItem, identifierToAssign));
  }

  @Override
  public List<ApiInventoryDOI> registerBulkIdentifiers(Integer igsnsToAllocate, User user) {
    List<ApiInventoryDOI> result = new LinkedList<>();
    ApiInventoryDOI currentDoi;
    for (int i = 0; i < igsnsToAllocate; i++) {
      try {
        currentDoi = createNewDoi(user);
        DigitalObjectIdentifier dbObj = apiIdentifiersHelper.createDoiToSave(currentDoi, user);
        log.info("New IGSN allocated: {}", dbObj.getIdentifier());

        dbObj = doiDao.save(dbObj);
        result.add(new ApiInventoryDOI(dbObj));
      } catch (DataCiteConnectionException dataciteEx) {
        log.error(
            "It was not possible to allocate IGSN: {}. ", dataciteEx.getMessage(), dataciteEx);
        if (i == 0) { // if it happens during the first iteration
          throw dataciteEx; // then stop the loop because it will happen for each of the items
        }
      } catch (Exception ex) {
        log.warn("It was not possible to allocate IGSN: {}. ", ex.getMessage(), ex);
      }
    }
    return result;
  }

  private ApiInventoryRecordInfo updateInventoryRecordWithDoiUpdate(
      User user, InventoryRecord invRec, ApiInventoryDOI doiUpdate) {
    if (invRec.isSample()) {
      ApiSample sampleUpdate = getApiSampleUpdateWithIdentifier(invRec, doiUpdate);
      return sampleApiMgr.updateApiSample(sampleUpdate, user);
    }
    if (invRec.isSubSample()) {
      ApiSubSample subSampleUpdate = getApiSubSampleUpdateWithIdentifier(invRec, doiUpdate);
      return subSampleApiMgr.updateApiSubSample(subSampleUpdate, user);
    }
    if (invRec.isContainer()) {
      ApiContainer containerUpdate = getApiContainerUpdateWithIdentifier(invRec, doiUpdate);
      return containerApiMgr.updateApiContainer(containerUpdate, user);
    }
    throw new IllegalArgumentException("unsupported type for minting: " + invRec.getType());
  }

  @Override
  public ApiInventoryRecordInfo deleteAssociatedIdentifier(GlobalIdentifier invRecOid, User user) {
    InventoryRecord invRec = invRecRetriever.getInvRecordByGlobalId(invRecOid);
    if (invRec.getActiveIdentifiers().isEmpty()) {
      throw new IllegalArgumentException(
          "record " + invRecOid.toString() + " has no identifier to delete");
    }
    return updateInventoryRecordWithDoiUpdate(
        user, invRec, createUpdateWithDeleteDoi(invRec, user));
  }

  @Override
  public boolean deleteUnassociatedIdentifier(ApiInventoryDOI identifier, User user) {
    DigitalObjectIdentifier doi = doiDao.get(identifier.getId());
    doi.setDeleted(true);
    doi = doiDao.save(doi);
    return deleteFromDatacite(doi);
  }

  @Override
  public ApiInventoryRecordInfo publishIdentifier(GlobalIdentifier invRecOid, User user) {

    InventoryRecord invRec = invRecRetriever.getInvRecordByGlobalId(invRecOid);
    if (invRec.getActiveIdentifiers().isEmpty()) {
      throw new IllegalArgumentException(
          "record " + invRecOid.toString() + " has no identifier to publish");
    }
    return updateInventoryRecordWithDoiUpdate(
        user, invRec, createUpdateWithPublishedDoi(invRec, user));
  }

  @Override
  public ApiInventoryRecordInfo retractIdentifier(GlobalIdentifier invRecOid, User user) {

    InventoryRecord invRec = invRecRetriever.getInvRecordByGlobalId(invRecOid);
    if (invRec.getActiveIdentifiers().isEmpty()) {
      throw new IllegalArgumentException(
          "record " + invRecOid.toString() + " has no identifier to publish");
    }
    return updateInventoryRecordWithDoiUpdate(
        user, invRec, createUpdateWithRetractedDoi(invRec, user));
  }

  private ApiSample getApiSampleUpdateWithIdentifier(
      InventoryRecord invRec, ApiInventoryDOI identifier) {
    ApiSample sample = new ApiSample();
    sample.setName(invRec.getName());
    sample.setId(invRec.getId());
    sample.getIdentifiers().add(identifier);
    sample.setTags(null); // skip tags update
    return sample;
  }

  private ApiSubSample getApiSubSampleUpdateWithIdentifier(
      InventoryRecord invRec, ApiInventoryDOI identifier) {
    ApiSubSample subSample = new ApiSubSample();
    subSample.setName(invRec.getName());
    subSample.setId(invRec.getId());
    subSample.getIdentifiers().add(identifier);
    subSample.setTags(null); // skip tags update
    return subSample;
  }

  private ApiContainer getApiContainerUpdateWithIdentifier(
      InventoryRecord invRec, ApiInventoryDOI identifier) {
    ApiContainer container = new ApiContainer();
    container.setName(invRec.getName());
    container.setId(invRec.getId());
    container.getIdentifiers().add(identifier);
    container.setTags(null);
    return container;
  }

  @SneakyThrows
  private ApiInventoryDOI createNewDoi(User user) {
    DataCiteDoi createdDoi;
    try {
      createdDoi = dataCiteConnector.registerDoi(new DataCiteDoi());
    } catch (DataCiteConnectionException dcException) {
      throw new DataCiteConnectionException(
          "Error when registering new DOI with DataCite. "
              + "If the problem persists, please contact your System Admin",
          dcException);
    }
    if (createdDoi == null || !"draft".equals(createdDoi.getAttributes().getState())) {
      throw new IllegalStateException("DataCite registration failed");
    }

    ApiInventoryDOI newDoi = new ApiInventoryDOI(user, createdDoi);
    newDoi.setRegisterIdentifierRequest(true);
    newDoi.setCreatorName(user.getFullName());
    newDoi.setCreatorType("Personal");
    newDoi.setPublisher(properties.getCustomerName());
    newDoi.setPublicationYear(Year.now().getValue());
    newDoi.setResourceType("Material Sample");
    newDoi.setResourceTypeGeneral("PhysicalObject");
    return newDoi;
  }

  private ApiInventoryDOI updateNewAssociatedDoi(InventoryRecord invRec, ApiInventoryDOI doi) {
    doi.setTitle(invRec.getName());
    doi.setAssociatedGlobalId(invRec.getGlobalIdentifier());
    return doi;
  }

  private ApiInventoryDOI createUpdateWithNewDoi(InventoryRecord invRec, User user) {
    ApiInventoryDOI newDoi = createNewDoi(user);
    return updateNewAssociatedDoi(invRec, newDoi);
  }

  private ApiInventoryDOI assignUpdateWithNewDoi(
      InventoryRecord inventoryItem, DigitalObjectIdentifier identifierToAssign) {
    ApiInventoryDOI newDoi = new ApiInventoryDOI(identifierToAssign);
    newDoi.setAssignIdentifierRequest(true);
    return updateNewAssociatedDoi(inventoryItem, newDoi);
  }

  @SneakyThrows
  private ApiInventoryDOI createUpdateWithDeleteDoi(InventoryRecord invRec, User user) {

    DigitalObjectIdentifier doi = invRec.getActiveIdentifiers().get(0);
    deleteFromDatacite(doi);

    ApiInventoryDOI deleteDoi = new ApiInventoryDOI();
    deleteDoi.setId(invRec.getActiveIdentifiers().get(0).getId());
    deleteDoi.setDeleteIdentifierRequest(true);
    return deleteDoi;
  }

  private boolean deleteFromDatacite(DigitalObjectIdentifier doi) {
    boolean dataCiteDeleteResult;
    try {
      dataCiteDeleteResult = dataCiteConnector.deleteDoi(doi.getIdentifier());
    } catch (DataCiteConnectionException dcException) {
      log.error("Error when deleting the DOI from DataCite: ", dcException.getCause());
      throw new DataCiteConnectionException(
          "Error when deleting the DOI from DataCite. "
              + "If the problem persists, please contact your System Admin",
          dcException);
    }
    if (!dataCiteDeleteResult) {
      throw new IllegalStateException("DataCite delete failed");
    }
    return dataCiteDeleteResult;
  }

  @SneakyThrows
  private ApiInventoryDOI createUpdateWithPublishedDoi(InventoryRecord invRec, User user) {

    String rorAffiliationID = rorService.getSystemRoRValue();
    String rorAffiliationName = rorService.getRorNameForSystemRoRValue();
    DigitalObjectIdentifier doi = invRec.getActiveIdentifiers().get(0);
    ApiInventoryDOI actualdoi = new ApiInventoryDOI(doi);
    actualdoi.setCreatorAffiliation(rorAffiliationName);
    actualdoi.setCreatorAffiliationIdentifier(rorAffiliationID);
    DataCiteDoi doiToPublish = actualdoi.convertToDataCiteDoi();
    DataCiteDoi publishResult;
    try {
      publishResult = dataCiteConnector.publishDoi(doiToPublish);
    } catch (DataCiteConnectionException dcException) {
      throw new DataCiteConnectionException(
          "Error when publishing the DOI in DataCite. "
              + "If the problem persists, please contact your System Admin",
          dcException);
    }
    if (publishResult == null || !"findable".equals(publishResult.getAttributes().getState())) {
      throw new IllegalStateException("DataCite publish failed");
    }

    ApiInventoryDOI publishDoi = new ApiInventoryDOI();
    publishDoi.setId(invRec.getActiveIdentifiers().get(0).getId());
    publishDoi.setState(publishResult.getAttributes().getState());
    publishDoi.setUrl(publishResult.getAttributes().getUrl());
    publishDoi.setPublicUrl(dataCitePublicUrlPrefix + doi.getIdentifier());
    publishDoi.setCreatorAffiliation(rorAffiliationName);
    publishDoi.setCreatorAffiliationIdentifier(rorAffiliationID);
    return publishDoi;
  }

  @SneakyThrows
  private ApiInventoryDOI createUpdateWithRetractedDoi(InventoryRecord invRec, User user) {
    String rorAffiliationID = rorService.getSystemRoRValue();
    String rorAffiliationName = rorService.getRorNameForSystemRoRValue();
    DigitalObjectIdentifier doi = invRec.getActiveIdentifiers().get(0);
    ApiInventoryDOI actualdoi = new ApiInventoryDOI(doi);
    actualdoi.setCreatorAffiliation(rorAffiliationName);
    actualdoi.setCreatorAffiliationIdentifier(rorAffiliationID);
    DataCiteDoi doiToRetract = actualdoi.convertToDataCiteDoi();

    DataCiteDoi retractResult;
    try {
      retractResult = dataCiteConnector.retractDoi(doiToRetract);
    } catch (DataCiteConnectionException dcException) {
      throw new DataCiteConnectionException(
          "Error when retracting the DOI in DataCite. "
              + "If the problem persists, please contact your System Admin",
          dcException);
    }
    if (retractResult == null || !"registered".equals(retractResult.getAttributes().getState())) {
      throw new IllegalStateException("datacite retract failed");
    }

    ApiInventoryDOI publishDoi = new ApiInventoryDOI();
    publishDoi.setId(invRec.getActiveIdentifiers().get(0).getId());
    publishDoi.setState(retractResult.getAttributes().getState());
    publishDoi.setCreatorAffiliation(rorAffiliationName);
    publishDoi.setCreatorAffiliationIdentifier(rorAffiliationID);
    return publishDoi;
  }

  @NotNull
  private InventoryRecord getInventoryRecordIfNotAlreadyAssociated(GlobalIdentifier invRecOid) {
    InventoryRecord invRec = invRecRetriever.getInvRecordByGlobalId(invRecOid);
    if (!invRec.getActiveIdentifiers().isEmpty()) {
      throw new IllegalArgumentException(
          "Inventory Item [" + invRecOid.toString() + "] has got already an identifier");
    }
    return invRec;
  }

  /* for testing */
  @Override
  @Autowired
  public void setDataCiteConnector(DataCiteConnector dataCiteConnector) {
    this.dataCiteConnector = dataCiteConnector;
  }
}
