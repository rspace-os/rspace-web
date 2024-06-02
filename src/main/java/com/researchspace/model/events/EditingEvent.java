package com.researchspace.model.events;

import com.researchspace.model.User;

/**
 * Generic editing event
 *
 * @param <T>
 */
public interface EditingEvent<T> {

  T getEditedItem();

  User getEditedBy();
}
