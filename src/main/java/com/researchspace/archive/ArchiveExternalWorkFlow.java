package com.researchspace.archive;

import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(
    of = {"extId", "externalService"},
    callSuper = false)
public class ArchiveExternalWorkFlow extends ArchivalGalleryMetadata {
  private @XmlElement String extId;
  private @XmlElement String externalService;
  @XmlTransient private String baseUrl;

  public ArchiveExternalWorkFlow() {}

  public ArchiveExternalWorkFlow(ExternalWorkFlow exWF) {
    this.extId = exWF.getExtId();
    this.setId(exWF.getId());
    this.setName(exWF.getName());
    this.setDescription(exWF.getDescription());
    this.externalService =
        exWF.getExternalWorkflowInvocations()
            .iterator()
            .next()
            .getExternalWorkFlowData()
            .iterator()
            .next()
            .getExternalService()
            .name();
    this.baseUrl =
        exWF.getExternalWorkflowInvocations()
            .iterator()
            .next()
            .getExternalWorkFlowData()
            .iterator()
            .next()
            .getBaseUrl();
  }
}
