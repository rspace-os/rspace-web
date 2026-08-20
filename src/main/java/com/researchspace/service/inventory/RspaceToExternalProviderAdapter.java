package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.inventory.InventoryRecord;

/**
 * Translates an RSpace inventory record into the domain wrapper of the requested external PID
 * provider. This is the seam the future generic registration endpoints (RSDEV-1209) will build on;
 * in this story only the B2INST path is routed through it, while DataCite delegates to the existing
 * {@link ApiInventoryDOI#convertToDataCiteDoi()}.
 *
 * <p>Provider guard rails: {@code PIDINST_*} providers accept only Instrument records ({@code
 * IN*}); {@code IGSN_*} providers accept only {@code IC*}/{@code SA*}/{@code SS*}.
 */
public interface RspaceToExternalProviderAdapter {

  /**
   * Build the B2INST create-record wrapper from an Instrument. Custom fields whose trimmed name
   * (case-insensitive) and type match the default "Instrument (PIDINST 1.0)" template's field
   * definitions are mapped into the PIDINST metadata; see CONTEXT.md ("PIDINST-mapped field").
   * LandingPage is the Landing page field when it holds a value the user typed themselves, else the
   * identifier's public landing page, and is omitted entirely when neither exists rather than sent
   * wrong (see ADR 0006). Owner always has exactly one entry (field content, falling back to the
   * record owner).
   *
   * <p>Not every template field is mapped. "Measurement technique", "Calibration" and "Last
   * calibrated" are documentation-only and feed nothing, because PIDINST has no property that fits
   * them; see CONTEXT.md ("Documentation-only field") and the superseded
   * DevDocs/adr/0005-measured-variable-narratives.md for the attempt that was rejected.
   *
   * <p>Must be called inside an existing transaction: the mapping reads the instrument's lazy
   * associations. The implementation declares {@code @Transactional(propagation = MANDATORY)}, so a
   * caller with no transaction fails immediately rather than part-way through the mapping.
   *
   * @param publicLandingPageUrl the identifier's public landing page address, registered as
   *     LandingPage when the instrument's Landing page field holds no address a resolver could
   *     follow; null when no server URL is configured, in which case the property is omitted rather
   *     than sent wrong — but only when there is no usable field value either, since a user-typed
   *     address still wins
   */
  B2instDoi buildB2instDoi(InventoryRecord instrument, String publicLandingPageUrl);

  /** Build the DataCite DOI wrapper from the RSpace DOI representation. */
  DataCiteDoi buildDataCiteDoi(ApiInventoryDOI doi);
}
