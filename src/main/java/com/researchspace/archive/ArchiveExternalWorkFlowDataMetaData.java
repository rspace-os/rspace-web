package com.researchspace.archive;

import java.util.HashSet;
import java.util.Set;
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
@EqualsAndHashCode(of = { "extId","rspaceDataId" })
public class ArchiveExternalWorkFlowDataMetaData extends ArchivalGalleryMetadata {
  private @XmlElement long rspaceDataId;
  private @XmlElement String externalService;
  private @XmlElement String extName;
  private @XmlElement String extId;
  private @XmlElement String extSecondaryId;
  private @XmlElement String extContainerId;
  private @XmlElement String extContainerName;
  private @XmlElement String baseUrl;
}
