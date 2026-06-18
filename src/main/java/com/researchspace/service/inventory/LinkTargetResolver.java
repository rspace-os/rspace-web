package com.researchspace.service.inventory;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;

/**
 * Resolves an Inventory Link target GlobalID (inventory item or ELN item) to decide whether it
 * points at a record that actually exists and that the supplied user is permitted to read. Used by
 * link validation to reject links to non-existent or unreadable targets, regardless of whether the
 * target lives in the Inventory or in the ELN. Not transactional itself: implementations touch
 * DAOs, so they must be invoked within the transaction of a calling {@code *Manager} service.
 */
public interface LinkTargetResolver {

  /**
   * @param target the parsed link target GlobalID (any version suffix is ignored: the base record's
   *     existence and readability is what matters)
   * @param user the acting user
   * @return true if the target resolves to a real record the user can READ, false if it does not
   *     exist, is not readable, or has an unsupported prefix
   */
  boolean targetExistsAndIsReadable(GlobalIdentifier target, User user);
}
