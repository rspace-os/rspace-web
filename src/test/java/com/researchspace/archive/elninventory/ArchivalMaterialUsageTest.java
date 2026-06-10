package com.researchspace.archive.elninventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.SubSample;
import org.junit.jupiter.api.Test;

public class ArchivalMaterialUsageTest {

  @Test
  public void schemaVersionIs2() {
    SubSample subSample = new SubSample();
    subSample.setId(1L);
    MaterialUsage mu = new MaterialUsage(null, subSample, null);

    ArchivalMaterialUsage archivalUsage = new ArchivalMaterialUsage(mu);

    assertEquals(2, archivalUsage.getSchemaVersion());
  }

  @Test
  public void globalIdIsPopulatedForSubSample() {
    SubSample subSample = new SubSample();
    subSample.setId(7L);
    MaterialUsage mu = new MaterialUsage(null, subSample, null);

    ArchivalMaterialUsage archivalUsage = new ArchivalMaterialUsage(mu);

    assertEquals("SS7", archivalUsage.getGlobalId());
    assertEquals("SUBSAMPLE", archivalUsage.getInvRecType());
    assertEquals(7L, archivalUsage.getInvRecId());
  }

  @Test
  public void globalIdIsPopulatedForInstrument() {
    Instrument instrument = new Instrument();
    instrument.setId(42L);
    MaterialUsage mu = new MaterialUsage(null, instrument, null);

    ArchivalMaterialUsage archivalUsage = new ArchivalMaterialUsage(mu);

    assertEquals("IN42", archivalUsage.getGlobalId());
    assertEquals("INSTRUMENT", archivalUsage.getInvRecType());
    assertEquals(42L, archivalUsage.getInvRecId());
    assertNull(archivalUsage.getUsageValue()); // no quantity for instruments
  }
}
