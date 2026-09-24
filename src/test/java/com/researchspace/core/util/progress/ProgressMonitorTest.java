package com.researchspace.core.util.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ProgressMonitorTest {
  ProgressMonitor progress;

  @Test
  public void ProgressMonitorImplRejects0OrLess() {
    assertThrows(IllegalArgumentException.class, () -> new ProgressMonitorImpl(0, "any"));
  }

  @Test
  public void ProgressMonitorImplRejectsNegativeIncrement() {
    progress = new ProgressMonitorImpl(10, "any");
    assertThrows(IllegalArgumentException.class, () -> progress.worked(0));
  }

  @Test
  public void testIncrementProgress() {
    progress = createProgressMonitor();
    assertThat(progress.getPercentComplete()).isCloseTo(0, within(0.001));
    assertNotNull(progress.worked(10)); // 5%
    assertThat(progress.getPercentComplete()).isCloseTo(5, within(0.001));
    progress.worked(190); // complete
    assertThat(progress.getPercentComplete()).isCloseTo(100, within(0.001));
    assertTrue(progress.isDone());
    assertEquals("any", progress.getDescription());

    // incrmenting more has no effect
    progress.worked(200);
    assertThat(progress.getPercentComplete()).isCloseTo(100, within(0.001));
  }

  private ProgressMonitorImpl createProgressMonitor() {
    return new ProgressMonitorImpl(200, "any");
  }

  @Test
  public void testIsCancelSupported() {
    progress = createProgressMonitor();
    progress.requestCancel();
    assertTrue(progress.isCancelRequested());
  }

  @Test
  public void testsetDone() {
    progress = createProgressMonitor();
    assertNotNull(progress.done());
    assertTrue(progress.isDone());
  }

  @Test
  public void testDefaultWorkUnits() {
    progress = new ProgressMonitorImpl();
    assertEquals(ProgressMonitorImpl.DEFAULT_WORK_UNIT_COUNT, progress.getTotalWorkUnits());
  }
}
