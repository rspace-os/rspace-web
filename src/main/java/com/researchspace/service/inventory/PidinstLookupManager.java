package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.model.User;

/**
 * PID lookup and instrument import against the deployment's enabled PIDINST provider (RSDEV-1326;
 * CONTEXT.md "PID lookup", "Instrument import"; ADR 0009). A {@code *Manager} in this package so
 * the inventory transaction advisor applies: the provider calls run inside the transaction, the
 * same ceiling registration already has.
 */
public interface PidinstLookupManager {

  /** Hits per search; one page, no paging (decision 6). */
  int MAX_HITS = 50;

  /**
   * A DOI or Handle, bare or as a doi.org / hdl.handle.net address, is a direct lookup on the
   * enabled provider; a PID of the other registry yields no hit; anything else is one full-text
   * query. Only PUBLIC records are offered - B2INST {@code accepted}, DataCite {@code findable} -
   * because only those may be linked to an instrument. Hits are sorted by name and carry {@code
   * linkedInstrumentGlobalId} when an instrument in this deployment already links the PID.
   *
   * @throws UnsupportedOperationException when no PIDINST provider is enabled
   */
  ApiPidinstSearchResult search(String query, User user);

  /**
   * Fetches the record for the PID from the enabled provider, creates an Instrument from the locked
   * default PIDINST template filled from it, attaches a linked identifier, and returns the result;
   * one transaction.
   *
   * @throws jakarta.ws.rs.NotFoundException when the provider has no PUBLIC instrument record for
   *     the PID, or the value is not a PID of the enabled provider. Only a public record may be
   *     linked, so a PID that exists but is not published is reported exactly like one that does
   *     not exist
   * @throws PidinstAlreadyLinkedException when an instrument in this deployment already links the
   *     PID
   * @throws com.researchspace.api.v1.auth.ApiRuntimeException when the record lacks a mandatory
   *     value
   */
  ApiInstrument importInstrument(String pid, ApiTargetLocation newTargetLocation, User user);
}
