package com.researchspace.model.events;

import com.researchspace.model.User;

/**
 * A generic creation event of type T
 *
 * @param <T>
 */
public interface CreationEvent<T> {

  T getCreatedItem();

  User getCreatedBy();
}
