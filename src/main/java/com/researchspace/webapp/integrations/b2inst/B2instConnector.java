package com.researchspace.webapp.integrations.b2inst;

import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRequestResponse;
import java.util.Optional;

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
   * <p>InvenioRDM keeps a draft writable in every review state except {@code accepted}: as a plain
   * {@code draft}, while a community review is open ({@code submitted}), and equally while the
   * review sits at {@code created}, {@code cancelled}, {@code declined} or {@code expired}. That
   * exclusion rule, rather than the narrower draft-and-submitted pair originally assumed, is what
   * the on-save external metadata update is built on (RSDEV-1251, ADR 0008), and it was verified
   * against b2inst-test.gwdg.de in August 2026. Acceptance publishes the record and removes its
   * draft, so an accepted record has nothing writable left and B2INST rejects the call.
   *
   * <p>The body is the same register-shaped payload {@link #registerDoi(B2instDoi)} sends, rebuilt
   * from the instrument's current fields: the endpoint replaces the metadata block wholesale, so a
   * partial body would silently drop properties.
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

  /** Re-read the {@code pidinst.b2inst.*} system properties and rebuild the HTTP client. */
  void reloadClient();

  /** True when B2INST is enabled and the server URL and token are configured. */
  boolean isConfiguredAndEnabled();

  /** Lightweight authenticated probe of the configured B2INST instance. */
  boolean testConnection();
}
