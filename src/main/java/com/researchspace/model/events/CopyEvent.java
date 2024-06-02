package com.researchspace.model.events;

/**
 * A generic copy event of type T
 *
 * @param <T>
 */
public interface CopyEvent<T> {

  T getCopiedItem();
}
