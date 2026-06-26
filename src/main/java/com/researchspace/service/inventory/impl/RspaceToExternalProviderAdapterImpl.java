package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.b2inst.model.common.B2instAccess;
import com.researchspace.b2inst.model.common.B2instFilesOptions;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.b2inst.model.metadata.B2instOwner;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.inventory.RspaceToExternalProviderAdapter;
import java.util.List;

/** See {@link RspaceToExternalProviderAdapter}. */
public class RspaceToExternalProviderAdapterImpl implements RspaceToExternalProviderAdapter {

  private static final String PIDINST_SCHEMA_VERSION = "1.0";
  private static final String PUBLIC_ACCESS = "public";

  @Override
  public B2instDoi buildB2instDoi(InventoryRecord instrument) {
    if (instrument == null || !instrument.isInstrument()) {
      throw new IllegalArgumentException(
          "B2INST instrument PIDs can only be built for Instrument records (IN*)");
    }
    B2instInstrumentMetadata metadata = new B2instInstrumentMetadata();
    metadata.setName(instrument.getName());
    metadata.setSchemaVersion(PIDINST_SCHEMA_VERSION);
    metadata.setOwner(List.of(ownerOf(instrument.getOwner())));
    // Manufacturer, Model and LandingPage are intentionally left unset at draft creation: the
    // Instrument entity has no manufacturer/model fields, and the public landing URL needs a DOI
    // public link that only exists once the identifier is persisted. See ADR-002.

    B2instAccess access = new B2instAccess();
    access.setRecord(PUBLIC_ACCESS);
    access.setFiles(PUBLIC_ACCESS);

    B2instDoi doi = new B2instDoi();
    doi.setMetadata(metadata);
    doi.setAccess(access);
    doi.setFiles(new B2instFilesOptions(false));
    return doi;
  }

  private B2instOwner ownerOf(User owner) {
    B2instOwner b2instOwner = new B2instOwner();
    if (owner != null) {
      b2instOwner.setOwnerName(owner.getFullName());
      b2instOwner.setOwnerContact(owner.getEmail());
    }
    return b2instOwner;
  }

  @Override
  public DataCiteDoi buildDataCiteDoi(ApiInventoryDOI doi) {
    return doi.convertToDataCiteDoi();
  }
}
