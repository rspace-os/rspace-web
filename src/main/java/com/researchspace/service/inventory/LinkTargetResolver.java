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

  /**
   * Like {@link #targetExistsAndIsReadable}, but additionally false when the target record is
   * soft-deleted. Registration flows use this: a deleted target is still readable by its owner, but
   * a permanent registry entry must not name a dead record.
   *
   * @param target the parsed link target GlobalID (any version suffix is ignored)
   * @param user the user whose READ permission decides, typically the owning record's owner rather
   *     than the acting user, so the outcome cannot vary with who triggers the flow
   * @return true if the target resolves to a live (non-deleted) record the user can READ
   */
  boolean targetIsLiveAndReadable(GlobalIdentifier target, User user);

  /**
   * Whether the target can be shown, positively, not to exist on this server: the record kind is
   * one we can probe without a permission check, and no row is there. False both for a target that
   * does exist and for one whose kind we cannot probe, so a caller relaxing its rules for missing
   * targets never relaxes them for a target that is merely unreadable.
   *
   * <p>Only Inventory prefixes are probed. Deciding the same for an ELN record would need a
   * permission-free load of it, which is the disclosure ADR-0002 exists to prevent.
   *
   * @param target the parsed link target GlobalID (any version suffix is ignored)
   * @return true only when the target's kind is probeable and no such record exists
   */
  boolean targetIsKnownMissing(GlobalIdentifier target);
}
