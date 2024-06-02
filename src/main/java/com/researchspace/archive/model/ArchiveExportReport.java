package com.researchspace.archive.model;

import java.util.Date;

/** A summary of the archival process for possible display to UI/ admins */
public interface ArchiveExportReport {

  boolean isArchivalCompleted();

  void setArchivalCompleted(boolean isCompleted);

  Date getArchivalDate();

  /**
   * Inactive once isArchivalCompleted()== true
   *
   * @param msg
   */
  public void addMessage(String msg);
}
