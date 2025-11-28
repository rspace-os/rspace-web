package com.researchspace.archive;

import java.util.HashSet;
import java.util.Set;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "ExternalWorkFlows")
@Getter
@Setter
public class AllArchiveExternalWorkFlowMetaData extends ArchivalGalleryMetadata {
  @XmlElement(name = "exported-externalWorkFlow")
  private Set<ArchiveExternalWorkFlowMetaData> workFlows = new HashSet<>();

  public ArchiveExternalWorkFlowMetaData findById(long id) {
    return workFlows.stream().filter(wf -> wf.getId() == id).findFirst().orElse(null);
  }
}
