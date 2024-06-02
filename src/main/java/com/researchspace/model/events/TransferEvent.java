package com.researchspace.model.events;

import com.researchspace.model.User;

/**
 * A generic transfer event of type T
 *
 * @param <T>
 */
public interface TransferEvent<T> {

  T getTransferredItem();

  User getTransferringUser();

  User getOriginalOwner();

  User getNewOwner();
}
