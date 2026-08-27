package com.researchspace.service.inventory.impl;

import static com.researchspace.model.inventory.field.InventoryIdentifierField.DOI_URL_PREFIX;
import static com.researchspace.model.inventory.field.InventoryIdentifierField.isValidDOI;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRequestResponse;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RoRService;
import com.researchspace.service.inventory.ApiIdentifiersHelper;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.InventoryRecordRetriever;
import com.researchspace.service.inventory.InventoryUrls;
import com.researchspace.service.inventory.RspaceToExternalProviderAdapter;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.webapp.integrations.b2inst.B2instConnectionException;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.time.Year;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.naming.InvalidNameException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
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
  private @Autowired InstrumentEntityApiManager instrumentApiMgr;
  private @Autowired DigitalObjectIdentifierDao doiDao;
  private @Autowired ApiIdentifiersHelper apiIdentifiersHelper;
  private @Autowired MessageSourceUtils messages;

  @Autowired private RoRService rorService;

  private @Autowired InventoryRecordRetriever invRecRetriever;
  private @Autowired IPropertyHolder properties;

  private DataCiteConnector dataCiteConnector;
  @Autowired private B2instConnector b2instConnector;
  @Autowired private RspaceToExternalProviderAdapter rspaceToExternalProviderAdapter;

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
      String state, Boolean isAssociated, String identifier, boolean allowSubstring, User owner)
      throws InvalidNameException {
    String finalIdentifier;
    if (isNotBlank(identifier)
        && (isValidURL(identifier) || isValidURL("https://" + identifier))
        && isValidDOI(identifier)) {
      finalIdentifier = getIdentifierSuffix(identifier);
    } else {
      finalIdentifier = identifier;
    }

    return doiDao.getActiveIdentifiersByOwner(owner).stream()
        .filter(r -> IdentifierType.IGSN_DATACITE.equals(r.getType()))
        .filter(
            r -> isBlank(finalIdentifier) || matchIdentifier(r, finalIdentifier, allowSubstring))
        .filter(r -> (isAssociated == null) || isAssociated.equals(r.isAssociated()))
        .filter(r -> isBlank(state) || state.equals(r.getState()))
        .map(ApiInventoryDOI::new)
        .collect(Collectors.toList());
  }

  private static boolean matchIdentifier(
      DigitalObjectIdentifier r, String finalIdentifier, boolean allowSubstringIdentifier) {
    if (allowSubstringIdentifier) {
      return r.getIdentifier().contains(finalIdentifier);
    } else {
      return r.getIdentifier().equals(finalIdentifier);
    }
  }

  private String getIdentifierSuffix(String identifierUrl) throws InvalidNameException {
    return "10." + identifierUrl.split("/10.")[1];
  }

  private boolean isValidURL(String url) {
    UrlValidator validator = new UrlValidator();
    return validator.isValid(url);
  }

  @Override
  public ApiInventoryRecordInfo registerNewIdentifier(GlobalIdentifier invRecOid, User user) {
    InventoryRecord invRec = getInventoryRecordIfNotAlreadyAssociated(invRecOid);
    // resolve the setting type and run all checks before registering anything with DataCite,
    // so no draft DOI is leaked for unsupported or disabled configurations
    InventorySettingType settingType = settingTypeFor(invRec);
    if (InventorySettingType.PIDINST.equals(settingType)) {
      assertPidinstRegistrationSupported();
    }
    return updateInventoryRecordWithDoiUpdate(
        user, invRec, createUpdateWithNewDoi(invRec, user, settingType));
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
    if (!settingTypeFor(inventoryItem).equals(settingTypeFor(identifierToAssign.getType()))) {
      throw new IllegalArgumentException(
          messages.getMessage(
              "errors.inventory.identifier.assignTypeMismatch",
              new Object[] {identifierToAssign.getType(), inventoryOid}));
    }

    return updateInventoryRecordWithDoiUpdate(
        user, inventoryItem, assignUpdateWithNewDoi(inventoryItem, identifierToAssign));
  }

  private InventorySettingType settingTypeFor(InventoryRecord invRec) {
    if (invRec.isSample() || invRec.isSubSample() || invRec.isContainer()) {
      return InventorySettingType.IGSN;
    }
    if (invRec.isInstrument()) {
      return InventorySettingType.PIDINST;
    }
    throw new IllegalArgumentException(
        messages.getMessage(
            "errors.inventory.identifier.mintingUnsupportedType", new Object[] {invRec.getType()}));
  }

  private InventorySettingType settingTypeFor(IdentifierType identifierType) {
    if (identifierType == null) {
      // identifiers persisted before the type column was populated load with a null type;
      // they predate PIDINST, so default to IGSN (matches DigitalObjectIdentifier's own default)
      // instead of letting the switch below throw a NullPointerException.
      return InventorySettingType.IGSN;
    }
    switch (identifierType) {
      case IGSN_DATACITE:
        return InventorySettingType.IGSN;
      case PIDINST_DATACITE:
      case PIDINST_B2INST:
        return InventorySettingType.PIDINST;
      default:
        throw new UnsupportedOperationException(
            messages.getMessage(
                "errors.inventory.identifier.typeUnsupported", new Object[] {identifierType}));
    }
  }

  private boolean isB2inst(IdentifierType identifierType) {
    return IdentifierType.PIDINST_B2INST.equals(identifierType);
  }

  private void assertPidinstRegistrationSupported() {
    boolean dataciteEnabled =
        dataCiteConnector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST);
    boolean b2instEnabled = b2instConnector.isConfiguredAndEnabled();
    if (!dataciteEnabled && !b2instEnabled) {
      throw new UnsupportedOperationException(
          messages.getMessage(
              "errors.inventory.identifier.integrationNotEnabled",
              new Object[] {InventorySettingType.PIDINST}));
    }
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
    if (invRec.isInstrument()) {
      ApiInstrument instrumentUpdate = getApiInstrumentUpdateWithIdentifier(invRec, doiUpdate);
      return instrumentApiMgr.updateApiInstrument(instrumentUpdate, user);
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
    if (!user.equals(doi.getOwner())) {
      throw new IllegalArgumentException(
          messages.getMessage("errors.inventory.identifier.deleteNotOwner"));
    }
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

  private ApiInstrument getApiInstrumentUpdateWithIdentifier(
      InventoryRecord invRec, ApiInventoryDOI identifier) {
    ApiInstrument instrument = new ApiInstrument();
    instrument.setName(invRec.getName());
    instrument.setId(invRec.getId());
    instrument.getIdentifiers().add(identifier);
    instrument.setTags(null); // skip tags update
    seedLandingPageForNewPidinst(instrument, invRec, identifier);
    return instrument;
  }

  /**
   * Puts the identifier's public landing page into the instrument's own Landing page field, so the
   * field shows the address that was registered instead of drifting from it (RSDEV-1254, ADR 0006
   * item 4). Rides this same update, so the value goes through the field's ordinary validation,
   * Envers revision and save rather than a second write path of its own.
   *
   * <p>Only for a brand-new PIDINST registration, and only when the field holds nothing the user
   * typed: a value of theirs is what PIDINST's LandingPage is for and is registered as-is, so
   * overwriting it would both destroy their input and register something the field never showed. A
   * landing page the retired auto-fill wrote counts as untyped, so registering replaces it.
   *
   * <p>Applied on the DataCite PIDINST path too, even though DataCite has no LandingPage property
   * and so transmits it nowhere: the same user action should leave the instrument looking the same
   * whichever provider a deployment has enabled.
   *
   * <p>Called while building the post-registration update, so the provider has already accepted: a
   * failed registration never reaches here and leaves the field as it was.
   */
  private void seedLandingPageForNewPidinst(
      ApiInstrument update, InventoryRecord invRec, ApiInventoryDOI identifier) {
    if (!identifier.isRegisterIdentifierRequest() || !invRec.isInstrument()) {
      return;
    }
    IdentifierType type = EnumUtils.getEnum(IdentifierType.class, identifier.getDoiType());
    if (!InventorySettingType.PIDINST.equals(settingTypeFor(type))) {
      return;
    }
    InstrumentEntity source = (InstrumentEntity) invRec;
    Optional<InventoryEntityField> field = PidinstFields.landingPage(source);
    if (field.isEmpty() || PidinstFields.userTypedLandingPage(source).isPresent()) {
      return;
    }
    /*
     * Guarded by the same rule the adapter applies to what it registers: the address is built from
     * the deployment's server URL, which nothing validates for a scheme, and the field's own
     * validation is lenient enough to store a scheme-less value. Storing one would leave the
     * instrument permanently showing an address nobody can follow, so the field is left as it was
     * and the operator gets a reason.
     */
    Optional<String> publicLandingPage =
        InventoryUrls.publicLandingPageUrl(
                properties.getServerUrl(), identifier.getPublicLinkSuffix())
            .filter(PidinstFields::isResolvableAddress);
    if (publicLandingPage.isEmpty()) {
      log.warn(
          "Leaving the Landing page of {} as it was: no usable public landing page could be built,"
              + " which means no server URL is configured or it carries no http(s) scheme.",
          invRec.getGlobalIdentifier());
      return;
    }
    ApiInventoryEntityField fieldUpdate = new ApiInventoryEntityField();
    fieldUpdate.setId(field.get().getId());
    fieldUpdate.setContent(publicLandingPage.get());
    update.getFields().add(fieldUpdate);
  }

  private ApiInventoryDOI createNewDoi(User user) {
    return createNewDoi(user, InventorySettingType.IGSN);
  }

  @SneakyThrows
  private ApiInventoryDOI createNewDoi(User user, InventorySettingType settingType) {
    DataCiteDoi createdDoi;
    try {
      createdDoi = dataCiteConnector.registerDoi(new DataCiteDoi(), settingType);
    } catch (DataCiteConnectionException dcException) {
      throw new DataCiteConnectionException(
          "Error when registering new DOI with DataCite. "
              + "If the problem persists, please contact your System Admin",
          dcException);
    }
    if (createdDoi == null || !"draft".equals(createdDoi.getAttributes().getState())) {
      throw new IllegalStateException(
          messages.getMessage("errors.inventory.identifier.dataCiteRegisterNoDraft"));
    }

    ApiInventoryDOI newDoi = new ApiInventoryDOI(user, createdDoi);
    // same invariant as the B2INST path: the entity adopts a DTO-generated suffix (RSDEV-1254);
    // for DataCite nothing consumes it before entity creation, so behavior is unchanged
    newDoi.generatePublicLinkSuffix();
    newDoi.setRegisterIdentifierRequest(true);
    newDoi.setCreatorName(user.getFullName());
    newDoi.setCreatorType("Personal");
    newDoi.setPublisher(properties.getCustomerName());
    newDoi.setPublicationYear(Year.now().getValue());
    if (InventorySettingType.PIDINST.equals(settingType)) {
      newDoi.setDoiType(IdentifierType.PIDINST_DATACITE.name());
      newDoi.setResourceType("Instrument");
      newDoi.setResourceTypeGeneral("Instrument");
    } else {
      newDoi.setDoiType(IdentifierType.IGSN_DATACITE.name());
      newDoi.setResourceType("Material Sample");
      newDoi.setResourceTypeGeneral("PhysicalObject");
    }
    return newDoi;
  }

  private ApiInventoryDOI updateNewAssociatedDoi(InventoryRecord invRec, ApiInventoryDOI doi) {
    doi.setTitle(invRec.getName());
    doi.setAssociatedGlobalId(invRec.getGlobalIdentifier());
    return doi;
  }

  private ApiInventoryDOI createUpdateWithNewDoi(
      InventoryRecord invRec, User user, InventorySettingType settingType) {
    ApiInventoryDOI newDoi;
    if (InventorySettingType.PIDINST.equals(settingType)
        && b2instConnector.isConfiguredAndEnabled()) {
      newDoi = createNewB2instDoi(invRec, user);
    } else {
      newDoi = createNewDoi(user, settingType);
    }
    return updateNewAssociatedDoi(invRec, newDoi);
  }

  /**
   * Registers a draft instrument record with B2INST and returns the RSpace DOI representation,
   * persisting the B2INST record id (RID) as the identifier. The Handle PID is minted only on
   * publish.
   *
   * <p>The identifier's public landing page address is generated before the registration call and
   * registered as its LandingPage, unless the instrument carries a landing page the user typed
   * themselves (RSDEV-1254, ADR 0006).
   */
  private ApiInventoryDOI createNewB2instDoi(InventoryRecord invRec, User user) {
    ApiInventoryDOI newDoi = new ApiInventoryDOI();
    // the public landing page address must exist before the provider call so it can be part of
    // the registered metadata; the same suffix later becomes the entity's publicLink, so the
    // registered address and the page RSpace serves can never diverge (RSDEV-1254, ADR 0006)
    newDoi.generatePublicLinkSuffix();
    String publicLandingPageUrl =
        InventoryUrls.publicLandingPageUrl(properties.getServerUrl(), newDoi.getPublicLinkSuffix())
            .orElse(null);

    B2instDoi b2instDoi =
        rspaceToExternalProviderAdapter.buildB2instDoi(invRec, publicLandingPageUrl);
    B2instDraftRecord draft;
    try {
      draft = b2instConnector.registerDoi(b2instDoi);
    } catch (B2instConnectionException b2instException) {
      throw B2instConnectionException.wrapping(
          messages.getMessage(
              "errors.inventory.identifier.b2instRegisterFailed",
              new Object[] {b2instException.getReason()}),
          b2instException);
    }
    if (draft == null || isBlank(draft.getId())) {
      throw new IllegalStateException(
          messages.getMessage("errors.inventory.identifier.b2instRegisterNoDraft"));
    }

    newDoi.setRegisterIdentifierRequest(true);
    newDoi.setDoi(draft.getId()); // the draft RID; the Handle PID is minted on publish
    newDoi.setState("draft");
    // B2INST reports the record's own page; prefer it over building the URL from the server
    // setting. It is not a public URL (viewing it needs a B2INST sign-in), hence not publicUrl.
    if (draft.getLinks() != null) {
      newDoi.setProviderUrl(draft.getLinks().getSelfHtml());
    }
    newDoi.setCreatorName(user.getFullName());
    newDoi.setCreatorType("Personal");
    newDoi.setPublisher(properties.getCustomerName());
    newDoi.setPublicationYear(Year.now().getValue());
    newDoi.setDoiType(IdentifierType.PIDINST_B2INST.name());
    newDoi.setResourceType("Instrument");
    newDoi.setResourceTypeGeneral("Instrument");
    return newDoi;
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
    if (isB2inst(doi.getType())) {
      return deleteFromB2inst(doi);
    }
    boolean dataCiteDeleteResult;
    try {
      dataCiteDeleteResult =
          dataCiteConnector.deleteDoi(doi.getIdentifier(), settingTypeFor(doi.getType()));
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

  private boolean deleteFromB2inst(DigitalObjectIdentifier doi) {
    boolean deleteResult;
    try {
      deleteResult = b2instConnector.deleteDoi(doi.getIdentifier());
    } catch (B2instConnectionException b2instException) {
      log.error("Error when deleting the PID from B2INST: ", b2instException);
      throw B2instConnectionException.wrapping(
          messages.getMessage(
              "errors.inventory.identifier.b2instDeleteFailed",
              new Object[] {b2instException.getReason()}),
          b2instException);
    }
    if (!deleteResult) {
      throw new IllegalStateException("B2INST delete failed");
    }
    return deleteResult;
  }

  @SneakyThrows
  private ApiInventoryDOI createUpdateWithPublishedDoi(InventoryRecord invRec, User user) {

    DigitalObjectIdentifier doi = invRec.getActiveIdentifiers().get(0);
    if (isB2inst(doi.getType())) {
      return createUpdateWithPublishedB2instDoi(doi);
    }
    String rorAffiliationID = rorService.getSystemRoRValue();
    String rorAffiliationName = rorService.getRorNameForSystemRoRValue();
    ApiInventoryDOI actualdoi = new ApiInventoryDOI(doi);
    actualdoi.setCreatorAffiliation(rorAffiliationName);
    actualdoi.setCreatorAffiliationIdentifier(rorAffiliationID);
    DataCiteDoi doiToPublish = rspaceToExternalProviderAdapter.buildDataCiteDoi(actualdoi, invRec);
    DataCiteDoi publishResult;
    try {
      publishResult = dataCiteConnector.publishDoi(doiToPublish, settingTypeFor(doi.getType()));
    } catch (DataCiteConnectionException dcException) {
      throw new DataCiteConnectionException(
          messages.getMessage("errors.inventory.identifier.dataCitePublishFailed"), dcException);
    }
    if (publishResult == null || !"findable".equals(publishResult.getAttributes().getState())) {
      throw new IllegalStateException("DataCite publish failed");
    }

    ApiInventoryDOI publishDoi = new ApiInventoryDOI();
    publishDoi.setId(invRec.getActiveIdentifiers().get(0).getId());
    publishDoi.setState(publishResult.getAttributes().getState());
    publishDoi.setUrl(publishResult.getAttributes().getUrl());
    publishDoi.setPublicUrl(DOI_URL_PREFIX + doi.getIdentifier());
    publishDoi.setCreatorAffiliation(rorAffiliationName);
    publishDoi.setCreatorAffiliationIdentifier(rorAffiliationID);
    return publishDoi;
  }

  @SneakyThrows
  private ApiInventoryDOI createUpdateWithRetractedDoi(InventoryRecord invRec, User user) {
    DigitalObjectIdentifier doi = invRec.getActiveIdentifiers().get(0);
    if (isB2inst(doi.getType())) {
      return createUpdateWithRetractedB2instDoi(doi);
    }
    String rorAffiliationID = rorService.getSystemRoRValue();
    String rorAffiliationName = rorService.getRorNameForSystemRoRValue();
    ApiInventoryDOI actualdoi = new ApiInventoryDOI(doi);
    actualdoi.setCreatorAffiliation(rorAffiliationName);
    actualdoi.setCreatorAffiliationIdentifier(rorAffiliationID);
    DataCiteDoi doiToRetract = rspaceToExternalProviderAdapter.buildDataCiteDoi(actualdoi, invRec);

    DataCiteDoi retractResult;
    try {
      retractResult = dataCiteConnector.retractDoi(doiToRetract, settingTypeFor(doi.getType()));
    } catch (DataCiteConnectionException dcException) {
      throw new DataCiteConnectionException(
          messages.getMessage("errors.inventory.identifier.dataCiteRetractFailed"), dcException);
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

  /**
   * Best-effort publish for B2INST: submits the draft to the configured community. On the test
   * community this is curator-gated, so the returned state reflects the submission status rather
   * than a minted PID.
   */
  private ApiInventoryDOI createUpdateWithPublishedB2instDoi(DigitalObjectIdentifier doi) {
    B2instRequestResponse result;
    try {
      result = b2instConnector.publishDoi(doi.getIdentifier());
    } catch (B2instConnectionException b2instException) {
      throw B2instConnectionException.wrapping(
          messages.getMessage(
              "errors.inventory.identifier.b2instPublishFailed",
              new Object[] {b2instException.getReason()}),
          b2instException);
    }
    ApiInventoryDOI publishDoi = new ApiInventoryDOI();
    publishDoi.setId(doi.getId());
    if (result != null && isNotBlank(result.getStatus())) {
      publishDoi.setState(result.getStatus());
    }
    return publishDoi;
  }

  private ApiInventoryDOI createUpdateWithRetractedB2instDoi(DigitalObjectIdentifier doi) {
    /*
     * B2INST/Invenio has no retract operation at all, so refuse before touching the connector, whose
     * UnsupportedOperationException would reach the user as developer-facing English. ApiRuntimeException
     * rather than UnsupportedOperationException because the advice maps the latter to 404 alongside
     * NotFoundException, which misreports an existing record as missing, and logs a full stack trace for
     * what is an expected refusal; this one resolves the bundle key itself and answers 422. The UI
     * already disables Delete/Retract for every B2INST review state, but the API is callable directly.
     */
    throw new ApiRuntimeException("errors.inventory.identifier.b2instRetractUnsupported");
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
