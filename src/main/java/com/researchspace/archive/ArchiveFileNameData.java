package com.researchspace.archive;

import static com.researchspace.archive.ArchiveUtils.filterArchiveNameString;
import static org.apache.commons.lang.StringUtils.abbreviate;

import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.core.RecordType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;

/**
 * Encapsulates the information maintained in a folder/ export file or folder name. <br>
 * These are value objects whose equality is based on ID and Revision number.
 *
 * <p>Does not hold information about file suffix, as this is dependent on the type of export being
 * generated (XML, HTML etc).
 */
@EqualsAndHashCode(of = {"id", "revision"})
@Getter
public class ArchiveFileNameData {

  public static final String DOC_PREFIX = "doc_";

  public static final String MEDIA_PREFIX = "media_";

  private final Long id;

  private Integer revision = null;
  private final String name;
  private final RecordType type;

  public static final int MAX_FNAME_LENGTH = 50;

  /**
   * Takes an {@link IRSpaceDoc} and optional revision to populate internal fields
   *
   * @param record
   * @param revision
   */
  public ArchiveFileNameData(IRSpaceDoc record, Number revision) {
    if (revision != null) {
      this.revision = revision.intValue();
    }
    this.id = record.getId();
    this.name =
        StringUtils.strip(
            abbreviate(filterArchiveNameString(record.getName()), MAX_FNAME_LENGTH), ".");
    if (record.isMediaRecord()) {
      this.type = RecordType.MEDIA_FILE;
    } else if (record.isStructuredDocument()) {
      this.type = RecordType.NORMAL;
    } else {
      this.type = RecordType.FOLDER;
    }
  }

  /**
   * Converts this object to a filename representation
   *
   * @return
   */
  public String toFileName() {
    String fileNamePrefix;
    if (RecordType.FOLDER.equals(type)) {
      fileNamePrefix = ""; // no prefix for folder/notebook
    } else if (RecordType.NORMAL.equals(type)) {
      fileNamePrefix = DOC_PREFIX;
    } else {
      fileNamePrefix = MEDIA_PREFIX;
    }
    String fileName = fileNamePrefix + name + "-" + id;
    fileName = addRevisionIfNotNull(fileName);
    return fileName;
  }

  @Override
  public String toString() {
    return "ArchiveFileNameData [id="
        + id
        + ", revision="
        + revision
        + ", name="
        + name
        + ", type="
        + type
        + "]";
  }

  private String addRevisionIfNotNull(String replaced) {
    if (revision != null) {
      replaced = replaced + "-rev" + revision;
    }
    return replaced;
  }
}
