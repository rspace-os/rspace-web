package com.researchspace.archive.model;

import com.researchspace.archive.ExportScope;

/** Super-interface for shared configuration for all sorts of export */
public interface IExportConfig {

  /**
   * Gets the scope of the archive; this controls what data is sent to the archive.
   *
   * @return
   */
  ExportScope getExportScope();

  /**
   * Convenience method for whether this configuration is selection-based export (i.e., is {@value
   * ExportScope#SELECTION})
   *
   * @return
   */
  boolean isSelectionScope();

  /**
   * Convenience method for whether this configuration is user-based export (i.e., is {@value
   * ExportScope#USER})
   *
   * @return
   */
  boolean isUserScope();

  /**
   * Convenience method for whether this configuration is group-based export (i.e., is {@value
   * ExportScope#GROUP})
   *
   * @return
   */
  boolean isGroupScope();
}
