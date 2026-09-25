package com.researchspace.webapp.integrations.b2inst;

import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRequestResponse;
import com.researchspace.b2inst.model.response.B2instSearchResult;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Talks to a B2INST (EUDAT, Invenio-RDM) instance to register instrument PIDs. Mirrors the
 * operation set of {@code DataCiteConnector} so the inventory identifier manager can treat B2INST
 * as an alternative {@code PIDINST} provider, but is B2INST-typed because the wire model differs.
 *
 * <p>Only {@link #registerDoi(B2instDoi)} (create draft) is fully exercised end-to-end. Publishing
 * goes through the Invenio community review/submit flow and is curator-gated on the configured
 * community, so {@link #publishDoi(String)} is best-effort and {@link #retractDoi(String)} is not
 * supported by B2INST.
 */
public interface B2instConnector {

  /** Create a draft record from the given metadata. The returned draft carries the RID. */
  B2instDraftRecord registerDoi(B2instDoi doi);

  /**
   * Full-replace update of a record's draft metadata, returning the updated draft.
   *
   * <p>InvenioRDM keeps a draft writable in every review state except {@code accepted}, which
   * publishes the record and removes its draft; see {@code
   * InventoryIdentifierExternalUpdateService.B2INST_PUBLISHED_STATES} for why that is an exclusion
   * rather than an inclusion list.
   *
   * <p>The body is the same register-shaped payload {@link #registerDoi(B2instDoi)} sends: the
   * endpoint replaces the metadata block wholesale, so a partial body silently drops properties.
   */
  B2instDraftRecord updateDraftDoi(String rid, B2instDoi doi);

  /** Delete a draft record by its RID. Returns true on success. */
  boolean deleteDoi(String rid);

  /**
   * Submit the draft (by RID) to the configured community for review (best-effort, curator-gated).
   */
  B2instRequestResponse publishDoi(String rid);

  /**
   * B2INST/Invenio has no retract operation; always throws {@link UnsupportedOperationException}.
   */
  B2instRequestResponse retractDoi(String rid);

  /**
   * The record's community-submission review request, whatever its status, or empty when B2INST
   * answers 404: no review was ever created, or the draft no longer exists because the record was
   * published (community accepted) or deleted.
   */
  Optional<B2instRequestResponse> getReviewOf(String rid);

  /** The published record by its RID, or empty when B2INST answers 404 (not published). */
  Optional<B2instDraftRecord> getPublishedRecord(String rid);

  /** The record's draft by its RID, or empty when B2INST answers 404 (no draft exists). */
  Optional<B2instDraftRecord> getDraftRecord(String rid);

  /**
   * Published records whose metadata matches a free-text query, at most {@code size} of them plus
   * the provider's total. The data is anonymous-readable, but the call carries the configured token
   * like every other one, and goes to the configured server (RSDEV-1326).
   */
  B2instSearchResult searchRecords(String query, int size);

  /** The shape a record id or Handle suffix must have before it is put in a URL. */
  Pattern RECORD_ID_SHAPE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  /**
   * The PUBLISHED record a Handle resolves to, or empty when B2INST has none. B2INST accepts the
   * suffix of a PID it minted as an alias of the record id (verified against b2inst.gwdg.de, where
   * the suffix is the b2rec uuid, and b2inst-test.gwdg.de, where it equals the record id; September
   * 2026), so this is {@link #getPublishedRecord(String)} on that suffix. Accepts the bare Handle
   * or an hdl.handle.net address. A suffix that is not a record id shape is answered empty locally.
   *
   * <p>Published only, deliberately. Only a public PID may be linked to an instrument (RSDEV-1326),
   * and {@code /api/records/{rid}} serves exactly that: a record still in draft, submitted for
   * community review, or declined answers 404 there, which is the answer the import wants. Its
   * draft is NOT looked for.
   */
  default Optional<B2instDraftRecord> getRecordByHandle(String handle) {
    if (handle == null || !handle.contains("/")) {
      return Optional.empty();
    }
    String suffix = handle.substring(handle.lastIndexOf('/') + 1).trim();
    if (!RECORD_ID_SHAPE.matcher(suffix).matches()) {
      return Optional.empty();
    }
    return getPublishedRecord(suffix);
  }

  /** Re-read the {@code pidinst.b2inst.*} system properties and rebuild the HTTP client. */
  void reloadClient();

  /** True when B2INST is enabled and the server URL and token are configured. */
  boolean isConfiguredAndEnabled();

  /** Lightweight authenticated probe of the configured B2INST instance. */
  boolean testConnection();
}
