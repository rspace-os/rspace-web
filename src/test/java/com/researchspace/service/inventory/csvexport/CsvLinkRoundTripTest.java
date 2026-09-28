package com.researchspace.service.inventory.csvexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.inventory.csvimport.CsvLinkValueParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The exporter and the importer are tested apart, each against the same hand-written literal cell,
 * so nothing has pinned the one property the feature exists for: that what the exporter actually
 * writes is what the importer accepts. The two sides derive the URL differently ({@code
 * globalIdPageUrl} against {@code globalIdPagePrefix}) and handle the relation token and the {@code
 * vN} suffix independently, so either can move without a test noticing until a customer's exported
 * CSV will not re-import.
 *
 * <p>Lives in the exporter's package because {@code csvValueForLink} is protected; the parser's
 * injected property holder is reached with {@link ReflectionTestUtils} rather than widening it.
 */
public class CsvLinkRoundTripTest {

  private static final String SERVER_URL = "https://rspace.example.com/";

  private final CsvInstrumentExporter exporter = new CsvInstrumentExporter();
  private final CsvLinkValueParser parser = new CsvLinkValueParser();

  @BeforeEach
  void setUp() {
    useServerUrl(SERVER_URL);
  }

  @ParameterizedTest
  @CsvSource({
    "IsDerivedFrom, SA, 123, 2",
    "Cites, GL, 44, ",
    "References, IT, 7, 11",
    "IsCalibratedBy, IN, 9, ",
    "IsDocumentedBy, SD, 5, 3"
  })
  void exportedCellReimportsAsTheSameLink(
      String relation, GlobalIdPrefix prefix, long dbId, Long versionPin) {
    String cell = exporter.csvValueForLink(link(relation, prefix, dbId, versionPin));

    ApiInventoryLink reimported = parser.parse(cell);

    assertEquals(relation, reimported.getRelationType());
    assertEquals(prefix.name() + dbId, reimported.getTargetGlobalId());
    assertEquals(versionPin, reimported.getVersionPin());
  }

  @Test
  void aServerUrlWithTrailingSlashesStillRoundTrips() {
    // the exporter strips them all through InventoryUrls; a reader normalising differently would
    // build a prefix that no longer matches its own side's output
    useServerUrl("https://rspace.example.com//");

    ApiInventoryLink reimported =
        parser.parse(exporter.csvValueForLink(link("Cites", GlobalIdPrefix.SA, 1L, null)));

    assertEquals("SA1", reimported.getTargetGlobalId());
    assertNull(reimported.getVersionPin());
  }

  private void useServerUrl(String serverUrl) {
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn(serverUrl);
    exporter.properties = properties;
    ReflectionTestUtils.setField(parser, "properties", properties);
  }

  private static InventoryLink link(
      String relation, GlobalIdPrefix prefix, long dbId, Long versionPin) {
    InventoryLink link = new InventoryLink();
    link.setRelationType(relation);
    link.setTargetPrefix(prefix);
    link.setTargetDbId(dbId);
    link.setTargetGlobalId(prefix.name() + dbId);
    link.setVersionPin(versionPin);
    return link;
  }
}
