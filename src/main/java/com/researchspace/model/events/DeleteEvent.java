package com.researchspace.model.events;

import com.researchspace.model.User;

/**
 * Generic delete event
 *
 * @param <T>
 */
public interface DeleteEvent<T> {

  T getDeletedItem();

  User getDeletedBy();
}
