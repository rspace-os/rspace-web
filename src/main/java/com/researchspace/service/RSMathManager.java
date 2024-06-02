package com.researchspace.service;

import com.researchspace.model.RSMath;
import com.researchspace.model.User;

public interface RSMathManager extends GenericManager<RSMath, Long> {

  /**
   * Gets {@link RSMath} element of given ID, asserting user has permission to view.
   *
   * @param id
   * @param revision may be null
   * @param user
   * @param getBytes boolean choice to retrieve SVG bytes
   * @return
   */
  RSMath get(long id, Integer revision, User user, boolean getBytes);
}
