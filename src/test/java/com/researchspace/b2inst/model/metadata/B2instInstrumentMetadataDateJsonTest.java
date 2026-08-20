package com.researchspace.b2inst.model.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guards the PIDINST {@code Date} wire keys. The EUDAT docs do not specify the entry's inner keys,
 * so they were verified against production b2inst.gwdg.de records: the inner date key is {@code
 * Date} (PascalCase, same as the outer list property), paired with a lowerCamelCase {@code
 * dateType}.
 *
 * <p>Assertions compare parsed {@link Map}s rather than raw JSON strings. Jackson gives no ordering
 * guarantee for a POJO without {@code @JsonPropertyOrder}, so a whole-string comparison would break
 * on a harmless field reordering or a Jackson/JDK upgrade. Map equality is order-independent but
 * still exact per entry, so it keeps catching a renamed, missing or unexpected extra wire key,
 * which is what this test exists to guard.
 *
 * <p>Assertions are scoped to the {@code Date} property rather than the whole serialised object, so
 * that adding an unrelated field to {@link B2instInstrumentMetadata} cannot fail these tests.
 */
class B2instInstrumentMetadataDateJsonTest {

  private static final TypeReference<Map<String, Object>> JSON_OBJECT =
      new TypeReference<Map<String, Object>>() {};

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serialisesDateListWithPidinstWireKeys() throws Exception {
    B2instInstrumentMetadata metadata = new B2instInstrumentMetadata();
    metadata.setDate(
        List.of(
            b2instDate("2024-02-21", "Commissioned"), b2instDate("2025-04-23", "DeCommissioned")));

    Map<String, Object> json = mapper.readValue(mapper.writeValueAsString(metadata), JSON_OBJECT);

    assertEquals(
        List.of(
            Map.of("Date", "2024-02-21", "dateType", "Commissioned"),
            Map.of("Date", "2025-04-23", "dateType", "DeCommissioned")),
        json.get("Date"));
  }

  @Test
  void omitsDateWhenNull() throws Exception {
    Map<String, Object> json =
        mapper.readValue(mapper.writeValueAsString(new B2instInstrumentMetadata()), JSON_OBJECT);

    assertFalse(json.containsKey("Date"));
  }

  @Test
  void deserialisesFromWireFormat() throws Exception {
    B2instInstrumentMetadata metadata =
        mapper.readValue(
            "{\"Date\":[{\"Date\":\"2023-12-01\",\"dateType\":\"Commissioned\"}]}",
            B2instInstrumentMetadata.class);

    assertEquals(1, metadata.getDate().size());
    assertEquals("2023-12-01", metadata.getDate().get(0).getDate());
    assertEquals("Commissioned", metadata.getDate().get(0).getDateType());
  }

  /**
   * Sets fields by name so the test does not depend on the generated constructor's argument order.
   */
  private static B2instDate b2instDate(String date, String dateType) {
    B2instDate b2instDate = new B2instDate();
    b2instDate.setDate(date);
    b2instDate.setDateType(dateType);
    return b2instDate;
  }
}
