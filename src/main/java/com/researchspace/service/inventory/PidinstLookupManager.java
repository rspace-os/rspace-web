package com.researchspace.service.inventory;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.model.User;
import java.util.List;

/**
 * PID lookup and instrument import against the public registries (RSDEV-1518, ADR 0011; CONTEXT.md
 * "PID lookup", "Instrument import"; ADR 0009). A {@code *Manager} in this package so the inventory
 * transaction advisor applies: the registry calls run inside the transaction, the same ceiling
 * registration already has.
 */
public interface PidinstLookupManager {

  /** Hits per page, for every registry page asked and for the merged page returned (ADR 0011). */
  int PAGE_SIZE = 50;

  /**
   * Shortest query a search accepts, after trimming. Below this a free-text query matches most of
   * the registry, which is a slow call for a page of results nobody wanted.
   */
  int MIN_QUERY_LENGTH = 4;

  /**
   * One merged page of PID lookup hits from the public registries named in {@code providers}
   * ({@code PIDINST_DATACITE}, {@code PIDINST_B2INST}, one or both), ordered by the registry's
   * update time newest first, then creation time, then PID (ADR 0011). A DOI or Handle, bare or as
   * a resolver address, is a direct lookup at the registry its shape names and yields nothing at
   * the other. Only PUBLIC records are offered - B2INST {@code accepted}, DataCite {@code findable}
   * - because only those may be linked to an instrument. A hit whose PID an instrument in this
   * deployment already links is marked {@code alreadyLinked}, and carries {@code
   * linkedInstrumentGlobalId} only when {@code user} may read that instrument (RSDEV-1505).
   * Independent of any PIDINST provider: nothing here reads the minting settings.
   *
   * @throws ApiRuntimeException {@code pidinstQueryTooShort} below {@link #MIN_QUERY_LENGTH} after
   *     trimming, so a direct caller is held to the same rule as the import dialog; {@code
   *     pidinstRegistryRequired} when {@code providers} is null, empty or names anything but the
   *     two PIDINST registries
   * @throws IllegalArgumentException when {@code pageNumber} is negative
   */
  ApiPidinstSearchResult search(String query, List<String> providers, int pageNumber, User user);

  /**
   * Fetches the record for the PID from the public registry named by {@code provider}, creates an
   * Instrument from the locked default PIDINST template filled from it, attaches a linked
   * identifier, and returns the result; one transaction.
   *
   * <p>The record's Measurement Technique and Calibration related identifiers fill the instrument's
   * two link fields when they name an item in this deployment the user can link (RSDEV-1528, ADR
   * 0009 decision 9); every other such entry is returned as a skipped entry on the created
   * instrument, and the import still succeeds.
   *
   * @throws ApiRuntimeException {@code pidinstImportProviderRequired} when {@code provider} is not
   *     one of the two PIDINST registries; or when the record lacks a mandatory value
   * @throws jakarta.ws.rs.NotFoundException when the registry has no PUBLIC instrument record for
   *     the PID, or the value is not a PID of that registry. Only a public record may be linked, so
   *     a PID that exists but is not published is reported exactly like one that does not exist
   * @throws PidinstAlreadyLinkedException when an instrument in this deployment already links the
   *     PID; the message names that instrument only to a caller who may read it (RSDEV-1505)
   */
  ApiInstrument importInstrument(
      String pid, String provider, ApiTargetLocation newTargetLocation, User user);
}
