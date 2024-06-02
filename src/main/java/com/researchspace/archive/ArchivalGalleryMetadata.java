package com.researchspace.archive;

import com.researchspace.model.record.ImportOverride;
import java.time.Instant;
import java.util.Date;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
@Getter
@Setter
@ToString
public class ArchivalGalleryMetadata {
  @XmlAttribute(name = "id", required = true)
  private long id;

  @XmlElement(required = true)
  private Integer schemaVersion = 1;

  private @XmlElement long parentId;

  /**
   * For use by derived elements e.g. annotations to refer to original image ID This cannot be a
   * required element as older pre 1.45 archives will not have this
   */
  private @XmlElement Long originalId;

  private @XmlElement String fileName;

  private @XmlElement String name;

  private @XmlElement String linkFile;
  private @XmlElement String contentType;
  private @XmlElement String extension;
  private @XmlElement String annotation;
  private @XmlElement Date creationDate;
  private @XmlElement Date modificationDate;

  private @XmlElement String createdBy;
  private @XmlElement String description;
  private @XmlAttribute Long parentGalleryFolderId;

  private @XmlElement String chemElementsFormat;

  private @XmlElement Long version;

  private @XmlElement Long originalVersion;

  private String linkToOriginalFile;

  public ArchivalGalleryMetadata() {
    this.contentType = "application/octet-stream";
  }

  public ImportOverride createImportOverride(boolean allowCreationDateAfterModificationDate) {
    Instant created = getCreationDate() != null ? getCreationDate().toInstant() : Instant.now();
    Instant modified =
        getModificationDate() != null ? getModificationDate().toInstant() : Instant.now();
    String originalOwner = getCreatedBy() != null ? getCreatedBy() : "n/a";
    return new ImportOverride(
        created, modified, originalOwner, allowCreationDateAfterModificationDate);
  }
}
