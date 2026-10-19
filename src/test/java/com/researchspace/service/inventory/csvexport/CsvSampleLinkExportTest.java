package com.researchspace.service.inventory.csvexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.properties.IPropertyHolder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Link fields export as "<RelationType> <serverUrl>/globalId/<GID>[vN]" in a single cell. */
public class CsvSampleLinkExportTest {

  private final CsvSampleExporter exporter = new CsvSampleExporter();
  private Sample sample;
  private InventoryLinkField templateLinkField;
  private ExtraLinkField extraLinkField;

  @BeforeEach
  void setUp() {
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example.com/");
    exporter.properties = properties;

    SampleTemplate template = new SampleTemplate();
    template.setId(7L);
    InventoryLinkField templateField = new InventoryLinkField();
    templateField.setName("Derived from");
    template.addSampleField(templateField);

    sample = new Sample();
    sample.setId(5L);
    templateLinkField = new InventoryLinkField();
    templateLinkField.setName("Derived from");
    templateLinkField.setTemplateField(templateField);
    sample.addSampleField(templateLinkField);

    extraLinkField = new ExtraLinkField();
    extraLinkField.setName("See also");
    sample.addExtraField(extraLinkField);
  }

  @Test
  void pinnedAndUnpinnedLinksExportAsRelationTypeAndGlobalIdUrl() throws IOException {
    templateLinkField.setLink(link("IsDerivedFrom", GlobalIdPrefix.SA, 123L, 2L));
    extraLinkField.setLink(link("Cites", GlobalIdPrefix.GL, 44L, null));

    List<String> row = exportRow();

    assertEquals("IsDerivedFrom https://rspace.example.com/globalId/SA123v2", cell(row, 12));
    assertEquals("Cites https://rspace.example.com/globalId/GL44", cell(row, 13));
  }

  @Test
  void unsetLinksExportAsEmptyCells() throws IOException {
    List<String> row = exportRow();

    assertEquals("", cell(row, 12));
    assertEquals("", cell(row, 13));
  }

  private List<String> exportRow() throws IOException {
    List<String> columns = new ArrayList<>(Collections.nCopies(12, "basic"));
    columns.add(exporter.getColumnNameForSampleField(templateLinkField));
    columns.add(exporter.getColumnNameForExtraField(extraLinkField));
    return exporter.writeSampleCsvDetailsIntoOutput(
        sample, columns, CsvExportMode.FULL, null, new ByteArrayOutputStream());
  }

  private static String cell(List<String> row, int index) {
    return row.get(index);
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
