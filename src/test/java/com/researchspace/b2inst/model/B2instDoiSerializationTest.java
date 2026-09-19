package com.researchspace.b2inst.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.b2inst.model.common.B2instAccess;
import com.researchspace.b2inst.model.common.B2instFilesOptions;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.b2inst.model.metadata.B2instOwner;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the JSON key conventions easiest to break when porting/renaming the B2INST POJOs: the
 * PIDINST keys on the request side (PascalCase at the top level, lowerCamelCase within nested
 * objects such as {@code Owner[].ownerName}) and the snake_case keys on the response side stay
 * exactly as the B2INST API expects, regardless of the Java {@code B2inst*} class names.
 */
class B2instDoiSerializationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void serializesPidinstPascalCaseKeysAndOmitsNulls() throws Exception {
    B2instInstrumentMetadata md = new B2instInstrumentMetadata();
    md.setName("My Instrument");
    md.setSchemaVersion("1.0");
    B2instOwner owner = new B2instOwner();
    owner.setOwnerName("Jane Doe");
    owner.setOwnerContact("jane@example.com");
    md.setOwner(List.of(owner));

    B2instDoi doi = new B2instDoi();
    doi.setMetadata(md);
    doi.setAccess(new B2instAccess());
    doi.setFiles(new B2instFilesOptions(false));

    JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(doi));
    JsonNode metadata = json.get("metadata");

    assertEquals("My Instrument", metadata.get("Name").asText());
    assertEquals("1.0", metadata.get("SchemaVersion").asText());
    assertEquals("Jane Doe", metadata.get("Owner").get(0).get("ownerName").asText());
    assertFalse(json.get("files").get("enabled").asBoolean());
    // NON_NULL: unset PIDINST fields must not be emitted into the request body.
    assertFalse(metadata.has("Manufacturer"), "null Manufacturer must be omitted");
    assertFalse(metadata.has("Model"), "null Model must be omitted");
  }

  @Test
  void deserializesDraftRecordSnakeCaseResponseKeys() throws Exception {
    // Mirrors the create-draft response: the RID and the snake_case links the connector reads.
    String json =
        "{\"id\":\"k2j9p-7yh21\",\"is_draft\":true,\"status\":\"draft\","
            + "\"links\":{\"self_html\":\"https://host/uploads/k2j9p-7yh21\","
            + "\"reserve_doi\":\"https://host/api/records/k2j9p-7yh21/draft/pids/doi\"}}";

    B2instDraftRecord record = MAPPER.readValue(json, B2instDraftRecord.class);

    assertEquals("k2j9p-7yh21", record.getId());
    assertTrue(record.getIsDraft());
    assertEquals("draft", record.getStatus());
    assertEquals("https://host/uploads/k2j9p-7yh21", record.getLinks().getSelfHtml());
    assertEquals(
        "https://host/api/records/k2j9p-7yh21/draft/pids/doi", record.getLinks().getReserveDoi());
  }

  @Test
  void ignoresUnknownPropertiesWhenDeserializing() throws Exception {
    // The B2INST API returns extra fields we do not model; deserialization must not fail.
    String json = "{\"metadata\":{\"Name\":\"X\",\"some_future_field\":123},\"unexpected\":true}";

    B2instDoi doi = MAPPER.readValue(json, B2instDoi.class);

    assertEquals("X", doi.getMetadata().getName());
  }
}
