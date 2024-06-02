package com.researchspace.model.events;

/**
 * Generic rename event
 *
 * @param <T>
 */
public interface RenameEvent<T> {

  T getRenamedItem();
}
