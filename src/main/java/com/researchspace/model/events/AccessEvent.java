package com.researchspace.model.events;

import com.researchspace.model.User;

/**
 * A generic access event when a type T is acccessed for reading
 *
 * @param <T>
 */
public interface AccessEvent<T> {

  T getAccessedItem();

  User getAccessedBy();
}
