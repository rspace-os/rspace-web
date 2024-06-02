package com.researchspace.service;

import com.researchspace.model.Group;
import com.researchspace.model.User;

/** Handles behavior for dealing with consequences of changing the PI of a group. */
public interface PiChangeHandler {

  /**
   * Handles pi changed event once roles have been swapped.
   *
   * @param oldPI
   * @param labGroup
   * @param newPi
   * @param cntext
   */
  void afterPiChanged(User oldPI, Group labGroup, User newPi, PiChangeContext cntext);

  /**
   * performs actions before old PI is swapped for new PI
   *
   * @param currPI
   * @param labGroup
   * @param newPi
   * @param cntext
   */
  void beforePiChanged(User currPI, Group labGroup, User newPi, PiChangeContext cntext);
}
