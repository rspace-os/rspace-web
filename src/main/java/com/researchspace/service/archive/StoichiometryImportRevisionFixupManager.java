package com.researchspace.service.archive;

import com.researchspace.model.User;

/**
 * Fixes up stoichiometry revision numbers in field HTML after archive import.
 *
 * <p>During import, stoichiometry entities are created inside a transaction where Envers audit
 * records are not yet available. This leaves {@code revision: null} in the field's {@code
 * data-stoichiometry-table} attribute. This manager runs in a separate transaction after the import
 * commits, queries Envers for the actual revision, and rewrites the field HTML.
 */
public interface StoichiometryImportRevisionFixupManager {

  /**
   * Scans imported records for stoichiometry references with null revisions, looks up the actual
   * Envers revision for each, and rewrites the field HTML.
   *
   * @param report the completed import report containing imported record references
   * @param user the user who performed the import
   */
  void fixupStoichiometryRevisions(ImportArchiveReport report, User user);
}
