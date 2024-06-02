package com.researchspace.archive.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DefaultArchiveExportReport implements ArchiveExportReport {
  private boolean archivalCompleted;
  private Long archivalDate;
  private List<String> msges = new ArrayList<String>();

  DefaultArchiveExportReport(boolean isCompleted, Date archivalDate) {
    super();
    this.archivalCompleted = isCompleted;
    this.archivalDate = archivalDate.getTime();
  }

  @Override
  public boolean isArchivalCompleted() {
    return archivalCompleted;
  }

  @Override
  public Date getArchivalDate() {
    return new Date(archivalDate);
  }

  public void addMessage(String msg) {
    if (!archivalCompleted) msges.add(msg);
  }

  public void setArchivalCompleted(boolean isCompleted) {
    this.archivalCompleted = isCompleted;
  }

  public String toString() {
    return msges + " on " + getArchivalDate();
  }
}
