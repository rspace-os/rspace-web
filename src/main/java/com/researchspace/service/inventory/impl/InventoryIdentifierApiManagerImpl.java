package com.researchspace.service.inventory.impl;

import static org.apache.commons.lang3.StringUtils.*;

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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
  public List<ApiInventoryDOI> findIdentifiersByStateAndOwner(
      String state, User owner, Boolean isAssociated) {
    return doiDao.getActiveIdentifiersByOwner(owner).stream()
        .filter(r -> (isAssociated == null) || isAssociated.equals(r.isAssociated()))
        .filter(r -> isBlank(state) || state.equals(r.getState()))
        .map(ApiInventoryDOI::new)
        .collect(Collectors.toList());
  }

  @Override
  public ApiInventoryRecordInfo registerNewIdentifier(GlobalIdentifier invRecOid, User user) {
    InventoryRecord invRec = invRecRetriever.getInvRecordByGlobalId(invRecOid);
    if (!invRec.getActiveIdentifiers().isEmpty()) {
      throw new IllegalArgumentException(
          "record " + invRecOid.toString() + " already has an identifier");
    }
    return updateInventoryRecordWithDoiUpdate(user, invRec, createUpdateWithNewDoi(invRec, user));
  }

  @Override
  public List<ApiInventoryDOI> registerBulkIdentifiers(Integer igsnsToAllocate, User user) {
    List<ApiInventoryDOI> result = new LinkedList<>();
    ApiInventoryDOI currentDoi;
    for (int i = 0; i < igsnsToAllocate; i++) {
      try {
        currentDoi = createNewDoi(user);
        DigitalObjectIdentifier dbObj = apiIdentifiersHelper.createDoiToSave(currentDoi, user);
        dbObj = doiDao.save(dbObj);

        result.add(new ApiInventoryDOI(dbObj));
      } catch (Exception ex) {
        log.warn("It was not possible to allocate IGSN: {}", ex.getMessage(), ex);
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
    sample.setId(invRec.getId());
    sample.getIdentifiers().add(identifier);
    sample.setTags(null); // skip tags update
    return sample;
  }

  private ApiSubSample getApiSubSampleUpdateWithIdentifier(
      InventoryRecord invRec, ApiInventoryDOI identifier) {
    ApiSubSample subSample = new ApiSubSample();
    subSample.setId(invRec.getId());
    subSample.getIdentifiers().add(identifier);
    subSample.setTags(null); // skip tags update
    return subSample;
  }

  private ApiContainer getApiContainerUpdateWithIdentifier(
      InventoryRecord invRec, ApiInventoryDOI identifier) {
    ApiContainer container = new ApiContainer();
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

  /* for testing */
  @Override
  @Autowired
  public void setDataCiteConnector(DataCiteConnector dataCiteConnector) {
    this.dataCiteConnector = dataCiteConnector;
  }
}
