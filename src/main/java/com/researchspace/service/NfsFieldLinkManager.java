package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.RecordInformation;
import java.util.Collection;
import java.util.List;

/** Keeps the Filestore link index in step with field content and answers lookups on it. */
public interface NfsFieldLinkManager {

  /**
   * Makes the index rows of {@code field} match the Filestore links in {@code content}: missing
   * rows are added, stale ones deleted. Links whose filestore does not exist in this instance are
   * skipped. Null or blank content clears the field's rows.
   */
  void reindex(Field field, String content);

  /**
   * Linked documents of the item at {@code remotePath} of the filestore, oldest first. Asserts read
   * access to the filestore's filesystem. Documents the user cannot read are owner-only
   * placeholders (null id, oid and name).
   *
   * @throws jakarta.ws.rs.NotFoundException for an unknown filestore
   */
  List<RecordInformation> getLinkedDocuments(Long filestoreId, String remotePath, User user);

  /** Linked documents of any of the absolute paths on the filesystem, as the overload above. */
  List<RecordInformation> getLinkedDocuments(
      NfsFileSystem fileSystem, Collection<String> absolutePaths, User user);
}
