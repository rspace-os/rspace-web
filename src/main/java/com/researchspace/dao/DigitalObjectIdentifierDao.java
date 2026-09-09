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
   * such PIDs in search results (RSDEV-1326). Exact match: a Handle or DOI is stored as the
   * provider reports it. Oldest row first should two ever exist (there is no unique key).
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
