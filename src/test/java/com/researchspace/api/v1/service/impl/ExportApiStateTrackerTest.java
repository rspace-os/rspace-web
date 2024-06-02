package com.researchspace.api.v1.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.researchspace.archive.ExportRecordList;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.core.util.progress.ProgressMonitorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportApiStateTrackerTest {
  ExportApiStateTracker tracker;
  ProgressMonitor monitor;

  @BeforeEach
  void before() {
    tracker = new ExportApiStateTracker();
    monitor = new ProgressMonitorImpl(10, "a test monitor");
  }

  @Test
  void addGetRemove() {
    tracker.addExportRecordList("a", new ExportRecordList());
    tracker.addProgressMonitor("a", monitor);
    // can't overwrite with same key
    assertThrows(
        IllegalArgumentException.class,
        () -> tracker.addExportRecordList("a", new ExportRecordList()));
    assertThrows(
        IllegalArgumentException.class,
        () -> tracker.addProgressMonitor("a", new ProgressMonitorImpl(10, "a test monitor")));

    // get(id) works as expected
    assertFalse(tracker.getById("b").isPresent());
    assertFalse(tracker.getProgressMonitorById("b").isPresent());
    assertTrue(tracker.getById("a").isPresent());
    assertTrue(tracker.getProgressMonitorById("a").isPresent());

    // clear should remove all state for this id
    tracker.clear("a");
    assertFalse(tracker.getById("a").isPresent());
    assertFalse(tracker.getProgressMonitorById("a").isPresent());
  }
}
