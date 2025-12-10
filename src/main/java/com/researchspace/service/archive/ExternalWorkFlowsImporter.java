package com.researchspace.service.archive;

import com.researchspace.archive.AllArchiveExternalWorkFlowMetaData;
import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchiveExternalWorkFlow;
import com.researchspace.archive.ArchiveExternalWorkFlowData;
import com.researchspace.archive.ArchiveExternalWorkFlowInvocation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.ExternalWorkFlowDataManager;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExternalWorkFlowsImporter {

  public void importExternalWorkFlows(
      StructuredDocument newDoc,
      ArchivalDocumentParserRef ref,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem,
      ExternalWorkFlowDataManager externalWorkFlowDataManager) {
    ArchivalDocument oldDoc = ref.getArchivalDocument();
    // Note - not all externalWorkFlowData is ever invoked and so it can be detached from any
    // external workflow
    // Conversely, an externalWorkFlow must have externalWorkFlowData and Invocations
    AllArchiveExternalWorkFlowMetaData allWorkflows = ref.getArchiveExternalWorkFlowMetaData();
    for (ArchivalField oldField : oldDoc.getListFields()) {
      Set<ArchiveExternalWorkFlowData> allExternalWorkFlowDataMeta =
          oldField.getExternalWorkFlowData();
      Set<ArchiveExternalWorkFlowInvocation> allExternalWorkFlowInvocations =
          oldField.getExternalWorkFlowInvocations();
      if (allExternalWorkFlowDataMeta != null) {
        Field newField = newDoc.getField(oldField.getFieldName());
        for (ArchiveExternalWorkFlowData externalWorkFlowMetaData : allExternalWorkFlowDataMeta) {
          Long rspaceAttachmentIdUSedForExternalWorkflow =
              findCorrespondingRSpaceDataIdForExportedExternalWorkFlowData(
                  oldField, externalWorkFlowMetaData, oldIdToNewGalleryItem);
          if (rspaceAttachmentIdUSedForExternalWorkflow != null) {
            ExternalWorkFlowData externalWorkFlowData =
                new ExternalWorkFlowData(
                    ExternalWorkFlowData.ExternalService.valueOf(
                        externalWorkFlowMetaData.getExternalService()),
                    rspaceAttachmentIdUSedForExternalWorkflow,
                    ExternalWorkFlowData.RspaceDataType.valueOf(
                        externalWorkFlowMetaData.getRspaceDataType()),
                    newField.getId(),
                    newField.getName(),
                    ExternalWorkFlowData.RspaceContainerType.valueOf(
                        externalWorkFlowMetaData.getRspaceContainerType()),
                    externalWorkFlowMetaData.getLinkFile(),
                    externalWorkFlowMetaData.getExtId(),
                    externalWorkFlowMetaData.getExtSecondaryId(),
                    externalWorkFlowMetaData.getExtContainerId(),
                    externalWorkFlowMetaData.getExtContainerName(),
                    externalWorkFlowMetaData.getBaseUrl());
            for (ArchiveExternalWorkFlowInvocation invocationMetaData :
                allExternalWorkFlowInvocations) {
              if (invocationMetaData.getDataIds().contains(externalWorkFlowMetaData.getId())) {
                ArchiveExternalWorkFlow workFlow =
                    allWorkflows.findById(invocationMetaData.getWorkFlowId());
                ExternalWorkFlow existingWorkFlow =
                    externalWorkFlowDataManager.findWorkFlowByExtIdAndName(
                        workFlow.getExtId(), workFlow.getName());
                if (existingWorkFlow != null) {
                  for (ExternalWorkFlowInvocation existingInvocation :
                      new HashSet<>(existingWorkFlow.getExternalWorkflowInvocations())) {
                    if (existingInvocation.getExtId().equals(invocationMetaData.getExtId())) {
                      externalWorkFlowData.getExternalWorkflowInvocations().add(existingInvocation);
                      existingInvocation.getExternalWorkFlowData().add(externalWorkFlowData);
                    } else {
                      ExternalWorkFlowInvocation newInvocation =
                          new ExternalWorkFlowInvocation(
                              invocationMetaData.getExtId(),
                              Set.of(externalWorkFlowData),
                              invocationMetaData.getStatus(),
                              existingWorkFlow);
                    }
                  }
                } else {
                  ExternalWorkFlow newExternalWorkFlow =
                      new ExternalWorkFlow(
                          workFlow.getExtId(), workFlow.getName(), workFlow.getDescription());
                  ExternalWorkFlowInvocation newInvocation =
                      new ExternalWorkFlowInvocation(
                          invocationMetaData.getExtId(),
                          Set.of(externalWorkFlowData),
                          invocationMetaData.getStatus(),
                          newExternalWorkFlow);
                }
              }
            }
            externalWorkFlowDataManager.save(externalWorkFlowData);
          }
        }
      }
    }
  }

  public Long findCorrespondingRSpaceDataIdForExportedExternalWorkFlowData(
      ArchivalField oldField,
      ArchiveExternalWorkFlowData externalWorkFlowMetaDataUsedByOldField,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem) {
    long originalRspaceDataId = externalWorkFlowMetaDataUsedByOldField.getRspaceDataId();
    for (ArchivalGalleryMetadata attached : oldField.getAllGalleryMetaData()) {
      if (attached.getId() == originalRspaceDataId) {
        String key =
            originalRspaceDataId
                + "-"
                + (attached.getModificationDate() != null
                    ? "" + attached.getModificationDate().getTime()
                    : "null");
        return oldIdToNewGalleryItem.get(key).getId();
      }
    }
    return originalRspaceDataId; // This will happen when the attached data in the original field
    // was used for an external workflow
    // and was subsequently removed from that field (unattached). The data has been unattached from
    // the field and so it has not been exported.
    // However, it still exists in the externalworkflows for that field which we *are* exporting
  }
}
