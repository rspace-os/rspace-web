package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList.ApiInventoryRecordRevision;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplateInfo;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.service.AuditManager;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("inventoryAuditApiManager")
public class InventoryAuditApiManagerImpl implements InventoryAuditApiManager {

  protected @Autowired AuditManager auditManager;

  @Override
  public ApiInventoryRecordRevisionList getInventoryRecordRevisions(InventoryRecord currentInvRec) {

    Class<?> cls = currentInvRec.getClass();
    List<?> entityRevisions = auditManager.getRevisionsForEntity(cls, currentInvRec.getId());

    ApiInventoryRecordRevisionList apiRevisions = new ApiInventoryRecordRevisionList();
    for (int i = 0; i < entityRevisions.size(); i++) {
      AuditedEntity<?> auditedEntity = (AuditedEntity<?>) entityRevisions.get(i);
      InventoryRecord invRec = (InventoryRecord) auditedEntity.getEntity();
      long revisionId = auditedEntity.getRevision().longValue();
      ApiInventoryRecordInfo apiInvRec = ApiInventoryRecordInfo.fromInventoryRecord(invRec);
      apiInvRec.setRevisionId(revisionId);
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
      result = (ApiSubSample) ApiInventoryRecordInfo.fromInventoryRecordToFullApiRecord(subSample);
      ;
      result.setRevisionId(revisionId);
    }
    return result;
  }

  @Override
  public ApiSampleTemplate getApiTemplateVersion(Sample currTemplate, Long version) {
    if (currTemplate.getVersion().equals(version)) {
      return new ApiSampleTemplate(currTemplate);
    }

    List<ApiInventoryRecordRevision> templateRevisions =
        getInventoryRecordRevisions(currTemplate).getRevisions();
    List<ApiInventoryRecordRevision> versionRevisions =
        templateRevisions.stream()
            .filter(
                recRev -> ((ApiSampleTemplateInfo) recRev.getRecord()).getVersion().equals(version))
            .collect(Collectors.toList());
    if (versionRevisions.isEmpty()) {
      return null;
    }

    Long lastRevisionForVersion = versionRevisions.get(versionRevisions.size() - 1).getRevisionId();
    ApiSampleTemplate result =
        (ApiSampleTemplate) getApiSampleRevision(currTemplate.getId(), lastRevisionForVersion);
    result.setHistoricalVersion(true);
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
}
