package com.researchspace.archive;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "ExternalWorkFlows")
@Getter
@Setter
public class AllArchiveExternalWorkFlowMetaData extends ArchivalGalleryMetadata {
  @XmlElement(name = "exported-externalWorkFlow")
  private Set<ArchiveExternalWorkFlow> workFlows = new HashSet<>();

  public ArchiveExternalWorkFlow findById(long id) {
    return workFlows.stream().filter(wf -> wf.getId() == id).findFirst().orElse(null);
  }
}
