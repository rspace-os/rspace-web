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
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One Filestore link found in the content of a text field: a row of the Filestore link index. */
@Entity
@Getter
@EqualsAndHashCode(of = {"field", "fileStore", "relPath"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NfsFieldLink implements Serializable {

  private static final long serialVersionUID = 1659L;

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "nfs_field_link_gen")
  @TableGenerator(
      name = "nfs_field_link_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  @Setter(AccessLevel.PACKAGE)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  private Field field;

  @ManyToOne(fetch = FetchType.LAZY)
  private NfsFileStore fileStore;

  @ManyToOne(fetch = FetchType.LAZY)
  private NfsFileSystem fileSystem;

  @Column(nullable = false, length = 1024)
  private String relPath;

  @Column(nullable = false, length = 1024)
  private String absolutePath;

  public NfsFieldLink(Field field, NfsFileStore fileStore, String relPath) {
    this.field = field;
    this.fileStore = fileStore;
    this.relPath = relPath;
    this.fileSystem = fileStore.getFileSystem();
    this.absolutePath = normalisePath(fileStore.getAbsolutePath(relPath));
  }

  /**
   * The one normalisation rule for paths on a filesystem: no leading, trailing or doubled slashes,
   * so the root of the filesystem is the empty string.
   */
  public static String normalisePath(String path) {
    if (path == null) {
      return "";
    }
    return Arrays.stream(path.split("/"))
        .filter(segment -> !segment.isEmpty())
        .collect(Collectors.joining("/"));
  }
}
