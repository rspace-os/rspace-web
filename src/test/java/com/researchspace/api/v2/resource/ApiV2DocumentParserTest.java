package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import com.researchspace.model.collection.DocumentValidationException.Violation;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.SplitReferenceBinding;
import com.researchspace.service.CollectionMutationException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiV2DocumentParserTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void preservesPresenceAndExplicitNullMessage() throws Exception {
    ParsedDocument patch =
        parseMaintenanceUpdate(
            mapper.readTree(
                """
                {
                  "startDate": "2026-08-01T10:00:00Z",
                  "stopUserLoginDate": "2026-08-01T09:45:00Z",
                  "message": null
                }
                """));

    assertTrue(patch.values().containsKey("startDate"));
    assertEquals(Date.from(Instant.parse("2026-08-01T10:00:00Z")), patch.values().get("startDate"));
    assertFalse(patch.values().containsKey("endDate"));
    assertTrue(patch.values().containsKey("stopUserLoginDate"));
    assertTrue(patch.values().containsKey("message"));
    assertNull(patch.values().get("message"));
  }

  @Test
  void rejectsEmptyUnknownAndNullDateFields() throws Exception {
    assertThrows(
        DocumentValidationException.class, () -> parseMaintenanceUpdate(mapper.readTree("{}")));
    assertThrows(
        DocumentValidationException.class,
        () -> parseMaintenanceUpdate(mapper.readTree("{\"id\":42}")));
    assertThrows(
        DocumentValidationException.class,
        () -> parseMaintenanceUpdate(mapper.readTree("{\"endDate\":null}")));
  }

  @Test
  void rejectsNonTextualUnparsableAndOversizedValues() throws Exception {
    assertThrows(
        DocumentValidationException.class,
        () -> parseMaintenanceUpdate(mapper.readTree("{\"startDate\":42}")));
    assertThrows(
        DocumentValidationException.class,
        () -> parseMaintenanceUpdate(mapper.readTree("{\"startDate\":\"not-a-date\"}")));

    String oversized = "x".repeat(User.DEFAULT_MAXFIELD_LEN + 1);
    assertThrows(
        DocumentValidationException.class,
        () -> parseMaintenanceUpdate(mapper.readTree("{\"message\":\"" + oversized + "\"}")));
  }

  @Test
  void enforcesCreateRequirementsAndSuppliesFixedFieldDefaults() throws Exception {
    CollectionDescription<TestDocument> description =
        new CollectionDescription<>(
            "testDocuments",
            TestDocument.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), TestDocument::id),
                Field.writable(
                        "name",
                        "name",
                        CollectionFieldTypes.text(10),
                        TestDocument::name,
                        TestDocument::setName)
                    .required(),
                Field.writable(
                        "label",
                        "label",
                        CollectionFieldTypes.text(10),
                        TestDocument::label,
                        TestDocument::setLabel)
                    .defaultValue("default"),
                Field.writable(
                    "active",
                    "active",
                    CollectionFieldTypes.bool(),
                    TestDocument::active,
                    TestDocument::setActive)),
            List.of(),
            "id",
            List.of(new Sort("id", true)));

    ParsedDocument document =
        ApiV2DocumentParser.parse(
            mapper.readTree("{\"name\":\"document\",\"active\":true}"),
            description,
            WriteOperation.CREATE,
            "error",
            writeContext(Operation.CREATE));

    assertEquals(Map.of("name", "document", "label", "default", "active", true), document.values());
    assertThrows(
        DocumentValidationException.class,
        () ->
            ApiV2DocumentParser.parse(
                mapper.readTree("{}"),
                description,
                WriteOperation.CREATE,
                "error",
                writeContext(Operation.CREATE)));
    assertThrows(
        DocumentValidationException.class,
        () ->
            ApiV2DocumentParser.parse(
                mapper.readTree("{\"name\":42}"),
                description,
                WriteOperation.CREATE,
                "error",
                writeContext(Operation.CREATE)));
    DocumentValidationException multipleFailures =
        assertThrows(
            DocumentValidationException.class,
            () ->
                ApiV2DocumentParser.parse(
                    mapper.readTree("{\"name\":42,\"unknown\":true}"),
                    description,
                    WriteOperation.CREATE,
                    "error",
                    writeContext(Operation.CREATE)));
    assertEquals(
        List.of(
            new Violation("name", Reason.WRONG_TYPE),
            new Violation("unknown", Reason.UNKNOWN_FIELD)),
        multipleFailures.getViolations());
  }

  @Test
  void parsesNonEmptyBulkCreateEnvelopeAndReportsTheDocumentIndex() throws Exception {
    List<ParsedDocument> documents =
        ApiV2DocumentParser.parseMany(
            mapper.readTree(
                """
                {"docs":[
                  {"startDate":"2026-08-01T10:00:00Z","endDate":"2026-08-01T11:00:00Z"},
                  {"startDate":"2026-08-02T10:00:00Z","endDate":"2026-08-02T11:00:00Z"}
                ]}
                """),
            ApiV2MaintenanceResource.DESCRIPTION,
            "error",
            writeContext(Operation.CREATE));

    assertEquals(2, documents.size());
    assertEquals(WriteOperation.CREATE, documents.get(0).operation());

    DocumentValidationException invalidDocument =
        assertThrows(
            DocumentValidationException.class,
            () ->
                ApiV2DocumentParser.parseMany(
                    mapper.readTree(
                        """
                        {"docs":[
                          {"startDate":"2026-08-01T10:00:00Z","endDate":"2026-08-01T11:00:00Z"},
                          {"startDate":42}
                        ]}
                        """),
                    ApiV2MaintenanceResource.DESCRIPTION,
                    "error",
                    writeContext(Operation.CREATE)));
    assertEquals(
        List.of(
            new Violation("docs[1].startDate", Reason.WRONG_TYPE),
            new Violation("docs[1].endDate", Reason.REQUIRED)),
        invalidDocument.getViolations());

    for (String invalid :
        List.of("{}", "{\"docs\":[]}", "{\"docs\":{}}", "{\"docs\":[],\"extra\":true}")) {
      DocumentValidationException exception =
          assertThrows(
              DocumentValidationException.class,
              () ->
                  ApiV2DocumentParser.parseMany(
                      mapper.readTree(invalid),
                      ApiV2MaintenanceResource.DESCRIPTION,
                      "error",
                      writeContext(Operation.CREATE)));
      assertEquals(
          List.of(new Violation("docs", Reason.INVALID_DOCUMENT)), exception.getViolations());
    }
  }

  @Test
  void rejectsOversizedBulkCreateBeforeParsingIndividualDocuments() throws Exception {
    var docs = mapper.createArrayNode();
    for (int i = 0; i <= CollectionMutationLimits.MAX_BULK_CREATE_ROWS; i++) {
      docs.addObject();
    }
    var body = mapper.createObjectNode().set("docs", docs);

    CollectionMutationException exception =
        assertThrows(
            CollectionMutationException.class,
            () ->
                ApiV2DocumentParser.parseMany(
                    body,
                    ApiV2MaintenanceResource.DESCRIPTION,
                    "error",
                    writeContext(Operation.CREATE)));

    assertEquals(CollectionMutationException.Reason.BULK_LIMIT, exception.getReason());
  }

  @Test
  void parsesRelationshipObjectsAndUpdateGlobalIdToTheSameReference() throws Exception {
    CollectionDescription<RelatedDocument> description = relatedDescription();

    ParsedDocument created =
        ApiV2DocumentParser.parse(
            mapper.readTree("{\"target\":{\"relationTo\":\"instruments\",\"value\":9}}"),
            description,
            WriteOperation.CREATE,
            "error",
            writeContext(Operation.CREATE));
    ParsedDocument updated =
        ApiV2DocumentParser.parse(
            mapper.readTree(
                "{\"target\":{\"relationTo\":\"instruments\",\"value\":9,\"globalId\":\"IN9\"}}"),
            description,
            WriteOperation.UPDATE,
            "error",
            writeContext(Operation.UPDATE));
    ParsedDocument globalIdOnly =
        ApiV2DocumentParser.parse(
            mapper.readTree("{\"target\":\"IN9\"}"),
            description,
            WriteOperation.UPDATE,
            "error",
            writeContext(Operation.UPDATE));

    assertEquals(new ResourceReference<>("INSTRUMENT", 9L), created.values().get("target"));
    assertEquals(created.values().get("target"), updated.values().get("target"));
    assertEquals(created.values().get("target"), globalIdOnly.values().get("target"));
  }

  @Test
  void rejectsIncompleteNullAndCreateGlobalIdRelationshipInputs() throws Exception {
    CollectionDescription<RelatedDocument> description = relatedDescription();

    for (String body :
        List.of(
            "{\"target\":{\"relationTo\":\"instruments\"}}",
            "{\"target\":{\"relationTo\":\"instruments\",\"value\":9,\"extra\":true}}",
            "{\"target\":{\"relationTo\":\"instruments\",\"value\":9,\"globalId\":\"IN10\"}}",
            "{\"target\":null}",
            "{\"target\":\"IN9\"}")) {
      assertThrows(
          DocumentValidationException.class,
          () ->
              ApiV2DocumentParser.parse(
                  mapper.readTree(body),
                  description,
                  WriteOperation.CREATE,
                  "error",
                  writeContext(Operation.CREATE)));
    }
  }

  private static ParsedDocument parseMaintenanceUpdate(JsonNode body) {
    return ApiV2DocumentParser.parse(
        body,
        ApiV2MaintenanceResource.DESCRIPTION,
        WriteOperation.UPDATE,
        "errors.api.v2.maintenance.patch",
        writeContext(Operation.UPDATE));
  }

  private static final class TestDocument {

    private String name;
    private String label;
    private Boolean active;

    private Long id() {
      return 1L;
    }

    private String name() {
      return name;
    }

    private void setName(String name) {
      this.name = name;
    }

    private String label() {
      return label;
    }

    private void setLabel(String label) {
      this.label = label;
    }

    private Boolean active() {
      return active;
    }

    private void setActive(Boolean active) {
      this.active = active;
    }
  }

  private record RelatedDocument(Long id, ResourceReference<String, Long> target) {}

  private record Instrument(Long id) {}

  private static CollectionDescription<RelatedDocument> relatedDescription() {
    return new CollectionDescription<>(
        "relatedDocuments",
        RelatedDocument.class,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), RelatedDocument::id)),
        List.of(
            CollectionDescription.Relationship.polymorphicToOne(
                    "target",
                    CollectionFieldTypes.longNumber(),
                    List.of(
                        new RelationshipTarget<>(
                            "instruments", "INSTRUMENT", "IN", Instrument.class)),
                    new SplitReferenceBinding<>(RelatedDocument::target, "targetType", "targetId"))
                .required()
                .acceptGlobalIdOn(WriteOperation.UPDATE)),
        "id",
        List.of(new Sort("id", true)));
  }

  /** A context that imposes no field-level write restriction. */
  private static AccessContext writeContext(Operation operation) {
    return new AccessContext(null, operation, "widgets");
  }
}
