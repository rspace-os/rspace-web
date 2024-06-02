package com.researchspace.service;

import com.researchspace.model.comms.Communication;

/** Basic interface to distribute information or messages inside/outside of RS */
public interface Broadcaster {

  /**
   * Implementing classes should handle their own checked exceptions, since possibly many
   * broadcasters will be called, and the failure of one should not prejuduce the failure of others.
   *
   * @param comm the {@link Communication} to broadcast.
   */
  void broadcast(Communication comm);
}
