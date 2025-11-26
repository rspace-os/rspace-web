package com.researchspace.archive;

import java.util.HashSet;
import java.util.Set;
import javax.xml.bind.annotation.XmlElement;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(of = { "extId" })
public class ArchiveExternalWorkFlowMetaData extends ArchivalGalleryMetadata{
  private @XmlElement String extId;
  private @XmlElement String name;
  private @XmlElement String description;
  private @XmlElement String id;
  private Set<ArchiveExternalWorkFlowInvocationMetaData> invocations = new HashSet<>();
}
