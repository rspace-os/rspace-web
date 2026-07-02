package com.researchspace.service.inventory.impl;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiFieldToModelFieldFactory;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplateInfo;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleTemplateSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSampleWithoutSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.jsonserialisers.LocalDateDeserialiser;
import com.researchspace.dao.SampleDao;
import com.researchspace.dao.SampleTemplateDao;
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
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventorySeriesNamingHelper;
import com.researchspace.model.inventory.MovableInventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.record.IActiveUserStrategy;
import com.researchspace.service.inventory.DataCiteRelationType;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import com.researchspace.service.inventory.InventoryFieldNameUniquenessValidator;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.inventory.InventoryLinkValidator;
import com.researchspace.service.inventory.InventoryMoveHelper;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("sampleApiManager")
public class SampleApiManagerImpl extends InventoryApiManagerImpl<SampleEntity>
    implements SampleApiManager {

  public static final String SAMPLE_DEFAULT_NAME = "Generic Sample";

  private @Autowired SubSampleApiManager subSampleMgr;
  private @Autowired SampleDao sampleDao;
  private @Autowired SampleTemplateDao sampleTemplateDao;
  private @Autowired InventoryMoveHelper inventoryMoveHelper;
  private @Autowired InventoryAuditApiManager inventoryAuditMgr;
  private @Autowired ApiFieldToModelFieldFactory apiFieldToModelFieldFactory;
  private @Autowired InventoryLinkManager inventoryLinkManager;

  @Override
  public ApiSampleSearchResult getSamplesForUser(
      PaginationCriteria<Sample> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user) {

    ISearchResults<Sample> dbSamples =
        sampleDao.getSamplesForUser(pgCrit, null, ownedBy, deletedOption, user);
    List<ApiSampleInfo> sampleInfos = new ArrayList<>();
    for (Sample sample : dbSamples.getResults()) {
      ApiSampleInfo apiSample = new ApiSampleInfo(sample);
      setOtherFieldsForOutgoingApiInventoryRecord(apiSample, sample, user);
      sampleInfos.add(apiSample);
    }

    ApiSampleSearchResult apiSearchResult = new ApiSampleSearchResult();
    apiSearchResult.setTotalHits(dbSamples.getTotalHits());
    apiSearchResult.setPageNumber(dbSamples.getPageNumber());
    apiSearchResult.setItems(sampleInfos);

    return apiSearchResult;
  }

  @Override
  public ApiSampleTemplateSearchResult getTemplatesForUser(
      PaginationCriteria<SampleTemplate> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user) {

    ISearchResults<SampleTemplate> dbTemplates =
        sampleTemplateDao.getTemplatesForUser(pgCrit, ownedBy, deletedOption, user);
    List<ApiSampleTemplateInfo> templateInfos = new ArrayList<>();
    for (SampleTemplate st : dbTemplates.getResults()) {
      ApiSampleTemplateInfo apiInvRec = new ApiSampleTemplateInfo(st);
      setOtherFieldsForOutgoingApiInventoryRecord(apiInvRec, st, user);
      templateInfos.add(apiInvRec);
    }

    ApiSampleTemplateSearchResult apiSearchResult = new ApiSampleTemplateSearchResult();
    apiSearchResult.setTotalHits(dbTemplates.getTotalHits());
    apiSearchResult.setPageNumber(dbTemplates.getPageNumber());
    apiSearchResult.setItems(templateInfos);

    return apiSearchResult;
  }

  @Override
  public boolean exists(long id) {
    return sampleDao.exists(id) || sampleTemplateDao.exists(id);
  }

  @Override
  public Sample assertUserCanReadSample(Long id, User user) {
    Sample sample = getSampleOrThrowNotFound(id);
    invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(sample, user);
    return sample;
  }

  @Override
  public Sample assertUserCanEditSample(Long id, User user) {
    Sample sample = getSampleOrThrowNotFound(id);
    invPermissions.assertUserCanEditInventoryRecord(sample, user);
    return sample;
  }

  @Override
  public Sample assertUserCanDeleteSample(Long id, User user) {
    Sample sample = getSampleOrThrowNotFound(id);
    invPermissions.assertUserCanDeleteInventoryRecord(sample, user);
    return sample;
  }

  @Override
  public Sample assertUserCanTransferSample(Long id, User user) {
    Sample sample = getSampleOrThrowNotFound(id);
    invPermissions.assertUserCanTransferInventoryRecord(sample, user);
    return sample;
  }

  @Override
  public SampleTemplate assertUserCanReadSampleTemplate(Long id, User user) {
    SampleTemplate template = getSampleTemplateOrThrowNotFound(id);
    invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(template, user);
    return template;
  }

  @Override
  public SampleTemplate assertUserCanEditSampleTemplate(Long id, User user) {
    SampleTemplate template = getSampleTemplateOrThrowNotFound(id);
    invPermissions.assertUserCanEditInventoryRecord(template, user);
    return template;
  }

  @Override
  public SampleTemplate assertUserCanDeleteSampleTemplate(Long id, User user) {
    SampleTemplate template = getSampleTemplateOrThrowNotFound(id);
    invPermissions.assertUserCanDeleteInventoryRecord(template, user);
    return template;
  }

  @Override
  public SampleTemplate assertUserCanTransferSampleTemplate(Long id, User user) {
    SampleTemplate template = getSampleTemplateOrThrowNotFound(id);
    invPermissions.assertUserCanTransferInventoryRecord(template, user);
    return template;
  }

  /**
   * Returns the {@link Sample} (not a template) with the given id, or throws {@link
   * NotFoundException} if no sample exists with that id. A {@link SampleTemplate} that shares the
   * id space is treated as absent: {@code sampleDao} is anchored on {@code DTYPE='Sample'}.
   */
  private Sample getSampleOrThrowNotFound(Long id) {
    if (!sampleDao.exists(id)) {
      throw new NotFoundException("No sample with id: " + id);
    }
    Sample sample = sampleDao.get(id);
    for (SubSample ss : sample.getSubSamples()) {
      populateSubSampleParentContainerChain(ss);
    }
    return sample;
  }

  /**
   * Returns the {@link SampleTemplate} with the given id, or throws {@link NotFoundException} if no
   * template exists with that id. A plain {@link Sample} that shares the id space is treated as
   * absent: {@code sampleTemplateDao} is anchored on {@code DTYPE='SampleTemplate'}.
   */
  private SampleTemplate getSampleTemplateOrThrowNotFound(Long id) {
    if (!sampleTemplateDao.exists(id)) {
      throw new NotFoundException("No sample template with id: " + id);
    }
    return sampleTemplateDao.get(id);
  }

  @Override
  public boolean nameExistsForUser(String sampleName, User user) {
    return sampleDao.entityNameExistsForUser(sampleName, user);
  }

  @Override
  public ApiSampleWithFullSubSamples createNewApiSample(
      ApiSampleWithFullSubSamples apiSample, User user) {

    SampleTemplate template = getSampleTemplateIfExists(apiSample, user);

    String sampleName = getNameForIncomingApiSample(apiSample);
    return createSample(sampleName, apiSample, template, user);
  }

  private SampleTemplate getSampleTemplateIfExists(
      ApiSampleWithFullSubSamples apiSample, User user) {
    Long templateId = apiSample.getTemplateId();
    // templateId is null when creating a sample with no template; when provided, the caller must
    // have read access to it (enforced, not just existence-checked)
    if (templateId == null) {
      return null;
    }
    return assertUserCanReadSampleTemplate(templateId, user);
  }

  private String getNameForIncomingApiSample(ApiSampleInfo prototypeSample) {
    return StringUtils.isNotBlank(prototypeSample.getName())
        ? prototypeSample.getName()
        : SAMPLE_DEFAULT_NAME;
  }

  private ApiSampleWithFullSubSamples createSample(
      String sampleName,
      ApiSampleWithFullSubSamples apiSample,
      SampleTemplate sampleTemplate,
      User user) {
    Sample sample =
        sampleTemplate != null
            ? recordFactory.createSample(sampleName, user, sampleTemplate)
            : recordFactory.createSample(sampleName, user);

    setBasicFieldsFromNewIncomingApiInventoryRecord(sample, apiSample, user);
    if (sampleTemplate != null) {
      // might be null from incoming API request, but here we want to reference template icon id
      sample.setIconId(sampleTemplate.getIconId());
    }
    setSampleCoreProperties(apiSample, sample);
    List<ApiSubSample> apiSubSamples = apiSample.getSubSamples();
    if (apiSample.getQuantity() != null) {
      // set quantity of default subsample from provided sample quantity
      if (apiSubSamples.isEmpty()) {
        sample.getOnlySubSample().get().setQuantity(apiSample.getQuantity().toQuantityInfo());
      }
      // set quantity of single provided subsample that has no own quantity
      if (apiSubSamples.size() == 1 && apiSubSamples.get(0).getQuantity() == null) {
        apiSubSamples.get(0).setQuantity(apiSample.getQuantity());
      }
    }
    if (!apiSample.getFields().isEmpty()) {
      saveNewApiFieldsIntoSampleFields(apiSample.getFields(), sample.getActiveFields(), user);
    } else {
      assertDefaultFieldsValid(sample.getActiveFields());
    }

    if (apiSubSamples.isEmpty()) {
      // keep default subsample, but rename it with common method for consistency
      sample
          .getSubSamples()
          .get(0)
          .setName(InventorySeriesNamingHelper.getSerialNameForSubSample(sample.getName(), 1, 1));
    } else {
      // use provided apiSubSamples
      List<SubSample> newSubSamples = new ArrayList<>();
      int subSampleCount = 1;
      int subSampleTotal = apiSubSamples.size();
      for (ApiSubSample apiSubSample : apiSubSamples) {
        String subSampleName = apiSubSample.getName();
        if (StringUtils.isBlank(subSampleName)) {
          subSampleName =
              InventorySeriesNamingHelper.getSerialNameForSubSample(
                  sample.getName(), subSampleCount++, subSampleTotal);
        }
        SubSample subSample =
            createSubSampleFromIncomingApiSample(subSampleName, apiSubSample, sample, user);
        newSubSamples.add(subSample);
      }
      sample.setSubSamples(newSubSamples);
    }
    setWorkbenchAsParentForNewSubSamples(sample, user);
    sample.recalculateTotalQuantity();

    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNames(sample);
    Sample savedSample = sampleDao.persistNewSample(sample);
    saveIncomingSampleImage(savedSample, apiSample, user);
    publishAuditEventsForCreatedSample(user, savedSample);

    ApiSampleWithFullSubSamples apiResultSample = new ApiSampleWithFullSubSamples(savedSample);
    if (sampleTemplate != null) {
      apiResultSample.setTemplateId(sampleTemplate.getId());
    }
    populateOutgoingApiSampleWithFullSubSamples(apiResultSample, savedSample, user);

    return apiResultSample;
  }

  private void assertDefaultFieldsValid(List<InventoryEntityField> activeFields) {
    for (InventoryEntityField field : activeFields) {
      if (field instanceof InventoryLinkField) {
        // Link fields hold their value in the InventoryLink object, not fieldData (which is always
        // empty for them), and a mandatory link is legitimately unfilled when a sample is first
        // created from a template (the same state "update all samples" leaves pre-existing samples
        // in); it is filled by a later link update. assertFieldDataValid would reject the empty
        // content as a missing mandatory value, so skip the fieldData check for link fields - this
        // mirrors the LINK special-casing in ApiFieldsHelper.validateMandatoryFieldsForEntityPost.
        continue;
      }
      field.assertFieldDataValid(field.getFieldData());
    }
  }

  private void setWorkbenchAsParentForNewSubSamples(Sample sample, User user) {
    Container workbench = containerDao.getWorkbenchForUser(user);
    for (SubSample ss : sample.getSubSamples()) {
      setWorkbenchAsParentForNewInventoryRecord(workbench, ss);
    }
  }

  private void setSampleCoreProperties(ApiSampleInfo apiSample, SampleEntity sample) {

    if (apiSample.getStorageTempMin() != null) {
      sample.setStorageTempMin(apiSample.getStorageTempMin().toQuantityInfo());
    }
    if (apiSample.getStorageTempMax() != null) {
      sample.setStorageTempMax(apiSample.getStorageTempMax().toQuantityInfo());
    }
    if (apiSample.getSampleSource() != null) {
      sample.setSampleSource(apiSample.getSampleSource());
    }
    if (apiSample.getExpiryDate() != null) {
      LocalDate newExpiryDate = apiSample.getExpiryDate();
      if (newExpiryDate.equals(LocalDateDeserialiser.NULL_DATE)) {
        newExpiryDate = null;
      }
      sample.setExpiryDate(newExpiryDate);
    }
  }

  private SubSample createSubSampleFromIncomingApiSample(
      String subSampleName, ApiSubSample apiSubSample, Sample sample, User user) {
    SubSample subSample = recordFactory.createSubSample(subSampleName, user, sample);
    setBasicFieldsFromNewIncomingApiInventoryRecord(subSample, apiSubSample, user);
    inventoryMoveHelper.moveRecordToTargetParentAndLocation(
        subSample, apiSubSample.getParentContainer(), apiSubSample.getParentLocation(), user);

    if (apiSubSample.getQuantity() != null) {
      subSample.setQuantity(apiSubSample.getQuantity().toQuantityInfo());
    }
    if (apiSubSample.getNotes() != null) {
      for (ApiSubSampleNote apiNote : apiSubSample.getNotes()) {
        subSample.addNote(apiNote.getContent(), user);
      }
    }
    return subSample;
  }

  private void saveNewApiFieldsIntoSampleFields(
      List<? extends ApiInventoryEntityField> apiFieldList,
      List<InventoryEntityField> inventoryEntityFieldList,
      User user) {

    if (apiFieldList.size() != inventoryEntityFieldList.size()) {
      throw new IllegalArgumentException(
          String.format(
              "Number of incoming sample fields [%d]"
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
   * Applies a sample's chosen link value to its structured link field, going through the {@link
   * InventoryLinkManager} so the target is parsed/validated and the Envers revision captured (the
   * same path used by extra-field links). An unchanged payload is a no-op (previously every save
   * replaced the row, resetting its identity and creation date); a changed payload updates the
   * field's existing InventoryLink row in place; clearing the value dereferences the row, which the
   * field's {@code orphanRemoval} mapping hard-deletes at flush (an Envers DEL revision keeps the
   * history in {@code InventoryLink_AUD}; a prior soft-delete write would be collapsed into that
   * same DEL revision, so none is attempted). This differs deliberately from the extra-field delete
   * path, where the FIELD itself is soft-deleted and its link row therefore survives soft-deleted
   * alongside it. The chosen relation type must be permitted by the template field's
   * allowed-relation-types whitelist (an empty whitelist permits all).
   */
  boolean applyLinkFieldValue(
      InventoryLinkField field, ApiInventoryEntityField apiField, User user) {
    ApiInventoryLink apiLink = apiField.getLink();
    String target = apiLink == null ? null : apiLink.getTargetGlobalId();
    InventoryLink existing = field.getLink();
    if (target == null || target.trim().isEmpty()) {
      if (existing == null) {
        return false; // no link before, none requested now
      }
      field.setLink(null); // orphanRemoval hard-deletes the dereferenced row at flush
      return true;
    }
    Long effectivePin =
        apiLink.derivedVersionPin() != null ? apiLink.derivedVersionPin() : apiLink.getVersionPin();
    // compare on the parsed base id, not the raw string: the stored row holds the
    // unsuffixed id (the pin lives in versionPin), so a suffixed incoming id like
    // "SA2v4" would otherwise never compare equal and every save would fire a
    // spurious update (and Envers revision). Mirrors ApiExtraFieldsHelper.linkChanged.
    GlobalIdentifier incoming = parseTargetOrNull(target);
    if (existing != null
        && incoming != null
        && incoming.getPrefix() == existing.getTargetPrefix()
        && Objects.equals(incoming.getDbId(), existing.getTargetDbId())
        && Objects.equals(effectivePin, existing.getVersionPin())
        && Objects.equals(apiLink.getRelationType(), existing.getRelationType())) {
      return false; // unchanged
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
   * Applies link values to an existing sample's structured link fields (the update path). The DTO
   * apply loop leaves link fields untouched because it cannot reach the service-layer {@link
   * InventoryLinkManager}; this matches each modified link field by id and applies it here.
   */
  private boolean applyLinkFieldValuesOnUpdate(
      ApiSampleWithoutSubSamples apiSample, Sample dbSample, User user) {
    if (apiSample.getFields() == null) {
      return false;
    }
    boolean changed = false;
    for (ApiInventoryEntityField apiField : apiSample.getFields()) {
      if (apiField.isNewFieldRequest()
          || apiField.isDeleteFieldRequest()
          || apiField.getId() == null) {
        continue;
      }
      Optional<InventoryEntityField> dbFieldOpt =
          dbSample.getActiveFields().stream()
              .filter(
                  f ->
                      f instanceof InventoryLinkField
                          && Objects.equals(f.getId(), apiField.getId()))
              .findFirst();
      if (dbFieldOpt.isPresent()) {
        rejectSelfLink(apiField.getLink(), dbSample);
        changed |= applyLinkFieldValue((InventoryLinkField) dbFieldOpt.get(), apiField, user);
      }
    }
    return changed;
  }

  private void assertRelationAllowed(InventoryLinkField field, String relationType) {
    // a chosen relation must be a real DataCite relation type, even when the whitelist is empty.
    // ApiRuntimeException maps to a 422 with the resolved bundle message, where a raw
    // IllegalArgumentException would surface as an unmapped 500.
    if (!DataCiteRelationType.isValid(relationType)) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.relationTypeInvalid", relationType);
    }
    String allowed = field.getAllowedRelationTypes();
    if (allowed == null || allowed.trim().isEmpty()) {
      return; // empty whitelist = all relation types allowed
    }
    if (!Arrays.asList(allowed.split("\\|")).contains(relationType)) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.relationTypeNotPermitted", relationType, field.getName());
    }
  }

  private void rejectSelfLink(ApiInventoryLink apiLink, Sample dbSample) {
    if (apiLink == null || dbSample.getOid() == null) {
      return;
    }
    GlobalIdentifier target = parseTargetOrNull(apiLink.getTargetGlobalId());
    if (target == null) {
      return; // malformed/blank targets are handled by the manager / clear path
    }
    if (InventoryLinkValidator.isSelfLink(target, dbSample.getOid().toString())) {
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

  /**
   * Soft-deletes the {@link InventoryLink} backing a structured link field once that field has been
   * soft-deleted, so the link row (and its Envers audit trail) stays in step with the field. The
   * field's {@code deleted} flag is flipped in the model layer (a template link-field delete, or
   * its propagation to child samples through {@code Sample#updateToLatestTemplateVersion}), which
   * cannot reach the service-layer {@link InventoryLinkManager}; a soft-delete is also an ordinary
   * update rather than a JPA remove, so the {@code cascade}/{@code orphanRemoval} on {@code
   * InventoryLinkField#link} never fires. Without this the link row would linger with {@code
   * deleted=false} after its field is gone. No-op unless the field is a deleted {@link
   * InventoryLinkField} whose link is still live.
   */
  void softDeleteLinkOfDeletedLinkField(InventoryEntityField field, User user) {
    if (field instanceof InventoryLinkField && field.isDeleted()) {
      InventoryLink link = ((InventoryLinkField) field).getLink();
      if (link != null && !link.isDeleted()) {
        inventoryLinkManager.deleteLink(link, user);
      }
    }
  }

  private void publishAuditEventsForCreatedSample(User user, Sample savedSample) {
    publisher.publishEvent(new InventoryCreationEvent(savedSample, user));
    for (SubSample subSample : savedSample.getSubSamples()) {
      publisher.publishEvent(new InventoryCreationEvent(subSample, user));
      Container subSampleParent = subSample.getParentContainer();
      if (subSampleParent != null
          && !ContainerType.WORKBENCH.equals(subSampleParent.getContainerType())) {
        publisher.publishEvent(new InventoryMoveEvent(subSample, null, subSampleParent, user));
      }
    }
  }

  @Override
  public ApiSample getApiSampleById(Long id, User user) {
    return doGetSample(id, user, false);
  }

  private ApiSample doGetSample(Long id, User user, boolean asTemplate) {
    SampleEntity sample = getIfExists(id);
    if (asTemplate != sample.isSampleTemplate()) {
      throw new IllegalArgumentException(
          String.format("Sample template flag doesn't match the request (id %d)", id));
    }
    publisher.publishEvent(new InventoryAccessEvent(sample, user));
    return getOutgoingApiSample(sample, user);
  }

  private ApiSample getOutgoingApiSample(SampleEntity sample, User user) {
    ApiSample result =
        sample.isSampleTemplate()
            ? new ApiSampleTemplate((SampleTemplate) sample)
            : new ApiSample((Sample) sample);
    populateOutgoingApiSample(result, sample, user);
    return result;
  }

  private void populateOutgoingApiSample(ApiSample apiSample, SampleEntity sample, User user) {
    if (apiSample == null) {
      return;
    }
    setOtherFieldsForOutgoingApiInventoryRecord(apiSample, sample, user);
    populateSharingPermissions(apiSample.getSharedWith(), sample);
    if (!apiSample.isTemplate()) {
      List<ApiSubSampleInfo> apiSubSamples = apiSample.getSubSamples();
      if (apiSubSamples != null) {
        for (int i = 0; i < apiSubSamples.size(); i++) {
          setOtherFieldsForOutgoingApiInventoryRecord(
              apiSubSamples.get(i), sample.getSubSamples().get(i), user);
        }
      }
    } else if (apiSample instanceof ApiSampleTemplate) {
      setSamplesToUpdateCount((ApiSampleTemplate) apiSample, (SampleTemplate) sample, user);
    }
  }

  /**
   * Records, on the outgoing template DTO, how many of {@code user}'s samples were created from an
   * older version of this template and could therefore be updated to its latest version. This is
   * the same "behind" set the bulk update endpoint acts on, so the count is 0 exactly when there is
   * nothing to update - notably for a template that is merely the target of a link from some sample
   * (that sample is not created from the template, so it is not counted).
   */
  void setSamplesToUpdateCount(ApiSampleTemplate apiTemplate, SampleTemplate template, User user) {
    apiTemplate.setSamplesToUpdateCount(
        sampleDao
            .getSamplesLinkingOlderTemplateVersionForUser(
                template.getId(), template.getVersion(), user)
            .size());
  }

  private void populateOutgoingApiSampleWithFullSubSamples(
      ApiSampleWithFullSubSamples apiSample, Sample sample, User user) {
    setOtherFieldsForOutgoingApiInventoryRecord(apiSample, sample, user);
    populateSharingPermissions(apiSample.getSharedWith(), sample);
    List<ApiSubSample> apiResultSubSamples = apiSample.getSubSamples();
    for (int i = 0; i < apiResultSubSamples.size(); i++) {
      setOtherFieldsForOutgoingApiInventoryRecord(
          apiResultSubSamples.get(i), sample.getSubSamples().get(i), user);
    }
  }

  @Override
  public ApiInventorySearchResult searchSubSamplesBySampleId(
      Long sampleId,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user) {

    SampleEntity sample = getIfExists(sampleId);
    boolean canRead = invPermissions.canUserReadInventoryRecord(sample, user);

    if (!canRead
        || StringUtils.isNotBlank(ownedBy) && !ownedBy.equals(sample.getOwner().getUsername())) {
      // sample's owner owns all subsamples, so if not matching, then return empty result
      return ApiInventorySearchResult.emptyResult();
    }

    List<SubSample> subSamples;
    switch (deletedOption) {
      case DELETED_ONLY:
        subSamples = sample.getDeletedSubSamples();
        break;
      case INCLUDE:
        subSamples = sample.getSubSamples();
        break;
      case EXCLUDE:
      default:
        subSamples = sample.getActiveSubSamples();
    }
    return sortRepaginateConvertToApiInventorySearchResult(pgCrit, subSamples, user);
  }

  @Override
  public ApiInventorySearchResult getSamplesCreatedFromTemplate(
      Long templateId,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      PaginationCriteria<Sample> pgCrit,
      User user) {

    SampleEntity template = getIfExists(templateId);
    boolean canRead = invPermissions.canUserReadInventoryRecord(template, user);
    if (!canRead) {
      return ApiInventorySearchResult
          .emptyResult(); // no searches for samples created from unreadable template
    }

    ISearchResults<Sample> dbSamples =
        sampleDao.getSamplesForUser(pgCrit, templateId, ownedBy, deletedOption, user);

    return convertToApiInventorySearchResult(
        dbSamples.getTotalHits(),
        (pgCrit != null ? pgCrit.getPageNumber().intValue() : 0),
        dbSamples.getResults(),
        user);
  }

  @Override
  public List<ApiInventoryRecordInfo> getSamplesLinkingOldTemplateVersion(
      Long templateId, User user) {
    SampleEntity template = getIfExists(templateId, true);
    List<Sample> samples =
        sampleDao.getSamplesLinkingOlderTemplateVersionForUser(
            templateId, template.getVersion(), user);

    return samples.stream()
        .map(ApiInventoryRecordInfo::fromInventoryRecord)
        .collect(Collectors.toList());
  }

  @Override
  public SampleEntity getIfExists(Long id) {
    return getIfExists(id, false);
  }

  private SampleEntity getIfExists(Long id, boolean onlyIfTemplate) {
    SampleEntity s;
    if (sampleDao.exists(id)) {
      s = sampleDao.get(id);
    } else if (sampleTemplateDao.exists(id)) {
      s = sampleTemplateDao.get(id);
    } else {
      throw new NotFoundException(
          "No sample " + (onlyIfTemplate ? "template " : "") + "with id: " + id);
    }
    if (onlyIfTemplate && !s.isTemplate()) {
      throw new NotFoundException("No sample template with id: " + id);
    }
    for (SubSample ss : s.getSubSamples()) {
      populateSubSampleParentContainerChain(ss);
    }
    return s;
  }

  @Override
  public ApiSample updateApiSample(ApiSampleWithoutSubSamples apiSample, User user) {
    // assertUserCanEditSample returns a Sample (a template id 404s), so ApiSample is safe
    SampleEntity dbSample = assertUserCanEditSample(apiSample.getId(), user);
    ApiSample original = new ApiSample(dbSample);

    boolean temporaryLock = lockItemForEdit(dbSample, user);
    try {
      dbSample = getIfExists(dbSample.getId());
      updateDbSample(apiSample, dbSample, false, user);
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbSample, user);
      }
    }

    ApiSample sample = getOutgoingApiSample(dbSample, user);
    updateOntologyOnUpdate(original, sample, user);
    return sample;
  }

  @Override
  public ApiSample changeApiSampleOwner(ApiSampleInfo apiSample, User user) {
    Validate.notNull(apiSample.getOwner(), "'owner' field not present");
    Validate.notNull(apiSample.getOwner().getUsername(), "'owner.username' field not present");

    invPermissions.assertUserCanTransferInventoryRecord(getIfExists(apiSample.getId()), user);

    SampleEntity dbSample = getIfExists(apiSample.getId());
    boolean temporaryLock = lockItemForEdit(dbSample, user);

    try {
      dbSample = getIfExists(dbSample.getId());
      User originalOwner = dbSample.getOwner();
      String newOwnerUsername = apiSample.getOwner().getUsername();

      if (!originalOwner.getUsername().equals(newOwnerUsername)) {
        Validate.isTrue(
            userManager.userExists(newOwnerUsername),
            "Target user [" + newOwnerUsername + "] not found");
        User newOwner = userManager.getUserByUsername(newOwnerUsername);
        dbSample.setOwner(newOwner);
        dbSample
            .getActiveSubSamples()
            .forEach(ss -> moveItemBetweenWorkbenches(ss, originalOwner, newOwner));
        if (dbSample.isSampleTemplate()) {
          dbSample = sampleTemplateDao.saveAndReindexSubSamples((SampleTemplate) dbSample);
        } else {
          dbSample = sampleDao.saveAndReindexSubSamples((Sample) dbSample);
        }
        publisher.publishEvent(new InventoryTransferEvent(dbSample, user, originalOwner, newOwner));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbSample, user);
      }
    }

    ApiSample changedOwnerSample = getOutgoingApiSample(dbSample, user);
    updateOntologyOnRecordChanges(changedOwnerSample, user);
    return changedOwnerSample;
  }

  private void updateDbSample(
      ApiSampleWithoutSubSamples apiSample,
      SampleEntity dbSample,
      boolean alreadyChanged,
      User user) {
    boolean contentChanged = alreadyChanged;
    contentChanged |=
        extraFieldHelper.createDeleteRequestedExtraFieldsInDatabaseSample(
            apiSample, dbSample, user);
    contentChanged |=
        barcodesHelper.createDeleteRequestedBarcodes(apiSample.getBarcodes(), dbSample, user);
    contentChanged |=
        identifiersHelper.createDeleteRequestedIdentifiers(
            apiSample.getIdentifiers(), dbSample, user);
    contentChanged |=
        identifiersHelper.createAssignRequestedIdentifiers(
            apiSample.getIdentifiers(), dbSample, user);
    contentChanged |= apiSample.applyChangesToDatabaseSample(dbSample, user);
    if (!dbSample.isTemplate()) {
      contentChanged |= applyLinkFieldValuesOnUpdate(apiSample, (Sample) dbSample, user);
    }
    contentChanged |= saveSharingACLForIncomingApiInvRec(dbSample, apiSample);
    contentChanged |= saveIncomingSampleImage(dbSample, apiSample, user);
    dbSample.refreshActiveFieldsAndColumnIndex();
    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNames(dbSample);
    if (contentChanged) {
      saveDbSampleUpdate(dbSample, user);
    }
  }

  @Override
  public void saveDbSampleUpdate(SampleEntity dbSample, User user) {
    dbSample.setModificationDate(new Date());
    dbSample.setModifiedBy(user.getUsername(), IActiveUserStrategy.CHECK_OPERATE_AS);
    dbSample.increaseVersion();
    dbSample = saveSampleEntity(dbSample);
    publisher.publishEvent(new InventoryEditingEvent(dbSample, user));
  }

  /** Routes a plain save to the DAO matching the entity's concrete kind. */
  private SampleEntity saveSampleEntity(SampleEntity dbSample) {
    if (dbSample.isSampleTemplate()) {
      return sampleTemplateDao.save((SampleTemplate) dbSample);
    }
    return sampleDao.save((Sample) dbSample);
  }

  @Override
  public ApiSample markSampleAsDeleted(Long sampleId, boolean forceDelete, User user) {
    SampleEntity dbSample = getIfExists(sampleId);
    invPermissions.assertUserCanDeleteInventoryRecord(dbSample, user);
    boolean temporaryLock = lockItemForEdit(dbSample, user);

    try {
      dbSample = getIfExists(dbSample.getId());
      if (!dbSample.isDeleted()) {
        long ssInContainersCount =
            dbSample.getActiveSubSamples().stream()
                .filter(MovableInventoryRecord::isStoredInContainer)
                .count();
        if (forceDelete || ssInContainersCount == 0) {
          dbSample
              .getActiveSubSamples()
              .forEach(ss -> subSampleMgr.markSubSampleAsDeleted(ss.getId(), user, true));
          dbSample.refreshActiveSubSamples();
          dbSample.recalculateTotalQuantity();

          /* then delete the sample */
          dbSample.setRecordDeleted(true);
          dbSample = saveSampleEntity(dbSample);
          publisher.publishEvent(new InventoryDeleteEvent(dbSample, user));
        }
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbSample, user);
      }
    }
    ApiSample apiSampleResult = getOutgoingApiSample(dbSample, user);
    updateOntologyOnRecordChanges(apiSampleResult, user);
    return apiSampleResult;
  }

  @Override
  public ApiSample restoreDeletedSample(
      Long sampleId, User user, boolean includeSubSamplesDeletedOnSampleDeletion) {
    SampleEntity dbSample = getIfExists(sampleId);
    invPermissions.assertUserCanDeleteInventoryRecord(dbSample, user);
    boolean temporaryLock = lockItemForEdit(dbSample, user);
    try {
      dbSample = getIfExists(dbSample.getId());
      if (dbSample.isDeleted()) {
        for (SubSample ss : dbSample.getActiveSubSamples()) {
          if (includeSubSamplesDeletedOnSampleDeletion) {
            subSampleMgr.restoreDeletedSubSample(ss.getId(), user, true);
          } else {
            // forget the 'deletedOnSampleDeletion' status
            ss.setDeletedOnSampleDeletion(false);
          }
        }
        dbSample.setRecordDeleted(false);
        dbSample.refreshActiveSubSamples();
        dbSample.recalculateTotalQuantity();
        dbSample = saveSampleEntity(dbSample);
        publisher.publishEvent(new InventoryRestoreEvent(dbSample, user));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbSample, user);
      }
    }

    ApiSample restored = getOutgoingApiSample(dbSample, user);
    updateOntologyOnRecordChanges(restored, user);
    return restored;
  }

  /**
   * Save incoming sample image.
   *
   * @throws IOException
   * @returns true if any images were saved
   */
  private boolean saveIncomingSampleImage(
      SampleEntity dbSample, ApiSampleInfo apiSample, User user) {
    if (dbSample.isTemplate()) {
      return saveIncomingImage(
          dbSample,
          apiSample,
          user,
          SampleTemplate.class,
          template -> sampleTemplateDao.save(template));
    }
    return saveIncomingImage(
        dbSample, apiSample, user, Sample.class, sample -> sampleDao.save(sample));
  }

  @Override
  public SampleEntity getSampleById(Long id, User user) {
    return getIfExists(id);
  }

  @Override
  public ApiSampleWithFullSubSamples duplicate(Long sampleId, User user) {
    SampleEntity copy = copyDbSample(sampleId, user);
    // cast is safe: copyDbSample persists and returns a Sample for non-template ids (controller
    // guards the endpoint)
    return new ApiSampleWithFullSubSamples((Sample) copy);
  }

  @Override
  public ApiSampleTemplate duplicateTemplate(Long sampleId, User user) {
    SampleEntity copy = copyDbSample(sampleId, user);
    // cast is safe: copyDbSample persists and returns a SampleTemplate for template ids (controller
    // guards the endpoint)
    return new ApiSampleTemplate((SampleTemplate) copy);
  }

  private SampleEntity copyDbSample(Long sampleId, User user) {
    SampleEntity dbSample = getIfExists(sampleId);
    invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(dbSample, user);
    SampleEntity copy;
    if (dbSample.isSample()) {
      Sample sampleCopy = ((Sample) dbSample).copy(user);
      setWorkbenchAsParentForNewSubSamples(sampleCopy, user);
      copy = sampleDao.persistNewSample(sampleCopy);
    } else {
      SampleTemplate templateCopy = ((SampleTemplate) dbSample).copy(user);
      /* persistSampleTemplate's choice/radio-definition pre-save is a no-op here: the copied
       * fields share the original template's already-persistent definitions, so this matches
       * the plain persist the legacy persistNewSample call performed for template copies. */
      copy = sampleTemplateDao.persistSampleTemplate(templateCopy);
    }
    publisher.publishEvent(new InventoryCreationEvent(copy, user));
    return copy;
  }

  @Override
  public List<SampleTemplate> getAllTemplates(User user) {
    PaginationCriteria<SampleTemplate> pg =
        PaginationCriteria.createDefaultForClass(SampleTemplate.class);
    pg.setGetAllResults();
    return sampleTemplateDao.getTemplatesForUser(pg, null, null, user).getResults();
  }

  @Override
  public ApiSampleTemplate getApiSampleTemplateById(Long id, User user) {
    return (ApiSampleTemplate) doGetSample(id, user, true);
  }

  @Override
  public SampleTemplate getSampleTemplateByIdWithPopulatedFields(Long id, User user) {
    // enforce read/limited-read permission (not just existence) before returning the template
    SampleTemplate template = assertUserCanReadSampleTemplate(id, user);
    template.getActiveFields().size(); // initialize lazy-loaded collection
    return template;
  }

  @Override
  public ApiSampleTemplate getApiSampleTemplateVersion(Long templateId, Long version, User user) {
    SampleEntity currentVersion = getIfExists(templateId);
    if (!currentVersion.isSampleTemplate()) {
      throw new IllegalArgumentException(
          String.format("Requested id (%d) points to the sample, not template", templateId));
    }
    if (version.equals(currentVersion.getVersion())) {
      return getApiSampleTemplateById(templateId, user);
    }

    ApiSampleTemplate apiTemplateVersion =
        inventoryAuditMgr.getApiTemplateVersion((SampleTemplate) currentVersion, version);
    populateOutgoingApiSample(apiTemplateVersion, currentVersion, user);
    return apiTemplateVersion;
  }

  @Override
  public ApiSample getApiSampleVersion(Long sampleId, Long version, User user) {
    // Intentionally no explicit read-permission assert: like the live getApiSampleById read,
    // access control is enforced downstream by setOtherFieldsForOutgoingApiInventoryRecord, which
    // reduces the response to a public-view whitelist for a user without read permission. Do not
    // drop that reduction in a refactor or this would leak full historical data. (The sibling
    // /revisions endpoint asserts at the controller instead and hard-errors; the same data is
    // exposed either way, only the HTTP status differs.)
    SampleEntity currentSample = getIfExists(sampleId);
    if (version.equals(currentSample.getVersion())) {
      return getApiSampleById(sampleId, user);
    }
    // joins this transaction (REQUIRED propagation), so currentSample stays session-attached;
    // historical reads intentionally publish no InventoryAccessEvent, matching the
    // template-version precedent
    ApiSample apiSampleVersion = inventoryAuditMgr.getApiSampleVersion(currentSample, version);
    populateOutgoingHistoricalApiSample(apiSampleVersion, currentSample, user);
    return apiSampleVersion;
  }

  /**
   * As populateOutgoingApiSample, but for a historical snapshot: permissions are evaluated against
   * the live sample, and the snapshot's subsample listing (which may differ from the live one)
   * inherits the sample's permissions.
   */
  private void populateOutgoingHistoricalApiSample(
      ApiSample apiSample, SampleEntity currentSample, User user) {
    if (apiSample == null) {
      return;
    }
    setOtherFieldsForOutgoingApiInventoryRecord(apiSample, currentSample, user);
    populateSharingPermissions(apiSample.getSharedWith(), currentSample);
    if (apiSample.getSubSamples() != null) {
      for (ApiSubSampleInfo apiSubSample : apiSample.getSubSamples()) {
        setOtherFieldsForOutgoingApiInventoryRecord(apiSubSample, currentSample, user);
      }
    }
  }

  @Override
  public ApiSampleTemplate createSampleTemplate(ApiSampleTemplatePost apiSample, User user) {
    SampleTemplate sampleTemplate = recordFactory.createSampleTemplate(apiSample.getName(), user);
    sampleTemplate.setIconId(DEFAULT_ICON_ID);
    sampleTemplate.setSubSampleAliases(
        apiSample.getSubSampleAlias().getAlias(), apiSample.getSubSampleAlias().getPlural());
    sampleTemplate.setDefaultUnitId(apiSample.getDefaultUnitId());
    setBasicFieldsFromNewIncomingApiInventoryRecord(sampleTemplate, apiSample, user);
    setSampleCoreProperties(apiSample, sampleTemplate);
    createFields(apiSample, sampleTemplate);

    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNames(sampleTemplate);
    SampleTemplate savedSampleTemplate = sampleTemplateDao.persistSampleTemplate(sampleTemplate);
    saveIncomingSampleImage(savedSampleTemplate, apiSample, user);
    publisher.publishEvent(new InventoryCreationEvent(savedSampleTemplate, user));
    ApiSampleTemplate rc = new ApiSampleTemplate(savedSampleTemplate);
    setOtherFieldsForOutgoingApiInventoryRecord(rc, savedSampleTemplate, user);
    populateSharingPermissions(rc.getSharedWith(), savedSampleTemplate);
    return rc;
  }

  private void createFields(ApiSampleTemplatePost apiSample, SampleTemplate sample) {
    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
        apiSample.getFields(), null);
    for (ApiInventoryEntityField field : apiSample.getFields()) {
      InventoryEntityField toAdd = apiFieldToModelFieldFactory.apiInventoryFieldToModelField(field);
      sample.addSampleField(toAdd);
    }
  }

  @Override
  public ApiSampleTemplate updateApiSampleTemplate(ApiSampleTemplate apiSample, User user) {
    SampleTemplate dbTemplate = assertUserCanEditSampleTemplate(apiSample.getId(), user);

    boolean temporaryLock = lockItemForEdit(dbTemplate, user);
    try {
      // re-fetch by id returns the same template row
      dbTemplate = getSampleTemplateOrThrowNotFound(dbTemplate.getId());
      boolean contentChanged =
          createDeleteRequestedFieldsInDbSampleTemplate(apiSample, dbTemplate, user);
      contentChanged |= apiSample.applyChangesToDatabaseTemplate(dbTemplate, user);
      updateDbSample(apiSample, dbTemplate, contentChanged, user);
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbTemplate, user);
      }
    }

    return (ApiSampleTemplate) getOutgoingApiSample(dbTemplate, user);
  }

  private boolean createDeleteRequestedFieldsInDbSampleTemplate(
      ApiSampleWithoutSubSamples apiSample, SampleTemplate dbTemplate, User user) {
    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
        apiSample.getFields(), null);
    boolean changed = false;
    for (ApiInventoryEntityField apiField : apiSample.getFields()) {
      if (apiField.isNewFieldRequest()) {
        InventoryEntityField toAdd =
            apiFieldToModelFieldFactory.apiInventoryFieldToModelField(apiField);
        dbTemplate.addSampleField(toAdd);
        changed = true;
      }
      if (apiField.isDeleteFieldRequest()) {
        if (apiField.getId() == null) {
          throw new ApiRuntimeException("errors.inventory.field.deleteRequest.idMissing");
        }
        Optional<InventoryEntityField> dbFieldOpt =
            dbTemplate.getActiveFields().stream()
                .filter(sf -> apiField.getId().equals(sf.getId()))
                .findFirst();
        if (dbFieldOpt.isEmpty()) {
          throw new ApiRuntimeException(
              "errors.inventory.field.deleteRequest.idUnknown", apiField.getId());
        }
        dbTemplate.deleteSampleField(dbFieldOpt.get(), apiField.isDeleteFieldOnSampleUpdate());
        softDeleteLinkOfDeletedLinkField(dbFieldOpt.get(), user);
        changed = true;
      }
    }
    return changed;
  }

  @Override
  public ApiSample updateSampleToLatestTemplateVersion(Long sampleId, User user) {
    SampleEntity dbSample = assertUserCanEditSample(sampleId, user);
    // assertUserCanEditSample resolves a Sample (a template id 404s), so the cast is safe
    Long parentTemplateId = ((Sample) dbSample).getParentTemplateId();
    if (parentTemplateId == null) {
      throw new IllegalArgumentException("Sample is not based on any template");
    }
    SampleTemplate dbTemplate = assertUserCanReadSampleTemplate(parentTemplateId, user);

    boolean temporaryLock = lockItemForEdit(dbSample, user);
    try {
      // casts below are safe: templates throw earlier on the null parent-template id, and a
      // re-fetch by the same id cannot change the entity kind (discriminator is written only on
      // insert)
      dbSample = getIfExists(dbSample.getId());
      if (!dbTemplate.getVersion().equals(((Sample) dbSample).getSTemplateLinkedVersion())) {
        // Snapshot the link fields before the sync: propagating a deleted template link-field
        // marks the matching sample field deleted in the model layer, which cannot soft-delete the
        // field's InventoryLink. Reconcile those orphaned links here once the sync has run.
        List<InventoryLinkField> linkFieldsBeforeUpdate =
            dbSample.getActiveFields().stream()
                .filter(InventoryLinkField.class::isInstance)
                .map(InventoryLinkField.class::cast)
                .collect(Collectors.toList());
        ((Sample) dbSample).updateToLatestTemplateVersion();
        syncLinkFieldWhitelistsFromTemplate(dbSample.getActiveFields());
        linkFieldsBeforeUpdate.forEach(field -> softDeleteLinkOfDeletedLinkField(field, user));
        saveDbSampleUpdate(dbSample, user);
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbSample, user);
      }
    }

    return getApiSampleById(sampleId, user);
  }

  @Override
  public SampleEntity saveIconId(SampleEntity sample, Long iconId) {
    // either DAO works here: saveIconId's DML targets SampleEntity, covering both kinds
    sampleDao.saveIconId(sample, iconId);
    return sample;
  }
}
