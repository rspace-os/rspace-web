package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** For DAO operations on inventory item identifier */
public interface DigitalObjectIdentifierDao extends GenericDao<DigitalObjectIdentifier, Long> {

  Optional<DigitalObjectIdentifier> getLastPublishedIdentifierByPublicLink(String publicLink);

  List<DigitalObjectIdentifier> getActiveIdentifiersByOwner(User owner);

  /**
   * The active identifier holding exactly this value for this provider type, if any. The instrument
   * import uses it to refuse linking a PID that is already linked in this deployment, and to flag
   * such PIDs in search results (RSDEV-1326). Exact match on the stored value, which is the PID for
   * an imported identifier but the provider's record id for one RSpace registered through B2INST,
   * so a caller asking "is this PID linked?" must try both. Oldest row first should two ever exist
   * (there is no unique key).
   *
   * <p>"Active" is the identifier's own flag, never the state of the record it hangs off, so a PID
   * is released only when something soft-deletes the identifier. Trashing an instrument does
   * (RSDEV-1504, ADR 0010); trashing a sample or container does not.
   */
  Optional<DigitalObjectIdentifier> findActiveByIdentifierAndType(
      String identifier, IdentifierType type);

  /**
   * The same lookup for a whole page of provider hits at once, so annotating a search costs one
   * query rather than one per hit. Oldest row first, so a caller keeping the first match per
   * identifier sees what {@link #findActiveByIdentifierAndType} would have returned. An empty input
   * yields an empty list without touching the database.
   */
  List<DigitalObjectIdentifier> findActiveByIdentifiersAndType(
      Collection<String> identifiers, IdentifierType type);
}
