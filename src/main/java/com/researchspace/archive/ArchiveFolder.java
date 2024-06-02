package com.researchspace.archive;

import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.ImportOverride;
import java.time.Instant;
import java.util.Date;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Holds information about the RSpace folder tree of exported records.
 *
 * <p>Equality is based on the value of the id attribute.
 */
@XmlType()
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode(of = {"id"})
public class ArchiveFolder implements IRSpaceDoc {

  @XmlAttribute(required = true)
  private Long id;

  @XmlAttribute private Long parentId;

  @XmlAttribute private String name;

  @XmlElement private String tag;

  @XmlElement private String tagMetaData;

  @XmlAttribute private boolean media;

  // optional for medial folder exports
  @XmlAttribute private String mediaType;

  @XmlAttribute private String type;

  @XmlTransient private String globalIdentifier;

  private ArchiveUser owner;
  private Date modificationDate;
  private Date creationDate;

  private boolean is(RecordType type) {
    return getType() != null && getType().contains(type.name());
  }

  @XmlTransient
  public boolean isSystemFolder() {
    return is(RecordType.SYSTEM);
  }

  @Override
  @XmlTransient
  public boolean isMediaRecord() {
    return is(RecordType.MEDIA_FILE);
  }

  @XmlTransient
  public boolean isRootMediaFolder() {
    return is(RecordType.ROOT_MEDIA);
  }

  @XmlTransient
  public boolean isApiFolder() {
    return is(RecordType.API_INBOX);
  }

  @XmlTransient
  public boolean isRootFolder() {
    return is(RecordType.ROOT);
  }

  @Override
  @XmlTransient
  public boolean isStructuredDocument() {
    return is(RecordType.NORMAL);
  }

  @Override
  public Date getModificationDateAsDate() {
    return modificationDate == null ? null : new Date(modificationDate.getTime());
  }

  // handles null values in exports from very old archives
  public ImportOverride createImportOverride(boolean allowCreationDateAfterModificationDate) {
    Instant created = getCreationDate() != null ? getCreationDate().toInstant() : Instant.now();
    Instant modified =
        getModificationDate() != null ? getModificationDate().toInstant() : Instant.now();
    String originalOwner = getOwner() != null ? getOwner().getUniqueName() : "n/a";
    return new ImportOverride(
        created, modified, originalOwner, allowCreationDateAfterModificationDate);
  }
}
