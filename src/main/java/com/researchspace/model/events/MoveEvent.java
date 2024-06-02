package com.researchspace.model.events;

import com.researchspace.model.User;

/**
 * Generic move event
 *
 * @param <T,S>
 */
public interface MoveEvent<T, S, U> {

  T getMovedItem();

  S getSourceItem();

  U getTargetItem();

  User getMovedBy();
}
