package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.RecordInformation;
import java.util.List;

/** Keeps the Filestore link index in step with field content and answers lookups on it. */
public interface NfsFieldLinkManager {

  /**
   * Makes the index rows of {@code field} match the Filestore links in {@code content}: missing
   * rows are added, stale ones deleted. Links whose filestore does not exist in this instance, or
   * whose path is longer than {@link
   * com.researchspace.model.netfiles.NfsFieldLink#MAX_PATH_LENGTH}, are skipped. Null or blank
   * content clears the field's rows.
   */
  void reindex(Field field, String content);

  /**
   * Linked documents of the item at {@code remotePath} of the filestore, oldest first. Asserts read
   * access to the filestore's filesystem, which on filesystems with per-user logins says nothing
   * about the item: there the caller must first check that the user's own login reaches it.
   * Documents the user cannot read are owner-only placeholders (null id, oid and name).
   *
   * @throws jakarta.ws.rs.NotFoundException for an unknown filestore
   */
  List<RecordInformation> getLinkedDocuments(Long filestoreId, String remotePath, User user);
}
