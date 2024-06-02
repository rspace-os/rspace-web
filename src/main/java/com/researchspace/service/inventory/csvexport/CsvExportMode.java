package com.researchspace.service.inventory.csvexport;

/** Export mode decides about columns included in export. */
public enum CsvExportMode {

  /**
   * COMPACT export mode includes:
   *
   * <ul>
   *   <li>all basic fields: global id, name, tags, description
   * </ul>
   */
  COMPACT,
  /**
   * FULL export mode includes:
   *
   * <ul>
   *   <li>all fields exported in COMPACT mode
   *   <li>sample template fields
   *   <li>extra fields
   * </ul>
   */
  FULL
}
