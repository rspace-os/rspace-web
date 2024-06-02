package com.researchspace.model.events;

import com.researchspace.model.User;

/**
 * An item is restored from deleted state
 *
 * @param <T>
 */
public interface RestoreEvent<T> {

  T getRestored();

  /**
   * The user performing the restore action
   *
   * @return
   */
  User getSubject();
}
