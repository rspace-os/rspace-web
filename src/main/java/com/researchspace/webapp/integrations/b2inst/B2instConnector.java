package com.researchspace.webapp.integrations.b2inst;

import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRequestResponse;

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

  /** Re-read the {@code pidinst.b2inst.*} system properties and rebuild the HTTP client. */
  void reloadClient();

  /** True when B2INST is enabled and the server URL and token are configured. */
  boolean isConfiguredAndEnabled();

  /** Lightweight authenticated probe of the configured B2INST instance. */
  boolean testConnection();
}
