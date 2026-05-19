package com.researchspace.export.pdf;

import com.researchspace.model.preference.ExportPageSize;

/** Configures what additional content is to be extracted from a document to its HTML view */
public interface StructuredDocumentHTMLViewConfig {

  /**
   * Whether comments should be included - default is <code>false</code>
   *
   * @return
   */
  default boolean isComments() {
    return false;
  }

  /**
   * Gets the required page size - default A4
   *
   * @return
   */
  default ExportPageSize getPageSizeEnum() {
    return ExportPageSize.A4;
  }

  /**
   * Whether a revision history summary is required - default <code>false</code>
   *
   * @return
   */
  default boolean isProvenance() {
    return false;
  }

  /**
   * Whether last modified date should be appended to each field in the PDF export. Default is
   * <code>false</code>
   *
   * @return <code>true</code> if modification date of the field should be included, <code>false
   *     </code> otherwise.
   */
  default boolean isIncludeFieldLastModifiedDate() {
    return false;
  }

  /**
   * Whether external workflow data summaries should be appended to each field. Default is <code>
   * false</code> because generic HTML previews should not include export-only summaries.
   *
   * @return <code>true</code> if external workflow data should be included, <code>false</code>
   *     otherwise.
   */
  default boolean isIncludeExternalWorkflowData() {
    return false;
  }
}
