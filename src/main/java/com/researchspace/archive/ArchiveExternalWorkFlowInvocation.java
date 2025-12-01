package com.researchspace.archive;

import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import java.util.HashSet;
import java.util.Set;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = {"extId"})
public class ArchiveExternalWorkFlowInvocation extends ArchivalGalleryMetadata {
  private @XmlElement String extId;
  private @XmlElement String status;
  private @XmlElement String externalService;
  private @XmlElement long workFlowId;

  @XmlElementWrapper(name = "allExternalWorkFlowDataIds")
  private @XmlElement Set<Long> dataIds = new HashSet<>();

  private @XmlTransient ArchiveExternalWorkFlow workFlowMetaData;

  public ArchiveExternalWorkFlowInvocation() {}

  public ArchiveExternalWorkFlowInvocation(
      ExternalWorkFlowInvocation invocation, String externalService) {
    this.setId(invocation.getId());
    this.setExternalService(externalService);
    this.setStatus(invocation.getStatus());
    this.setExtId(invocation.getExtId());
    ExternalWorkFlow exWF = invocation.getExternalWorkFlow();
    workFlowId = exWF.getId();
    ArchiveExternalWorkFlow exWFAgm = new ArchiveExternalWorkFlow(exWF);
    this.workFlowMetaData = exWFAgm;
  }

  public void addDataId(Long dataID) {
    dataIds.add(dataID);
  }
}
