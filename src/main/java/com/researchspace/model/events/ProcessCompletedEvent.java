package com.researchspace.model.events;

/**
 * Event type for completion of long-running or background process.
 *
 * @param <T>
 */
public interface ProcessCompletedEvent<T> {

  /**
   * Get a report or result from the completed process
   *
   * @return
   */
  T getReport();
}
