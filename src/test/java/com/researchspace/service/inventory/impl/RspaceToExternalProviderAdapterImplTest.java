package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RspaceToExternalProviderAdapterImplTest {

  private final RspaceToExternalProviderAdapterImpl adapter =
      new RspaceToExternalProviderAdapterImpl();

  @Mock private InventoryRecord instrument;
  @Mock private User owner;

  @Test
  void buildsB2instDoiFromInstrumentFields() {
    when(instrument.isInstrument()).thenReturn(true);
    when(instrument.getName()).thenReturn("Microscope X");
    when(instrument.getOwner()).thenReturn(owner);
    when(owner.getFullName()).thenReturn("Jane Doe");
    when(owner.getEmail()).thenReturn("jane@example.org");

    B2instDoi doi = adapter.buildB2instDoi(instrument);

    assertEquals("Microscope X", doi.getMetadata().getName());
    assertEquals("1.0", doi.getMetadata().getSchemaVersion());
    assertEquals("Jane Doe", doi.getMetadata().getOwner().get(0).getOwnerName());
    assertEquals("jane@example.org", doi.getMetadata().getOwner().get(0).getOwnerContact());
    assertEquals("public", doi.getAccess().getRecord());
    assertFalse(doi.getFiles().getEnabled());
  }

  @Test
  void rejectsNonInstrumentRecord() {
    when(instrument.isInstrument()).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> adapter.buildB2instDoi(instrument));
  }

  @Test
  void delegatesDataCiteToConvertToDataCiteDoi() {
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.setTitle("My DOI");

    DataCiteDoi result = adapter.buildDataCiteDoi(doi);

    assertEquals("My DOI", result.getAttributes().getTitles().get(0).getTitle());
  }
}
