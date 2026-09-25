package com.researchspace.service.inventory.csvexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.properties.IPropertyHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Instrument link fields use the same single-cell form as sample ones. */
public class CsvInstrumentLinkExportTest {

  private final CsvInstrumentExporter exporter = new CsvInstrumentExporter();

  @BeforeEach
  void setUp() {
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example.com/");
    exporter.properties = properties;
  }

  @Test
  void pinnedLinkExportsAsRelationTypeAndGlobalIdUrl() {
    InventoryLink link = link("IsDerivedFrom", GlobalIdPrefix.IN, 9L, 3L);

    assertEquals(
        "IsDerivedFrom https://rspace.example.com/globalId/IN9v3", exporter.csvValueForLink(link));
  }

  @Test
  void unpinnedLinkOmitsTheVersionSuffix() {
    InventoryLink link = link("Cites", GlobalIdPrefix.SA, 44L, null);

    assertEquals("Cites https://rspace.example.com/globalId/SA44", exporter.csvValueForLink(link));
  }

  @Test
  void unsetLinkExportsAsAnEmptyCell() {
    assertEquals("", exporter.csvValueForLink(null));
  }

  @Test
  void blankServerUrlExportsTheRelationAloneRatherThanAnAddressToNowhere() {
    // a deployment with server.urls.prefix unset cannot name a resolvable target, so the cell
    // carries the relation only. Deliberately asymmetric: such a cell will not re-import.
    IPropertyHolder unset = mock(IPropertyHolder.class);
    when(unset.getServerUrl()).thenReturn("");
    exporter.properties = unset;

    assertEquals("Cites", exporter.csvValueForLink(link("Cites", GlobalIdPrefix.SA, 1L, null)));
  }

  private static InventoryLink link(String relation, GlobalIdPrefix prefix, long dbId, Long pin) {
    InventoryLink link = new InventoryLink();
    link.setRelationType(relation);
    link.setTargetPrefix(prefix);
    link.setTargetDbId(dbId);
    link.setTargetGlobalId(prefix.name() + dbId);
    link.setVersionPin(pin);
    return link;
  }
}
