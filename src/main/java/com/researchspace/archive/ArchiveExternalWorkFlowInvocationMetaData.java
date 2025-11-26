package com.researchspace.archive;

import java.util.HashSet;
import java.util.Set;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
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
@EqualsAndHashCode(of = { "extId" })
public class ArchiveExternalWorkFlowInvocationMetaData extends ArchivalGalleryMetadata{
  private @XmlElement String extId;
  private @XmlElement String status;
  private @XmlElement String id;
  private Set<ArchiveExternalWorkFlowDataMetaData> data = new HashSet<>();
  private @XmlTransient
  ArchiveExternalWorkFlowMetaData workFlowMetaData;
}

