package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentEntityInfo;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList.ApiInventoryRecordRevision;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.service.AuditManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("inventoryAuditApiManager")
public class InventoryAuditApiManagerImpl implements InventoryAuditApiManager {

  protected @Autowired AuditManager auditManager;
  protected @Autowired UserManager userManager;

  @Override
  public ApiInventoryRecordRevisionList getInventoryRecordRevisions(InventoryRecord currentInvRec) {

    Class<?> cls = currentInvRec.getClass();
    List<?> entityRevisions = auditManager.getRevisionsForEntity(cls, currentInvRec.getId());

    // memo so a record edited many times by the same user resolves that user's full name once
    Map<String, String> fullNameByUsername = new HashMap<>();
    ApiInventoryRecordRevisionList apiRevisions = new ApiInventoryRecordRevisionList();
    for (Object entityRevision : entityRevisions) {
      AuditedEntity<?> auditedEntity = (AuditedEntity<?>) entityRevision;
      InventoryRecord invRec = (InventoryRecord) auditedEntity.getEntity();

      initialiseInventoryRecordRelationships(invRec);

      long revisionId = auditedEntity.getRevision().longValue();
      ApiInventoryRecordInfo apiInvRec = ApiInventoryRecordInfo.fromInventoryRecord(invRec);
      apiInvRec.setRevisionId(revisionId);
      // fromInventoryRecord only knows the username; the version-history "By" column needs the
      // full name, resolved the same way as the live (non-revision) record path
      String modifiedBy = apiInvRec.getModifiedBy();
      if (modifiedBy != null) {
        apiInvRec.setModifiedByFullName(
            fullNameByUsername.computeIfAbsent(modifiedBy, userManager::getFullNameByUsername));
      }
      ApiInventoryRecordRevision apiRevision =
          new ApiInventoryRecordRevision(
              apiInvRec, revisionId, auditedEntity.getRevisionTypeString());
      apiRevisions.getRevisions().add(apiRevision);
    }
    apiRevisions.setRevisionsCount(entityRevisions.size());
    return apiRevisions;
  }

  @Override
  public ApiSample getApiSampleRevision(Long sampleId, Long revisionId) {
    ApiSample result = null;
    Sample sample = getInventoryRecordRevision(Sample.class, sampleId, revisionId);
    if (sample != null) {
      initialiseInventoryRecordRelationships(sample);
      result = (ApiSample) ApiInventoryRecordInfo.fromInventoryRecordToFullApiRecord(sample);
      result.setRevisionId(revisionId);
      result.setGlobalId(sample.getOidWithVersion().toString());
      for (ApiSubSampleInfo subSample : result.getSubSamples()) {
        subSample.setRevisionId(revisionId);
      }
    }
    return result;
  }

  @Override
  public ApiSubSample getApiSubSampleRevision(Long subSampleId, Long revisionId) {
    ApiSubSample result = null;
    SubSample subSample = getInventoryRecordRevision(SubSample.class, subSampleId, revisionId);
    if (subSample != null) {
      initialiseInventoryRecordRelationships(subSample);
      result = (ApiSubSample) ApiInventoryRecordInfo.fromInventoryRecordToFullApiRecord(subSample);
      result.setRevisionId(revisionId);
      result.setGlobalId(subSample.getOidWithVersion().toString());
    }
    return result;
  }

  @Override
  public ApiContainer getApiContainerRevision(Long containerId, Long revisionId) {
    ApiContainer result = null;
    Container container = getInventoryRecordRevision(Container.class, containerId, revisionId);
    if (container != null) {
      initialiseInventoryRecordRelationships(container);
      // locations are @NotAudited: a snapshot would lazily expose PRESENT-DAY contents,
      // so the historical view is built without content
      result = new ApiContainer(container, false);
      result.setRevisionId(revisionId);
      result.setGlobalId(container.getOidWithVersion().toString());
    }
    return result;
  }

  @Override
  public ApiSampleTemplate getApiTemplateVersion(Sample currTemplate, Long version) {
    if (version.equals(currTemplate.getVersion())) {
      return new ApiSampleTemplate(currTemplate);
    }

    Long lastRevisionForVersion = findNewestRevisionForVersion(currTemplate, version);
    if (lastRevisionForVersion == null) {
      return null;
    }
    ApiSampleTemplate result =
        (ApiSampleTemplate) getApiSampleRevision(currTemplate.getId(), lastRevisionForVersion);
    result.setHistoricalVersion(true);
    return result;
  }

  @Override
  public ApiSample getApiSampleVersion(Sample currentSample, Long version) {
    if (version.equals(currentSample.getVersion())) {
      return (ApiSample) ApiInventoryRecordInfo.fromInventoryRecordToFullApiRecord(currentSample);
    }
    Long revisionId = findNewestRevisionForVersion(currentSample, version);
    if (revisionId == null) {
      return null;
    }
    ApiSample result = getApiSampleRevision(currentSample.getId(), revisionId);
    if (result != null) {
      result.setHistoricalVersion(true);
    }
    return result;
  }

  @Override
  public ApiSubSample getApiSubSampleVersion(SubSample currentSubSample, Long version) {
    if (version.equals(currentSubSample.getVersion())) {
      return (ApiSubSample)
          ApiInventoryRecordInfo.fromInventoryRecordToFullApiRecord(currentSubSample);
    }
    Long revisionId = findNewestRevisionForVersion(currentSubSample, version);
    if (revisionId == null) {
      return null;
    }
    ApiSubSample result = getApiSubSampleRevision(currentSubSample.getId(), revisionId);
    if (result != null) {
      result.setHistoricalVersion(true);
    }
    return result;
  }

  @Override
  public ApiContainer getApiContainerVersion(Container currentContainer, Long version) {
    if (version.equals(currentContainer.getVersion())) {
      return new ApiContainer(currentContainer);
    }
    Long revisionId = findNewestRevisionForVersion(currentContainer, version);
    if (revisionId == null) {
      return null;
    }
    ApiContainer result = getApiContainerRevision(currentContainer.getId(), revisionId);
    if (result != null) {
      result.setHistoricalVersion(true);
    }
    return result;
  }

  @Override
  public ApiInstrument getApiInstrumentVersion(Instrument currentInstrument, Long version) {
    if (version.equals(currentInstrument.getVersion())) {
      return (ApiInstrument)
          ApiInventoryRecordInfo.fromInventoryRecordToFullApiRecord(currentInstrument);
    }
    Long revisionId = findNewestRevisionForVersion(currentInstrument, version);
    if (revisionId == null) {
      return null;
    }
    ApiInstrument result = getApiInstrumentRevision(currentInstrument.getId(), revisionId);
    if (result != null) {
      result.setHistoricalVersion(true);
    }
    return result;
  }

  /**
   * Finds the newest audit revision whose snapshot carries the given user-facing version, or null
   * if no revision matches. Non-version-bumping edits create several revisions sharing a version;
   * the version resolves to the last of them (the final state of that version).
   */
  private Long findNewestRevisionForVersion(InventoryRecord currentInvRec, Long version) {
    Number revision =
        auditManager.getRevisionNumberForInventoryRecordVersion(
            currentInvRec.getClass(), currentInvRec.getId(), version);
    return revision == null ? null : revision.longValue();
  }

  @Override
  public ApiInstrument getApiInstrumentRevision(Long instrumentId, Long revisionId) {
    Instrument instrument = getInventoryRecordRevision(Instrument.class, instrumentId, revisionId);
    if (instrument == null) {
      return null;
    }
    initialiseInventoryRecordRelationships(instrument);
    ApiInstrument result =
        (ApiInstrument) ApiInventoryRecordInfo.fromInventoryRecordToFullApiRecord(instrument);
    result.setRevisionId(revisionId);
    result.setGlobalId(instrument.getOidWithVersion().toString());
    return result;
  }

  @Override
  public ApiInstrumentTemplate getApiInstrumentTemplateRevision(Long templateId, Long revisionId) {
    InstrumentTemplate template =
        getInventoryRecordRevision(InstrumentTemplate.class, templateId, revisionId);
    if (template == null) {
      return null;
    }
    initialiseInventoryRecordRelationships(template);
    ApiInstrumentTemplate result =
        (ApiInstrumentTemplate) ApiInventoryRecordInfo.fromInventoryRecordToFullApiRecord(template);
    result.setRevisionId(revisionId);
    result.setGlobalId(template.getOidWithVersion().toString());
    return result;
  }

  @Override
  public ApiInstrumentTemplate getApiInstrumentTemplateVersion(
      InstrumentTemplate currTemplate, Long version) {
    if (version.equals(currTemplate.getVersion())) {
      return new ApiInstrumentTemplate(currTemplate);
    }

    List<ApiInventoryRecordRevision> templateRevisions =
        getInventoryRecordRevisions(currTemplate).getRevisions();
    List<ApiInventoryRecordRevision> versionRevisions =
        templateRevisions.stream()
            .filter(
                recRev ->
                    version.equals(((ApiInstrumentEntityInfo) recRev.getRecord()).getVersion()))
            .collect(Collectors.toList());
    if (versionRevisions.isEmpty()) {
      throw new NotFoundException(
          "No Instrument Template with id="
              + currTemplate.getId()
              + " and version="
              + version
              + " has been found");
    }

    Long lastRevisionForVersion = versionRevisions.get(versionRevisions.size() - 1).getRevisionId();
    ApiInstrumentTemplate result =
        getApiInstrumentTemplateRevision(currTemplate.getId(), lastRevisionForVersion);
    if (result != null) {
      result.setHistoricalVersion(true);
    }
    return result;
  }

  private <T extends InventoryRecord> T getInventoryRecordRevision(
      Class<T> cls, Long recordId, Long revisionId) {
    T invRec = null;
    AuditedEntity<T> entityRevision = auditManager.getObjectForRevision(cls, recordId, revisionId);
    if (entityRevision != null) {
      invRec = entityRevision.getEntity();
    }
    return invRec;
  }

  /***
   * Hibernate envers lazily loads all entity relationships regardless of the fetch type defined
   * in the entity mappings. Therefore, these FileProperty fields need to be explicitly initialised to
   * avoid lazy initialisation issues when attempting to access the file properties.
   * Subsamples in particular need to have the FileProperty fields of a connected sample initialised
   */
  private void initialiseInventoryRecordRelationships(InventoryRecord record) {
    Hibernate.initialize(record.getImageFileProperty());
    Hibernate.initialize(record.getThumbnailFileProperty());
    if (record instanceof Container) {
      Hibernate.initialize(((Container) record).getLocationsImageFileProperty());
    }
    if (record.isSubSample()) {
      Sample connectedSample = ((SubSample) record).getSample();
      if (connectedSample != null) {
        initialiseInventoryRecordRelationships(connectedSample);
      }
    }
  }
}
