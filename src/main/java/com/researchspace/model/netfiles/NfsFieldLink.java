package com.researchspace.model.netfiles;

import com.researchspace.model.field.Field;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/** One Filestore link found in the content of a text field: a row of the Filestore link index. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NfsFieldLink implements Serializable {

  private static final long serialVersionUID = 1659L;

  /** Column length of both paths; longer links are not indexed. */
  public static final int MAX_PATH_LENGTH = 1024;

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "nfs_field_link_gen")
  @TableGenerator(
      name = "nfs_field_link_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  private Field field;

  @ManyToOne(fetch = FetchType.LAZY)
  private NfsFileStore fileStore;

  @ManyToOne(fetch = FetchType.LAZY)
  private NfsFileSystem fileSystem;

  @Column(nullable = false, length = MAX_PATH_LENGTH)
  private String relPath;

  @Column(nullable = false, length = MAX_PATH_LENGTH)
  private String absolutePath;

  public NfsFieldLink(Field field, NfsFileStore fileStore, String relPath) {
    this.field = field;
    this.fileStore = fileStore;
    this.relPath = relPath;
    this.fileSystem = fileStore.getFileSystem();
    this.absolutePath = normalisePath(fileStore.getAbsolutePath(relPath));
  }

  /**
   * The one normalisation rule for paths on a filesystem: no leading or trailing slashes, so the
   * root of the filesystem is the empty string. Inner slashes are kept, as S3 keys are flat.
   */
  public static String normalisePath(String path) {
    return StringUtils.strip(path, "/");
  }
}
