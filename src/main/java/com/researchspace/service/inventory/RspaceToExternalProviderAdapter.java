package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.inventory.InventoryRecord;

/**
 * Translates an RSpace inventory record into the domain wrapper of the requested external PID
 * provider. This is the seam the future generic registration endpoints (RSDEV-1209) will build on.
 * The B2INST path is built here in full; the DataCite path delegates the base conversion to {@link
 * ApiInventoryDOI#convertToDataCiteDoi()} and adds the PIDINST properties that have no home on the
 * RSpace DOI representation (ADR 0007).
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
   * <p>The "Measurement technique" and "Calibration" link fields are mapped to RelatedIdentifier
   * entries (fixed IsDescribedBy relation, the target's globalId page as a URL, carrying the link's
   * version pin when the target type resolves a version-suffixed id; RSDEV-1253, ADR 0007). "Last
   * calibrated" remains documentation-only, because PIDINST has no property that fits it; see
   * CONTEXT.md ("Documentation-only field") and DevDocs/adr/0005-measured-variable-narratives.md.
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

  /**
   * Build the DataCite DOI wrapper: the RSpace DOI representation converted by {@link
   * ApiInventoryDOI#convertToDataCiteDoi()}, then, when the associated record is an Instrument, the
   * PIDINST related identifiers appended from the Measurement technique and Calibration link fields
   * (RSDEV-1253, ADR 0007): the link target's globalId page as a URL, carrying the link's version
   * pin when the target type resolves one, always related as IsDescribedBy, labelled through
   * relationTypeInformation. An instrument with no live links sends an explicit empty list, which
   * is how DataCite is told to clear the property. Entries whose address would not be an absolute
   * http(s) URL are omitted with a WARN rather than sent wrong.
   *
   * <p>Callers resending full metadata (publish and retract both do) must route through this
   * method, not {@code convertToDataCiteDoi()} directly, or the registered related identifiers
   * silently regress.
   *
   * <p>Must be called inside an existing transaction, whatever the record: the implementation
   * declares {@code @Transactional(propagation = MANDATORY)}, matching {@link #buildB2instDoi}, so
   * a caller without one fails immediately rather than part-way through the mapping. Only the
   * Instrument path actually needs the session, to read the instrument's lazy fields, but the
   * requirement is deliberately uniform: both production callers (publish and retract) are already
   * transactional, and a transaction requirement that changed with the argument's runtime type
   * would be the harder contract to honour.
   *
   * @param associatedRecord the inventory record the DOI belongs to; null or a non-Instrument
   *     yields the plain conversion
   */
  DataCiteDoi buildDataCiteDoi(ApiInventoryDOI doi, InventoryRecord associatedRecord);
}
