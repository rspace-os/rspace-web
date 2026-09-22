package com.researchspace.service.inventory;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.Optional;

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
   * soft-deleted, and type-exact: a record whose own GlobalID prefix differs from the requested one
   * does not count, even when it shares the numeric id. Registration flows use this: a deleted
   * target is still readable by its owner, but a permanent registry entry must not name a dead
   * record. The link target summary deliberately does NOT use this: a trashed Inventory item keeps
   * a working viewer, so the summary reports it through {@link #readableInventoryTarget} as
   * readable and deleted rather than hiding it.
   *
   * @param target the parsed link target GlobalID (any version suffix is ignored)
   * @param user the user whose READ permission decides, typically the owning record's owner rather
   *     than the acting user, so the outcome cannot vary with who triggers the flow
   * @return true if the target resolves to a live (non-deleted) record of exactly the requested
   *     kind that the user can READ
   */
  boolean targetIsLiveAndReadable(GlobalIdentifier target, User user);

  /**
   * The Inventory record a link target names, when the acting user may READ it, whether or not it
   * is soft-deleted. Callers that need the target's state rather than a yes/no use this: a deleted
   * Inventory item still has a working viewer in the trash, so the link card reports it as readable
   * and deleted rather than hiding it.
   *
   * <p>Type-exact: samples and sample templates share one numeric id space, so a record whose own
   * Global ID prefix differs from the requested one is not the target. Empty for a non-Inventory
   * prefix, since resolving ELN records runs through transactional {@code *Manager} proxies that
   * throw for missing or deleted records and would mark the caller's transaction rollback-only.
   *
   * @param target the parsed link target GlobalID (any version suffix is ignored)
   * @param user the acting user, whose READ permission decides
   * @return the record, or empty if it does not exist, is not readable, is of another kind, or the
   *     prefix is not an Inventory one
   */
  Optional<InventoryRecord> readableInventoryTarget(GlobalIdentifier target, User user);
}
