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

  /** Build the B2INST create-record wrapper from an Instrument. */
  B2instDoi buildB2instDoi(InventoryRecord instrument);

  /** Build the DataCite DOI wrapper from the RSpace DOI representation. */
  DataCiteDoi buildDataCiteDoi(ApiInventoryDOI doi);
}
