package com.researchspace.archive;

import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import com.researchspace.service.archive.export.externalWorkFlow.LinkableExternalWorkFlowData;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = {"extId", "rspaceDataId"})
public class ArchiveExternalWorkFlowDataMetaData extends ArchivalGalleryMetadata {
  private @XmlElement long rspaceDataId;
  private @XmlElement String externalService;
  private @XmlElement String extName;
  private @XmlElement String extId;
  private @XmlElement String extSecondaryId;
  private @XmlElement String extContainerId;
  private @XmlElement String extContainerName;
  private @XmlElement String baseUrl;

  public ArchiveExternalWorkFlowDataMetaData() {}

  public ArchiveExternalWorkFlowDataMetaData(
      LinkableExternalWorkFlowData item, String archiveLink, ArchivalField archiveField) {
    ExternalWorkFlowData data = item.getExternalWorkflowData();
    this.setId(item.getId());
    this.setParentId(archiveField.getFieldId());
    this.setFileName(archiveLink);
    this.setLinkFile(archiveLink);
    this.setRspaceDataId(data.getRspacedataid());
    this.setExternalService(data.getExternalService().name());
    this.setExtName(data.getExtName());
    this.setExtId(data.getExtId());
    this.setExtSecondaryId(data.getExtSecondaryId());
    this.setExtContainerId(data.getExtContainerID());
    this.setExtContainerName(data.getExtContainerName());
    this.setBaseUrl(data.getBaseUrl());
    for (ExternalWorkFlowInvocation invocation :
        item.getExternalWorkflowData().getExternalWorkflowInvocations()) {
      boolean existingInvocation = false;
      for (ArchiveExternalWorkFlowInvocationMetaData existingInvocationAgms :
          archiveField.getExternalWorkFlowInvocations()) {
        if (existingInvocationAgms.getId() == invocation.getId()) {
          existingInvocationAgms.addDataId(item.getId());
          existingInvocation = true;
          break;
        }
      }
      if (!existingInvocation) {
        ArchiveExternalWorkFlowInvocationMetaData invocationAgm =
            new ArchiveExternalWorkFlowInvocationMetaData(
                invocation, item.getExternalWorkflowData().getExternalService().name());
        invocationAgm.addDataId(item.getId());gi
        archiveField.getExternalWorkFlowInvocations().add(invocationAgm);
      }
    }
  }
}
