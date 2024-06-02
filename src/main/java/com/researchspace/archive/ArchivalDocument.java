package com.researchspace.archive;

import com.researchspace.model.record.ImportOverride;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** XML representation of a StructuredDocument */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ArchivalDocument {

  public static final String DATE_FORMAT = "yyyy-MM-dd";

  @XmlAttribute(name = "docId", required = true)
  private long docId;

  @XmlElement(name = "name", required = true)
  private String name;

  @XmlElement(name = "type", required = true)
  private String type;

  @XmlElement(name = "createdBy", required = true)
  private String createdBy;

  @XmlElement(name = "tag", required = false)
  private String documentTag;

  @XmlElement(name = "tagMetaData", required = false)
  private String tagMetaData;

  @XmlElement(name = "formType", required = false)
  private String formType;

  @XmlElement(name = "creationDate", required = true)
  private Date creationDate;

  @XmlElement(name = "lastModifiedDate", required = true)
  private Date lastModifiedDate;

  @XmlElement(name = "recordVersion", required = true)
  private Long version;

  @XmlElement(name = "parentFolder")
  private String parentFolder;

  @XmlElement(name = "folderId")
  private long folderId;

  @XmlElement(name = "folderName")
  private String folderName;

  @XmlElement(name = "folderType")
  private String folderType;

  /**
   * The version of the XML schema for this object. This is to avoid needing to change the namespace
   * when schema versions change.
   *
   * @return
   */
  @XmlElement(required = true)
  private Integer schemaVersion = 1;

  @XmlElementWrapper(name = "listFields")
  @XmlElement(name = "field", required = true)
  private List<ArchivalField> listFields = new ArrayList<ArchivalField>();

  public void addArchivalField(ArchivalField afd) {
    listFields.add(afd);
  }

  /**
   * Gets a list of all distinct ArchivalNfsFile elements in the document for all fields
   *
   * @return A possibly empty but non-null List<ArchivalNfsFile>
   */
  @XmlTransient
  public List<ArchivalNfsFile> getAllDistinctNfsElements() {
    return getListFields().stream()
        .flatMap(field -> field.getNfsElements().stream())
        .distinct()
        .collect(Collectors.toList());
  }

  public ImportOverride createImportOverride(boolean allowCreationDateAfterModificationDate) {
    Instant created = getCreationDate() != null ? getCreationDate().toInstant() : Instant.now();
    Instant modified =
        getLastModifiedDate() != null ? getLastModifiedDate().toInstant() : Instant.now();
    String originalOwner = getCreatedBy() != null ? getCreatedBy() : "n/a";
    return new ImportOverride(
        created, modified, originalOwner, allowCreationDateAfterModificationDate);
  }
}
