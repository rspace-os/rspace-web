package com.researchspace.service.inventory.impl;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiFieldToModelFieldFactory;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentEntity;
import com.researchspace.api.v1.model.ApiInstrumentEntityInfo;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInstrumentTemplateSearchResult;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
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
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.events.InventoryRestoreEvent;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.record.IActiveUserStrategy;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.DataCiteRelationType;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import com.researchspace.service.inventory.InventoryFieldNameUniquenessValidator;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.inventory.InventoryLinkValidator;
import com.researchspace.service.inventory.InventoryMoveHelper;
import com.researchspace.service.inventory.SampleApiManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InstrumentEntityApiManagerImpl extends InventoryApiManagerImpl<InstrumentEntity>
    implements InstrumentEntityApiManager {

  public static final String INSTRUMENT_DEFAULT_NAME = "Generic Instrument";

  private @Autowired InstrumentDao instrumentDao;
  private @Autowired InstrumentTemplateDao instrumentTemplateDao;
  private @Autowired InventoryEntityFieldDao inventoryEntityFieldDao;
  private @Autowired SampleApiManager sampleApiManager;
  private @Autowired InventoryLinkManager inventoryLinkManager;
  private @Autowired InventoryMoveHelper inventoryMoveHelper;
  private @Autowired InventoryAuditApiManager inventoryAuditMgr;
  private @Autowired ApiFieldToModelFieldFactory apiFieldToModelFieldFactory;
  private @Autowired MessageSourceUtils messages;

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

    InstrumentTemplate instrumentTemplate =
        getInstrumentTemplateIfPresentOnRequest(apiInstrument, user);

    String instrumentName = getNameForIncomingApiInstrument(apiInstrument);
    return createInstrument(instrumentName, apiInstrument, instrumentTemplate, user);
  }

  private InstrumentTemplate getInstrumentTemplateIfPresentOnRequest(
      ApiInstrument apiInstrument, User user) {
    InstrumentTemplate template = null;
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
      InstrumentTemplate instrTemplate,
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

    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNames(instrumentToSave);
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

      if (inventoryEntityField instanceof InventoryLinkField) {
        applyLinkFieldValue((InventoryLinkField) inventoryEntityField, apiField, user);
      } else if (inventoryEntityField.isOptionsStoringField()) {
        inventoryEntityField.setSelectedOptions(apiField.getSelectedOptions());
      } else {
        inventoryEntityField.setFieldData(newFieldContent);
      }
    }
  }

  /**
   * Applies a link value to a structured link field, going through the {@link InventoryLinkManager}
   * so the target is parsed/validated and the Envers revision captured. Mirrors the implementation
   * in {@link SampleApiManagerImpl}.
   */
  boolean applyLinkFieldValue(
      InventoryLinkField field, ApiInventoryEntityField apiField, User user) {
    ApiInventoryLink apiLink = apiField.getLink();
    String target = apiLink == null ? null : apiLink.getTargetGlobalId();
    InventoryLink existing = field.getLink();
    if (target == null || target.trim().isEmpty()) {
      if (existing == null) {
        return false;
      }
      field.setLink(null);
      return true;
    }
    Long effectivePin =
        apiLink.derivedVersionPin() != null ? apiLink.derivedVersionPin() : apiLink.getVersionPin();
    GlobalIdentifier incoming = parseTargetOrNull(target);
    if (existing != null
        && incoming != null
        && incoming.getPrefix() == existing.getTargetPrefix()
        && Objects.equals(incoming.getDbId(), existing.getTargetDbId())
        && Objects.equals(effectivePin, existing.getVersionPin())
        && Objects.equals(apiLink.getRelationType(), existing.getRelationType())) {
      return false;
    }
    assertRelationAllowed(field, apiLink.getRelationType());
    if (existing != null) {
      field.setLink(inventoryLinkManager.updateLink(existing, apiLink, user));
    } else {
      field.setLink(inventoryLinkManager.createLink(apiLink, user));
    }
    return true;
  }

  /**
   * Applies link values to an existing instrument's structured link fields (the update path). The
   * DTO apply loop leaves link fields untouched because it cannot reach the service-layer {@link
   * InventoryLinkManager}; this matches each modified link field by id and applies it here.
   */
  private boolean applyLinkFieldValuesOnUpdate(
      ApiInstrument apiInstrument, Instrument dbInstrument, User user) {
    if (apiInstrument.getFields() == null) {
      return false;
    }
    boolean changed = false;
    for (ApiInventoryEntityField apiField : apiInstrument.getFields()) {
      if (apiField.isNewFieldRequest()
          || apiField.isDeleteFieldRequest()
          || apiField.getId() == null) {
        continue;
      }
      Optional<InventoryEntityField> dbFieldOpt =
          dbInstrument.getActiveFields().stream()
              .filter(
                  f ->
                      f instanceof InventoryLinkField
                          && Objects.equals(f.getId(), apiField.getId()))
              .findFirst();
      if (dbFieldOpt.isPresent()) {
        rejectSelfLink(apiField.getLink(), dbInstrument);
        changed |= applyLinkFieldValue((InventoryLinkField) dbFieldOpt.get(), apiField, user);
      }
    }
    return changed;
  }

  private void assertRelationAllowed(InventoryLinkField field, String relationType) {
    if (!DataCiteRelationType.isValid(relationType)) {
      throw new ApiRuntimeException("errors.inventory.field.linkRelationTypeInvalid", relationType);
    }
    String allowed = field.getAllowedRelationTypes();
    if (allowed == null || allowed.trim().isEmpty()) {
      return;
    }
    if (!Arrays.asList(allowed.split("\\|")).contains(relationType)) {
      throw new ApiRuntimeException(
          "errors.inventory.field.linkRelationTypeNotPermitted", relationType, field.getName());
    }
  }

  private void rejectSelfLink(ApiInventoryLink apiLink, Instrument dbInstrument) {
    if (apiLink == null || dbInstrument.getOid() == null) {
      return;
    }
    GlobalIdentifier target = parseTargetOrNull(apiLink.getTargetGlobalId());
    if (target == null) {
      return;
    }
    if (InventoryLinkValidator.isSelfLink(target, dbInstrument.getOid().toString())) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.selfLinkForbidden", apiLink.getTargetGlobalId());
    }
  }

  private GlobalIdentifier parseTargetOrNull(String targetGlobalId) {
    try {
      return new GlobalIdentifier(targetGlobalId);
    } catch (IllegalArgumentException | NullPointerException ex) {
      return null;
    }
  }

  @Override
  public ApiInstrumentSearchResult getInstrumentsForUser(
      PaginationCriteria<Instrument> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user) {

    ISearchResults<Instrument> dbInstruments =
        instrumentDao.getInstrumentsForUser(pgCrit, ownedBy, deletedOption, null, user);
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
  public ApiInstrument getApiInstrumentVersion(Long instrumentId, Long version, User user) {
    // Intentionally no explicit read-permission assert: like the live getApiInstrumentById read,
    // access control is enforced downstream by setOtherFieldsForOutgoingApiInventoryRecord, which
    // reduces the response to a public-view whitelist for a user without read permission. Do not
    // drop that reduction in a refactor or this would leak full historical data. (The sibling
    // /revisions endpoint asserts at the controller instead and hard-errors; the same data is
    // exposed either way, only the HTTP status differs.)
    Instrument currentInstrument = (Instrument) getIfExists(instrumentId);
    if (version.equals(currentInstrument.getVersion())) {
      return getApiInstrumentById(instrumentId, user);
    }
    // joins this transaction (REQUIRED propagation), so currentInstrument stays
    // session-attached; historical reads intentionally publish no InventoryAccessEvent,
    // matching the template-version precedent
    ApiInstrument apiInstrumentVersion =
        inventoryAuditMgr.getApiInstrumentVersion(currentInstrument, version);
    if (apiInstrumentVersion != null) {
      // permissions are evaluated against the live instrument, as for a regular retrieval
      populateOutgoingApiInstrumentEntity(apiInstrumentVersion, currentInstrument, user);
    }
    return apiInstrumentVersion;
  }

  @Override
  public ApiInstrumentTemplate getApiInstrumentTemplateById(Long id, User user) {
    return getInstrumentTemplateById(id, user);
  }

  @Override
  public ApiInstrument updateApiInstrument(ApiInstrument apiInstrument, User user) {
    Instrument dbInstrument = assertUserCanEditInstrument(apiInstrument.getId(), user);
    ApiInstrument apiInstrumentOriginal = new ApiInstrument(dbInstrument);
    boolean temporaryLock = lockItemForEdit(dbInstrument, user);
    try {
      dbInstrument = (Instrument) getIfExists(dbInstrument.getId());
      Container orgParent = dbInstrument.getParentContainer();
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
      contentChanged |= applyLinkFieldValuesOnUpdate(apiInstrument, dbInstrument, user);
      contentChanged |= saveSharingACLForIncomingApiInvRec(dbInstrument, apiInstrument);
      contentChanged |= saveIncomingInstrumentImage(dbInstrument, apiInstrument, user);
      InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNames(dbInstrument);
      boolean moveSuccessful =
          inventoryMoveHelper.moveRecordToTargetParentAndLocation(
              dbInstrument,
              apiInstrument.getParentContainer(),
              apiInstrument.getParentLocation(),
              user);
      if (contentChanged) {
        saveDbInstrumentUpdate(dbInstrument, user);
      } else if (moveSuccessful) {
        instrumentDao.save(dbInstrument);
      }
      if (moveSuccessful) {
        publisher.publishEvent(
            new InventoryMoveEvent(
                dbInstrument, orgParent, dbInstrument.getParentContainer(), user));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbInstrument, user);
      }
    }

    ApiInstrument apiInstrumentResult = new ApiInstrument(dbInstrument);
    populateOutgoingApiInstrumentEntity(apiInstrumentResult, dbInstrument, user);
    updateOntologyOnUpdate(apiInstrumentOriginal, apiInstrumentResult, user);
    return apiInstrumentResult;
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

    ApiInstrument apiInstrumentResult = new ApiInstrument(dbInstrument);
    populateOutgoingApiInstrumentEntity(apiInstrumentResult, dbInstrument, user);
    updateOntologyOnRecordChanges(apiInstrumentResult, user);
    return apiInstrumentResult;
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
    ApiInstrument apiInstrumentResult = new ApiInstrument(dbInstrument);
    populateOutgoingApiInstrumentEntity(apiInstrumentResult, dbInstrument, user);
    updateOntologyOnRecordChanges(apiInstrumentResult, user);
    return apiInstrumentResult;
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
    ApiInstrument result = new ApiInstrument(copy);
    populateOutgoingApiInstrumentEntity(result, copy, user);
    return result;
  }

  @Override
  public ApiInstrumentTemplateSearchResult getTemplatesForUser(
      PaginationCriteria<InstrumentTemplate> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user) {

    ISearchResults<InstrumentTemplate> dbTemplates =
        instrumentTemplateDao.getTemplatesForUser(pgCrit, ownedBy, deletedOption, null, user);
    List<ApiInstrumentEntityInfo> templateInfos = new ArrayList<>();
    for (InstrumentTemplate template : dbTemplates.getResults()) {
      ApiInstrumentEntityInfo apiInfo = new ApiInstrumentEntityInfo(template);
      setOtherFieldsForOutgoingApiInventoryRecord(apiInfo, template, user);
      templateInfos.add(apiInfo);
    }

    ApiInstrumentTemplateSearchResult result = new ApiInstrumentTemplateSearchResult();
    result.setTotalHits(dbTemplates.getTotalHits());
    result.setPageNumber(dbTemplates.getPageNumber());
    result.setItems(templateInfos);
    return result;
  }

  @Override
  public ApiInventorySearchResult searchInstrumentsForUser(
      PaginationCriteria<Instrument> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      String searchTerm,
      User user) {
    ISearchResults<Instrument> dbInstruments =
        instrumentDao.getInstrumentsForUser(pgCrit, ownedBy, deletedOption, searchTerm, user);
    return convertToApiInventorySearchResult(
        dbInstruments.getTotalHits(),
        (pgCrit != null ? pgCrit.getPageNumber().intValue() : 0),
        dbInstruments.getResults(),
        user);
  }

  @Override
  public ApiInventorySearchResult searchInstrumentTemplatesForUser(
      PaginationCriteria<InstrumentTemplate> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      String searchTerm,
      User user) {
    ISearchResults<InstrumentTemplate> dbTemplates =
        instrumentTemplateDao.getTemplatesForUser(pgCrit, ownedBy, deletedOption, searchTerm, user);
    return convertToApiInventorySearchResult(
        dbTemplates.getTotalHits(),
        (pgCrit != null ? pgCrit.getPageNumber().intValue() : 0),
        dbTemplates.getResults(),
        user);
  }

  @Override
  public ApiInstrumentTemplate getApiInstrumentTemplateVersion(Long id, Long version, User user) {
    InstrumentTemplate dbTemplate = assertUserCanReadInstrumentTemplate(id, user);
    if (version.equals(dbTemplate.getVersion())) {
      return getApiInstrumentTemplateById(id, user);
    }
    ApiInstrumentTemplate apiTemplateVersion =
        inventoryAuditMgr.getApiInstrumentTemplateVersion(dbTemplate, version);
    if (apiTemplateVersion != null) {
      populateOutgoingApiInstrumentEntity(apiTemplateVersion, dbTemplate, user);
    }
    return apiTemplateVersion;
  }

  @Override
  public ApiInstrumentTemplate createInstrumentTemplate(ApiInstrumentTemplatePost post, User user) {
    InstrumentTemplate template = new InstrumentTemplate();
    template.setName(
        StringUtils.isNotBlank(post.getName()) ? post.getName() : INSTRUMENT_DEFAULT_NAME);
    template.setOwner(user);
    template.setCreatedBy(user.getUsername());
    template.setModifiedBy(user.getUsername(), IActiveUserStrategy.CHECK_OPERATE_AS);
    setBasicFieldsFromNewIncomingApiInventoryRecord(template, post, user);
    addFieldsToNewInstrumentTemplate(post, template);
    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNames(template);
    InstrumentTemplate savedTemplate = instrumentTemplateDao.persistInstrumentTemplate(template);
    saveIncomingInstrumentImage(savedTemplate, post, user);

    publisher.publishEvent(new InventoryCreationEvent(savedTemplate, user));

    ApiInstrumentTemplate result = new ApiInstrumentTemplate(savedTemplate);
    populateOutgoingApiInstrumentEntity(result, savedTemplate, user);
    return result;
  }

  private void addFieldsToNewInstrumentTemplate(
      ApiInstrumentTemplatePost post, InstrumentTemplate template) {
    for (ApiInventoryEntityField apiField : post.getFields()) {
      InventoryEntityField toAdd =
          apiFieldToModelFieldFactory.apiInventoryFieldToModelField(apiField);
      addNewFieldToInstrumentTemplate(template, toAdd);
    }
  }

  /**
   * Adds a new {@link InventoryEntityField} to an {@link InstrumentTemplate}, mirroring the
   * behaviour of {@code Sample.addSampleField}: assigns a column index before the field is added to
   * the list so that {@code getActiveFields()}'s natural sort cannot trip over a null columnIndex.
   */
  private void addNewFieldToInstrumentTemplate(
      InstrumentTemplate template, InventoryEntityField toAdd) {
    int nextIndex =
        template.getFields().stream()
                .map(InventoryEntityField::getColumnIndex)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0)
            + 1;
    toAdd.setColumnIndex(nextIndex);
    toAdd.setInventoryRecord(template);
    template.getFields().add(toAdd);
  }

  @Override
  public ApiInstrumentTemplate updateApiInstrumentTemplate(
      ApiInstrumentTemplate apiTemplate, User user) {
    InstrumentTemplate dbTemplate = assertUserCanEditInstrumentTemplate(apiTemplate.getId(), user);
    ApiInstrumentTemplate apiTemplateOriginal = new ApiInstrumentTemplate(dbTemplate);

    boolean temporaryLock = lockItemForEdit(dbTemplate, user);
    try {
      dbTemplate = (InstrumentTemplate) getIfExists(dbTemplate.getId());
      boolean contentChanged =
          createDeleteRequestedFieldsInDbInstrumentTemplate(apiTemplate, dbTemplate);
      contentChanged |=
          extraFieldHelper.createDeleteRequestedExtraFieldsInDatabaseInstrument(
              apiTemplate, dbTemplate, user);
      contentChanged |=
          barcodesHelper.createDeleteRequestedBarcodes(apiTemplate.getBarcodes(), dbTemplate, user);
      contentChanged |=
          identifiersHelper.createDeleteRequestedIdentifiers(
              apiTemplate.getIdentifiers(), dbTemplate, user);
      contentChanged |=
          identifiersHelper.createAssignRequestedIdentifiers(
              apiTemplate.getIdentifiers(), dbTemplate, user);
      contentChanged |= apiTemplate.applyChangesToDatabaseInstrument(dbTemplate, user);
      contentChanged |= saveSharingACLForIncomingApiInvRec(dbTemplate, apiTemplate);
      contentChanged |= saveIncomingInstrumentImage(dbTemplate, apiTemplate, user);
      if (contentChanged) {
        dbTemplate.refreshActiveFieldsAndColumnIndex();
        saveDbInstrumentTemplateUpdate(dbTemplate, user);
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbTemplate, user);
      }
    }

    ApiInstrumentTemplate result = new ApiInstrumentTemplate(dbTemplate);
    populateOutgoingApiInstrumentEntity(result, dbTemplate, user);
    updateOntologyOnUpdate(apiTemplateOriginal, result, user);
    return result;
  }

  private boolean createDeleteRequestedFieldsInDbInstrumentTemplate(
      ApiInstrumentTemplate apiTemplate, InstrumentTemplate dbTemplate) {
    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
        apiTemplate.getFields(), null);
    boolean changed = false;
    for (ApiInventoryEntityField apiField : apiTemplate.getFields()) {
      if (apiField.isNewFieldRequest()) {
        InventoryEntityField toAdd =
            apiFieldToModelFieldFactory.apiInventoryFieldToModelField(apiField);
        addNewFieldToInstrumentTemplate(dbTemplate, toAdd);
        changed = true;
      }
      if (apiField.isDeleteFieldRequest()) {
        if (apiField.getId() == null) {
          throw new IllegalArgumentException(
              "'id' property not provided "
                  + "for a template field with 'deleteFieldRequest' flag");
        }
        Optional<InventoryEntityField> dbFieldOpt =
            dbTemplate.getActiveFields().stream()
                .filter(f -> apiField.getId().equals(f.getId()))
                .findFirst();
        if (dbFieldOpt.isEmpty()) {
          throw new IllegalArgumentException(
              "Field id: "
                  + apiField.getId()
                  + " doesn't match id of any pre-existing template field");
        }
        dbTemplate.deleteInstrumentField(dbFieldOpt.get(), apiField.isDeleteFieldOnSampleUpdate());
        changed = true;
      }
    }
    return changed;
  }

  private void saveDbInstrumentTemplateUpdate(InstrumentTemplate dbTemplate, User user) {
    dbTemplate.setModificationDate(new Date());
    dbTemplate.setModifiedBy(user.getUsername(), IActiveUserStrategy.CHECK_OPERATE_AS);
    dbTemplate.increaseVersion();
    instrumentTemplateDao.save(dbTemplate);
    publisher.publishEvent(new InventoryEditingEvent(dbTemplate, user));
  }

  @Override
  public ApiInstrumentTemplate markInstrumentTemplateAsDeleted(Long templateId, User user) {
    InstrumentTemplate dbTemplate = assertUserCanDeleteInstrumentTemplate(templateId, user);
    boolean temporaryLock = lockItemForEdit(dbTemplate, user);
    try {
      dbTemplate = (InstrumentTemplate) getIfExists(dbTemplate.getId());
      if (!dbTemplate.isDeleted()) {
        dbTemplate.setRecordDeleted(true);
        instrumentTemplateDao.save(dbTemplate);
        publisher.publishEvent(new InventoryDeleteEvent(dbTemplate, user));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbTemplate, user);
      }
    }
    ApiInstrumentTemplate result = new ApiInstrumentTemplate(dbTemplate);
    populateOutgoingApiInstrumentEntity(result, dbTemplate, user);
    updateOntologyOnRecordChanges(result, user);
    return result;
  }

  @Override
  public ApiInstrumentTemplate restoreDeletedInstrumentTemplate(Long templateId, User user) {
    InstrumentTemplate dbTemplate = assertUserCanDeleteInstrumentTemplate(templateId, user);
    boolean temporaryLock = lockItemForEdit(dbTemplate, user);
    try {
      dbTemplate = (InstrumentTemplate) getIfExists(dbTemplate.getId());
      if (dbTemplate.isDeleted()) {
        dbTemplate.setRecordDeleted(false);
        instrumentTemplateDao.save(dbTemplate);
        publisher.publishEvent(new InventoryRestoreEvent(dbTemplate, user));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbTemplate, user);
      }
    }
    ApiInstrumentTemplate result = new ApiInstrumentTemplate(dbTemplate);
    populateOutgoingApiInstrumentEntity(result, dbTemplate, user);
    updateOntologyOnRecordChanges(result, user);
    return result;
  }

  @Override
  public ApiInstrumentTemplate changeApiInstrumentTemplateOwner(
      ApiInstrumentTemplate apiTemplate, User user) {
    Validate.notNull(apiTemplate.getOwner(), "'owner' field not present");
    Validate.notNull(apiTemplate.getOwner().getUsername(), "'owner.username' field not present");

    assertUserCanTransferInstrumentTemplate(apiTemplate.getId(), user);
    InstrumentTemplate dbTemplate = (InstrumentTemplate) getIfExists(apiTemplate.getId());
    boolean temporaryLock = lockItemForEdit(dbTemplate, user);
    try {
      dbTemplate = (InstrumentTemplate) getIfExists(dbTemplate.getId());
      User originalOwner = dbTemplate.getOwner();
      String newOwnerUsername = apiTemplate.getOwner().getUsername();
      if (!originalOwner.getUsername().equals(newOwnerUsername)) {
        Validate.isTrue(
            userManager.userExists(newOwnerUsername),
            "Target user [" + newOwnerUsername + "] not found");
        User newOwner = userManager.getUserByUsername(newOwnerUsername);
        dbTemplate.setOwner(newOwner);
        instrumentTemplateDao.save(dbTemplate);
        publisher.publishEvent(
            new InventoryTransferEvent(dbTemplate, user, originalOwner, newOwner));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbTemplate, user);
      }
    }
    return getApiInstrumentTemplateById(dbTemplate.getId(), user);
  }

  @Override
  public ApiInstrumentTemplate duplicateInstrumentTemplate(Long templateId, User user) {
    InstrumentTemplate dbTemplate = assertUserCanReadInstrumentTemplate(templateId, user);
    InstrumentTemplate copy = (InstrumentTemplate) dbTemplate.copy(user);
    copy = instrumentTemplateDao.save(copy);
    publisher.publishEvent(new InventoryCreationEvent(copy, user));
    ApiInstrumentTemplate result = new ApiInstrumentTemplate(copy);
    populateOutgoingApiInstrumentEntity(result, copy, user);
    return result;
  }

  @Override
  public ApiInstrument updateInstrumentToLatestTemplateVersion(Long instrumentId, User user) {
    Instrument dbInstrument = assertUserCanEditInstrument(instrumentId, user);
    InstrumentTemplate dbTemplate = dbInstrument.getInstrumentTemplate();
    if (dbTemplate == null) {
      throw new IllegalArgumentException("Instrument is not based on any template");
    }
    assertUserCanReadInstrumentTemplate(dbTemplate.getId(), user);

    boolean temporaryLock = lockItemForEdit(dbInstrument, user);
    try {
      dbInstrument = (Instrument) getIfExists(dbInstrument.getId());
      if (!dbTemplate.getVersion().equals(dbInstrument.getTemplateLinkedVersion())) {
        boolean updated = dbInstrument.updateToLatestTemplateVersion();
        // the model sync above does not copy link-field allowed-relation-types whitelists, so an
        // existing instrument would otherwise keep its create-time whitelist after a template edit
        // (RSDEV-1200) — mirror the sample path and re-apply them here.
        boolean whitelistsChanged =
            syncLinkFieldWhitelistsFromTemplate(dbInstrument.getActiveFields());
        if (updated || whitelistsChanged) {
          saveDbInstrumentUpdate(dbInstrument, user);
        }
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbInstrument, user);
      }
    }
    return getApiInstrumentById(instrumentId, user);
  }

  @Override
  public ApiInventorySearchResult getInstrumentsCreatedFromTemplate(
      Long templateId,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      PaginationCriteria<Instrument> pgCrit,
      User user) {

    InstrumentTemplate template = getInstrumentTemplateOrThrowNotFound(templateId);
    boolean canRead = invPermissions.canUserReadInventoryRecord(template, user);
    if (!canRead) {
      return ApiInventorySearchResult.emptyResult();
    }
    ISearchResults<Instrument> dbInstruments =
        instrumentDao.getInstrumentsForTemplate(pgCrit, templateId, ownedBy, deletedOption, user);
    return convertToApiInventorySearchResult(
        dbInstruments.getTotalHits(),
        (pgCrit != null ? pgCrit.getPageNumber().intValue() : 0),
        dbInstruments.getResults(),
        user);
  }

  @Override
  public List<ApiInventoryRecordInfo> getInstrumentsLinkingOldTemplateVersion(
      Long templateId, User user) {
    InstrumentTemplate template = assertUserCanReadInstrumentTemplate(templateId, user);
    List<Instrument> instruments =
        instrumentDao.getInstrumentsLinkingOlderTemplateVersionForUser(
            templateId, template.getVersion(), user);
    return instruments.stream()
        .map(ApiInventoryRecordInfo::fromInventoryRecord)
        .collect(Collectors.toList());
  }

  @Override
  public InstrumentTemplate saveIconId(InstrumentTemplate template, Long iconId) {
    template.setIconId(iconId);
    return instrumentTemplateDao.save(template);
  }

  @Override
  public boolean nameExistsForUser(String name, User user) {
    return !instrumentDao.findInstrumentsByName(name, user).isEmpty();
  }

  @Override
  public boolean templateNameExistsForUser(String name, User user) {
    return !instrumentTemplateDao.findInstrumentTemplatesByName(name, user).isEmpty();
  }

  @Override
  public Instrument assertUserCanDeleteInstrument(Long dbId, User user) {
    Instrument instrument = getInstrumentOrThrowNotFound(dbId);
    invPermissions.assertUserCanDeleteInventoryRecord(instrument, user);
    return instrument;
  }

  @Override
  public Instrument assertUserCanTransferInstrument(Long dbId, User user) {
    Instrument instrument = getInstrumentOrThrowNotFound(dbId);
    invPermissions.assertUserCanTransferInventoryRecord(instrument, user);
    return instrument;
  }

  @Override
  public Instrument assertUserCanEditInstrument(Long dbId, User user) {
    Instrument instrument = getInstrumentOrThrowNotFound(dbId);
    invPermissions.assertUserCanEditInventoryRecord(instrument, user);
    return instrument;
  }

  @Override
  public Instrument assertUserCanReadInstrument(Long dbId, User user) {
    Instrument instrument = getInstrumentOrThrowNotFound(dbId);
    invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(instrument, user);
    return instrument;
  }

  @Override
  public InstrumentTemplate assertUserCanEditInstrumentTemplate(Long dbId, User user) {
    InstrumentTemplate instrumentTemplate = getInstrumentTemplateOrThrowNotFound(dbId);
    invPermissions.assertUserCanEditInventoryRecord(instrumentTemplate, user);
    return instrumentTemplate;
  }

  @Override
  public InstrumentTemplate assertUserCanReadInstrumentTemplate(Long dbId, User user) {
    InstrumentTemplate instrumentTemplate = getInstrumentTemplateOrThrowNotFound(dbId);
    invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(instrumentTemplate, user);
    return instrumentTemplate;
  }

  @Override
  public InstrumentTemplate assertUserCanReadInstrumentTemplateWithPopulatedFields(
      Long dbId, User user) {
    InstrumentTemplate template = assertUserCanReadInstrumentTemplate(dbId, user);
    // force lazy initialisation of the fields collection within the current transaction so the
    // returned (eventually detached) entity can be safely walked by callers outside the tx.
    template.getActiveFields().size();
    return template;
  }

  @Override
  public InstrumentTemplate assertUserCanDeleteInstrumentTemplate(Long dbId, User user) {
    InstrumentTemplate instrumentTemplate = getInstrumentTemplateOrThrowNotFound(dbId);
    invPermissions.assertUserCanDeleteInventoryRecord(instrumentTemplate, user);
    return instrumentTemplate;
  }

  @Override
  public InstrumentTemplate assertUserCanTransferInstrumentTemplate(Long dbId, User user) {
    InstrumentTemplate instrumentTemplate = getInstrumentTemplateOrThrowNotFound(dbId);
    invPermissions.assertUserCanTransferInventoryRecord(instrumentTemplate, user);
    return instrumentTemplate;
  }

  private InventoryRecord getParentInventoryEntityOrThrowNotFound(Long fieldId) {
    try {
      return inventoryEntityFieldDao.getParentInventoryEntityFromFieldId(fieldId);
    } catch (NotFoundException nfe) {
      throw new NotFoundException(
          messages.getMessage("errors.inventory.field.notFound", new Object[] {fieldId}));
    }
  }

  @Override
  public InventoryRecord assertUserCanReadInventoryEntityField(Long id, User user) {
    InventoryRecord parentEntity = getParentInventoryEntityOrThrowNotFound(id);
    GlobalIdentifier entityGlobalId = parentEntity.getOid();
    switch (parentEntity.getType()) {
      case SAMPLE:
        return sampleApiManager.assertUserCanReadSample(entityGlobalId.getDbId(), user);
      case SAMPLE_TEMPLATE:
        return sampleApiManager.assertUserCanReadSampleTemplate(entityGlobalId.getDbId(), user);
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
    InventoryRecord parentEntity = getParentInventoryEntityOrThrowNotFound(id);
    GlobalIdentifier entityGlobalId = parentEntity.getOid();
    switch (parentEntity.getType()) {
      case SAMPLE:
        return sampleApiManager.assertUserCanEditSample(entityGlobalId.getDbId(), user);
      case SAMPLE_TEMPLATE:
        return sampleApiManager.assertUserCanEditSampleTemplate(entityGlobalId.getDbId(), user);
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

  private ApiInstrumentTemplate getInstrumentTemplateById(Long id, User user) {
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
      InstrumentEntity dbInstrument, ApiInventoryRecordInfo apiInstrument, User user) {
    if (dbInstrument.isTemplate()) {
      return saveIncomingImage(
          dbInstrument,
          apiInstrument,
          user,
          InstrumentTemplate.class,
          template -> instrumentTemplateDao.save(template));
    }
    return saveIncomingImage(
        dbInstrument,
        apiInstrument,
        user,
        Instrument.class,
        instrument -> instrumentDao.save(instrument));
  }

  /**
   * Returns the {@link Instrument} with the given id, or throws {@link NotFoundException} if no
   * instrument (as opposed to a template) exists with that id.
   *
   * <p>Queries the instrument table directly rather than routing through {@link #getIfExists} to
   * avoid a spurious 404 when an {@link InstrumentTemplate} happens to share the same numeric id
   * (the two tables use independent auto-increment sequences, so collisions are possible).
   */
  private Instrument getInstrumentOrThrowNotFound(Long id) {
    if (!instrumentExists(id)) {
      throw new NotFoundException("No instrument with id: " + id);
    }
    return instrumentDao.get(id);
  }

  /**
   * Returns the {@link InstrumentTemplate} with the given id, or throws {@link NotFoundException}
   * if no template (as opposed to an instrument instance) exists with that id.
   *
   * <p>Queries the template table directly for the same reason documented on {@link
   * #getInstrumentOrThrowNotFound}.
   */
  private InstrumentTemplate getInstrumentTemplateOrThrowNotFound(Long id) {
    if (!instrumentTemplateExists(id)) {
      throw new NotFoundException("No instrument template with id: " + id);
    }
    return instrumentTemplateDao.get(id);
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
