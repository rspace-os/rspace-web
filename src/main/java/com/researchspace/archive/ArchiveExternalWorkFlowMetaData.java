package com.researchspace.archive;

import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import javax.xml.bind.annotation.XmlElement;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(of = {"extId", "name", "externalService"})
public class ArchiveExternalWorkFlowMetaData extends ArchivalGalleryMetadata {
  private @XmlElement String extId;
  private @XmlElement String name;
  private @XmlElement String description;
  private @XmlElement String externalService;

  public ArchiveExternalWorkFlowMetaData() {}

  public ArchiveExternalWorkFlowMetaData(ExternalWorkFlow exWF) {
    this.extId = exWF.getExtId();
    this.setId(exWF.getId());
    this.name = exWF.getName();
    this.description = exWF.getDescription();
    this.externalService =
        exWF.getExternalWorkflowInvocations()
            .iterator()
            .next()
            .getExternalWorkFlowData()
            .iterator()
            .next()
            .getExternalService()
            .name();
  }
}
