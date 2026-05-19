package com.researchspace.service.inventory.impl;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentEntity;
import com.researchspace.api.v1.model.ApiInstrumentEntityInfo;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.dao.InventoryEntityFieldDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.events.InventoryDeleteEvent;
import com.researchspace.model.events.InventoryEditingEvent;
import com.researchspace.model.events.InventoryRestoreEvent;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.record.IActiveUserStrategy;
import com.researchspace.service.inventory.InstrumentApiManager;
import com.researchspace.service.inventory.InventoryMoveHelper;
import com.researchspace.service.inventory.SampleApiManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InstrumentApiManagerImpl extends InventoryApiManagerImpl<InstrumentEntity>
    implements InstrumentApiManager {

  public static final String INSTRUMENT_DEFAULT_NAME = "Generic Instrument";

  private @Autowired InstrumentDao instrumentDao;
  private @Autowired InstrumentTemplateDao instrumentTemplateDao;
  private @Autowired InventoryEntityFieldDao inventoryEntityFieldDao;
  private @Autowired SampleApiManager sampleApiManager;
  private @Autowired InventoryMoveHelper inventoryMoveHelper;

  @Override
  public boolean instrumentExists(long id) {
    return instrumentDao.exists(id);
  }

  @Override
  public boolean instrumentTemplateExists(long id) {
    return instrumentTemplateDao.exists(id);
  }

  @Override
  public ApiInstrument createNewApiInstrument(ApiInstrument apiInstrument, User user) {

    InstrumentEntity instrumentTemplate =
        getInstrumentTemplateIfPresentOnRequest(apiInstrument, user);

    String instrumentName = getNameForIncomingApiInstrument(apiInstrument);
    return createInstrument(instrumentName, apiInstrument, instrumentTemplate, user);
  }

  private InstrumentEntity getInstrumentTemplateIfPresentOnRequest(
      ApiInstrument apiInstrument, User user) {
    InstrumentEntity template = null;
    Long templateId = apiInstrument.getTemplateId();
    // if templateId is null (we're creating a new instrument),
    // but if not null, we expect it to exist
    if (templateId != null) {
      template = assertUserCanReadInstrumentTemplate(templateId, user);
    }
    return template;
  }

  private String getNameForIncomingApiInstrument(ApiInstrumentEntity instrumentRequest) {
    return StringUtils.isNotBlank(instrumentRequest.getName())
        ? instrumentRequest.getName()
        : INSTRUMENT_DEFAULT_NAME;
  }

  private ApiInstrument createInstrument(
      String instrumentName,
      ApiInstrument apiInstrument,
      InstrumentEntity instrTemplate,
      User user) {
    Instrument instrumentToSave =
        recordFactory.createInstrument(instrumentName, user, instrTemplate);

    setBasicFieldsFromNewIncomingApiInventoryRecord(instrumentToSave, apiInstrument, user);
    if (instrTemplate != null) {
      // might be null from incoming API request, but here we want to reference template icon id
      instrumentToSave.setIconId(instrTemplate.getIconId());
    }
    if (!apiInstrument.getFields().isEmpty()) {
      saveNewApiFieldsIntoInstrumentFields(
          apiInstrument.getFields(), instrumentToSave.getActiveFields(), user);
    } else {
      assertDefaultFieldsValid(instrumentToSave.getActiveFields());
    }
    setLocationForNewInstrument(apiInstrument, instrumentToSave, user);

    Instrument savedInstrument = instrumentDao.save(instrumentToSave);
    saveIncomingInstrumentImage(savedInstrument, apiInstrument, user);

    publisher.publishEvent(new InventoryCreationEvent(savedInstrument, user));

    ApiInstrument apiResultInstrument = new ApiInstrument(savedInstrument);
    if (instrTemplate != null) {
      apiResultInstrument.setTemplateId(instrTemplate.getId());
    }
    populateOutgoingApiInstrumentEntity(apiResultInstrument, savedInstrument, user);

    return apiResultInstrument;
  }

  private void setLocationForNewInstrument(
      ApiInstrument apiInstrument, Instrument instrumentToSave, User user) {
    inventoryMoveHelper.moveRecordToTargetParentAndLocation(
        instrumentToSave,
        apiInstrument.getParentContainer(),
        apiInstrument.getParentLocation(),
        user);
    setWorkbenchAsParentForNewInstrument(instrumentToSave, user);
  }

  private void setWorkbenchAsParentForNewInstrument(Instrument instrument, User user) {
    Container workbench = containerDao.getWorkbenchForUser(user);
    setWorkbenchAsParentForNewInventoryRecord(workbench, instrument);
  }

  private void assertDefaultFieldsValid(List<InventoryEntityField> activeFields) {
    for (InventoryEntityField field : activeFields) {
      field.assertFieldDataValid(field.getFieldData());
    }
  }

  private void saveNewApiFieldsIntoInstrumentFields(
      List<ApiInventoryEntityField> apiFieldList,
      List<InventoryEntityField> inventoryEntityFieldList,
      User user) {

    if (apiFieldList.size() != inventoryEntityFieldList.size()) {
      throw new IllegalArgumentException(
          String.format(
              "Number of incoming instrument fields [%d]"
                  + " doesn't match number of template fields [%d]",
              apiFieldList.size(), inventoryEntityFieldList.size()));
    }

    for (int i = 0; i < apiFieldList.size(); i++) {
      ApiInventoryEntityField apiField = apiFieldList.get(i);
      String newFieldContent = apiField.getContent();
      InventoryEntityField inventoryEntityField = inventoryEntityFieldList.get(i);

      if (inventoryEntityField.isOptionsStoringField()) {
        inventoryEntityField.setSelectedOptions(apiField.getSelectedOptions());
      } else {
        inventoryEntityField.setFieldData(newFieldContent);
      }
    }
  }

  @Override
  public ApiInstrumentSearchResult getInstrumentsForUser(
      PaginationCriteria<Instrument> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user) {

    ISearchResults<Instrument> dbInstruments =
        instrumentDao.getInstrumentsForUser(pgCrit, ownedBy, deletedOption, user);
    List<ApiInstrumentEntityInfo> instrumentInfos = new ArrayList<>();
    for (Instrument instrument : dbInstruments.getResults()) {
      ApiInstrumentEntityInfo apiInstrument = new ApiInstrumentEntityInfo(instrument);
      setOtherFieldsForOutgoingApiInventoryRecord(apiInstrument, instrument, user);
      instrumentInfos.add(apiInstrument);
    }

    ApiInstrumentSearchResult result = new ApiInstrumentSearchResult();
    result.setTotalHits(dbInstruments.getTotalHits());
    result.setPageNumber(dbInstruments.getPageNumber());
    result.setItems(instrumentInfos);
    return result;
  }

  @Override
  public ApiInstrument getApiInstrumentById(Long id, User user) {
    return getInstrumentById(id, user);
  }

  @Override
  public ApiInstrumentEntity getApiInstrumentTemplateById(Long id, User user) {
    return getInstrumentTemplateById(id, user);
  }

  @Override
  public ApiInstrument updateApiInstrument(ApiInstrument apiInstrument, User user) {
    Instrument dbInstrument = assertUserCanEditInstrument(apiInstrument.getId(), user);
    boolean temporaryLock = lockItemForEdit(dbInstrument, user);
    try {
      dbInstrument = (Instrument) getIfExists(dbInstrument.getId());
      boolean contentChanged =
          extraFieldHelper.createDeleteRequestedExtraFieldsInDatabaseInstrument(
              apiInstrument, dbInstrument, user);
      contentChanged |=
          barcodesHelper.createDeleteRequestedBarcodes(
              apiInstrument.getBarcodes(), dbInstrument, user);
      contentChanged |=
          identifiersHelper.createDeleteRequestedIdentifiers(
              apiInstrument.getIdentifiers(), dbInstrument, user);
      contentChanged |=
          identifiersHelper.createAssignRequestedIdentifiers(
              apiInstrument.getIdentifiers(), dbInstrument, user);
      contentChanged |= apiInstrument.applyChangesToDatabaseInstrument(dbInstrument, user);
      contentChanged |= saveSharingACLForIncomingApiInvRec(dbInstrument, apiInstrument);
      contentChanged |= saveIncomingInstrumentImage(dbInstrument, apiInstrument, user);
      if (contentChanged) {
        saveDbInstrumentUpdate(dbInstrument, user);
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbInstrument, user);
      }
    }
    return getInstrumentById(dbInstrument.getId(), user);
  }

  private void saveDbInstrumentUpdate(Instrument dbInstrument, User user) {
    dbInstrument.setModificationDate(new Date());
    dbInstrument.setModifiedBy(user.getUsername(), IActiveUserStrategy.CHECK_OPERATE_AS);
    dbInstrument.increaseVersion();
    instrumentDao.save(dbInstrument);
    publisher.publishEvent(new InventoryEditingEvent(dbInstrument, user));
  }

  @Override
  public ApiInstrument markInstrumentAsDeleted(Long instrumentId, User user) {
    Instrument dbInstrument = assertUserCanDeleteInstrument(instrumentId, user);
    boolean temporaryLock = lockItemForEdit(dbInstrument, user);
    try {
      dbInstrument = (Instrument) getIfExists(dbInstrument.getId());
      if (!dbInstrument.isDeleted()) {
        // Detach from parent container before marking deleted, so the container's
        // location slot and count are updated consistently (mirrors SubSampleApiManagerImpl).
        dbInstrument.removeFromCurrentParent();
        dbInstrument.setRecordDeleted(true);
        instrumentDao.save(dbInstrument);
        publisher.publishEvent(new InventoryDeleteEvent(dbInstrument, user));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbInstrument, user);
      }
    }
    ApiInstrument deleted = getInstrumentById(dbInstrument.getId(), user);
    updateOntologyOnRecordChanges(deleted, user);
    return deleted;
  }

  @Override
  public ApiInstrument restoreDeletedInstrument(Long instrumentId, User user) {
    Instrument dbInstrument = assertUserCanDeleteInstrument(instrumentId, user);
    boolean temporaryLock = lockItemForEdit(dbInstrument, user);
    try {
      dbInstrument = (Instrument) getIfExists(dbInstrument.getId());
      if (dbInstrument.isDeleted()) {
        // Move the restored instrument to the user's workbench, since the original
        // container location is no longer available (mirrors SubSampleApiManagerImpl).
        Container workbench = containerDao.getWorkbenchForUser(user);
        dbInstrument.moveToNewParent(workbench);
        dbInstrument.setRecordDeleted(false);
        instrumentDao.save(dbInstrument);
        publisher.publishEvent(new InventoryRestoreEvent(dbInstrument, user));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbInstrument, user);
      }
    }
    ApiInstrument restored = getInstrumentById(dbInstrument.getId(), user);
    updateOntologyOnRecordChanges(restored, user);
    return restored;
  }

  @Override
  public ApiInstrument changeApiInstrumentOwner(ApiInstrument apiInstrument, User user) {
    Validate.notNull(apiInstrument.getOwner(), "'owner' field not present");
    Validate.notNull(apiInstrument.getOwner().getUsername(), "'owner.username' field not present");

    assertUserCanTransferInstrument(apiInstrument.getId(), user);
    Instrument dbInstrument = (Instrument) getIfExists(apiInstrument.getId());
    boolean temporaryLock = lockItemForEdit(dbInstrument, user);
    try {
      dbInstrument = (Instrument) getIfExists(dbInstrument.getId());
      User originalOwner = dbInstrument.getOwner();
      String newOwnerUsername = apiInstrument.getOwner().getUsername();
      if (!originalOwner.getUsername().equals(newOwnerUsername)) {
        Validate.isTrue(
            userManager.userExists(newOwnerUsername),
            "Target user [" + newOwnerUsername + "] not found");
        User newOwner = userManager.getUserByUsername(newOwnerUsername);
        dbInstrument.setOwner(newOwner);
        moveItemBetweenWorkbenches(dbInstrument, originalOwner, newOwner);
        instrumentDao.save(dbInstrument);
        publisher.publishEvent(
            new InventoryTransferEvent(dbInstrument, user, originalOwner, newOwner));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbInstrument, user);
      }
    }
    return getInstrumentById(dbInstrument.getId(), user);
  }

  @Override
  public ApiInstrument duplicateInstrument(Long instrumentId, User user) {
    Instrument dbInstrument = assertUserCanReadInstrument(instrumentId, user);
    Instrument copy = (Instrument) dbInstrument.copy(user);
    setWorkbenchAsParentForNewInstrument(copy, user);
    copy = instrumentDao.save(copy);
    publisher.publishEvent(new InventoryCreationEvent(copy, user));
    return getInstrumentById(copy.getId(), user);
  }

  @Override
  public boolean nameExistsForUser(String name, User user) {
    return !instrumentDao.findInstrumentsByName(name, user).isEmpty();
  }

  @Override
  public Instrument assertUserCanDeleteInstrument(Long dbId, User user) {
    Instrument instrument = (Instrument) getIfExists(dbId);
    invPermissions.assertUserCanDeleteInventoryRecord(instrument, user);
    return instrument;
  }

  @Override
  public Instrument assertUserCanTransferInstrument(Long dbId, User user) {
    Instrument instrument = (Instrument) getIfExists(dbId);
    invPermissions.assertUserCanTransferInventoryRecord(instrument, user);
    return instrument;
  }

  @Override
  public Instrument assertUserCanEditInstrument(Long dbId, User user) {
    Instrument instrument = instrumentDao.get(dbId);
    invPermissions.assertUserCanEditInventoryRecord(instrument, user);
    return instrument;
  }

  @Override
  public Instrument assertUserCanReadInstrument(Long dbId, User user) {
    Instrument instrument = instrumentDao.get(dbId);
    invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(instrument, user);
    return instrument;
  }

  @Override
  public InstrumentTemplate assertUserCanEditInstrumentTemplate(Long dbId, User user) {
    InstrumentTemplate instrumentTemplate = instrumentTemplateDao.get(dbId);
    invPermissions.assertUserCanEditInventoryRecord(instrumentTemplate, user);
    return instrumentTemplate;
  }

  @Override
  public InstrumentTemplate assertUserCanReadInstrumentTemplate(Long dbId, User user) {
    InstrumentTemplate instrumentTemplate = instrumentTemplateDao.get(dbId);
    invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(instrumentTemplate, user);
    return instrumentTemplate;
  }

  @Override
  public InventoryRecord assertUserCanReadInventoryEntityField(Long id, User user) {
    InventoryRecord parentEntity = inventoryEntityFieldDao.getParentInventoryEntityFromFieldId(id);
    GlobalIdentifier entityGlobalId = parentEntity.getOid();
    switch (parentEntity.getType()) {
      case SAMPLE:
        return sampleApiManager.assertUserCanReadSample(entityGlobalId.getDbId(), user);
      case INSTRUMENT:
        return this.assertUserCanReadInstrument(entityGlobalId.getDbId(), user);
      case INSTRUMENT_TEMPLATE:
        return this.assertUserCanReadInstrumentTemplate(entityGlobalId.getDbId(), user);
      default:
        throw new IllegalArgumentException(
            "unsupported global id type: " + entityGlobalId.getIdString());
    }
  }

  @Override
  public InventoryRecord assertUserCanEditInventoryEntityField(Long id, User user) {
    InventoryRecord parentEntity = inventoryEntityFieldDao.getParentInventoryEntityFromFieldId(id);
    GlobalIdentifier entityGlobalId = parentEntity.getOid();
    switch (parentEntity.getType()) {
      case SAMPLE:
        return sampleApiManager.assertUserCanEditSample(entityGlobalId.getDbId(), user);
      case INSTRUMENT:
        return this.assertUserCanEditInstrument(entityGlobalId.getDbId(), user);
      case INSTRUMENT_TEMPLATE:
        return this.assertUserCanEditInstrumentTemplate(entityGlobalId.getDbId(), user);
      default:
        throw new IllegalArgumentException(
            "unsupported global id type: " + entityGlobalId.getIdString());
    }
  }

  private ApiInstrument getInstrumentById(Long id, User user) {
    Instrument instrument = instrumentDao.get(id);
    publisher.publishEvent(new InventoryAccessEvent(instrument, user));
    ApiInstrument result = new ApiInstrument(instrument);
    populateOutgoingApiInstrumentEntity(result, instrument, user);
    return result;
  }

  private ApiInstrumentEntity getInstrumentTemplateById(Long id, User user) {
    InstrumentTemplate instrumentTemplate = instrumentTemplateDao.get(id);
    publisher.publishEvent(new InventoryAccessEvent(instrumentTemplate, user));
    ApiInstrumentTemplate result = new ApiInstrumentTemplate(instrumentTemplate);
    populateOutgoingApiInstrumentEntity(result, instrumentTemplate, user);
    return result;
  }

  private void populateOutgoingApiInstrumentEntity(
      ApiInstrumentEntity apiInstrument, InstrumentEntity instrument, User user) {
    if (apiInstrument != null) { // populate only if it is already created
      setOtherFieldsForOutgoingApiInventoryRecord(apiInstrument, instrument, user);
      populateSharingPermissions(apiInstrument.getSharedWith(), instrument);
    }
  }

  /**
   * Save incoming instrument image.
   *
   * @throws IOException
   * @returns true if any i mages were saved
   */
  private boolean saveIncomingInstrumentImage(
      InstrumentEntity dbInstrument, ApiInstrumentEntity apiInstrument, User user) {
    return saveIncomingImage(
        dbInstrument,
        apiInstrument,
        user,
        Instrument.class,
        instrument -> instrumentDao.save(instrument));
  }

  @Override
  public InstrumentEntity getIfExists(Long id) {
    if (instrumentExists(id)) {
      return instrumentDao.get(id);
    } else if (instrumentTemplateExists(id)) {
      return instrumentTemplateDao.get(id);
    }
    throw new NotFoundException("No Instrument or InstrumentTemplate found with id: " + id);
  }
}
