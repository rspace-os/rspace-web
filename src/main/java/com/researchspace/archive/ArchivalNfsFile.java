package com.researchspace.archive;

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
@EqualsAndHashCode(of = {"fileSystemId", "fileStoreId", "relativePath"})
public class ArchivalNfsFile {

  @XmlElement(required = true)
  private Integer schemaVersion = 2;

  private @XmlElement Long fileSystemId;
  private @XmlElement String fileSystemName;
  private @XmlElement String fileSystemUrl;
  private @XmlElement Long fileStoreId;
  private @XmlElement String fileStorePath;
  private @XmlElement String relativePath;

  // if nfs file is included in export
  private boolean addedToArchive;
  private String archivePath;
  // if for some reason not included in archive
  private String errorMsg;

  private boolean folderLink;
  private String folderExportSummaryMsg;
}
