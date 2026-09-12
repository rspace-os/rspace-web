package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Binding rules for the operations request body.
 *
 * <p>Every test here reads through a mapper built by {@link Jackson2ObjectMapperBuilder}, the same
 * builder the API's message converter is built from, rather than a bare {@code new ObjectMapper()}:
 * the builder turns FAIL_ON_UNKNOWN_PROPERTIES off, which is what makes an undeclared property
 * ignored rather than a 400. Where the strict bare mapper is also used, that is the point being
 * made: a server-side property must be skipped at binding even by a mapper that rejects unknowns.
 */
class ApiInventoryOperationPostBindingTest {

  /** The mapper the API's own converter is built from, unknown-property feature and all. */
  private final ObjectMapper apiMapper = Jackson2ObjectMapperBuilder.json().build();

  @Test
  void theApiMapperIgnoresAPropertyNoDtoDeclares() {
    // Pins the endpoint's behaviour on a mistyped or obsolete property: dropped silently, like on
    // every other endpoint, since the server builds the sample and a client property cannot change
    // what it builds. If a future Spring or config change makes the shared mapper strict, this
    // fails and the contract changes to a binding 400.
    assertDoesNotThrow(
        () ->
            apiMapper.readValue(
                "{\"operationType\":\"aliquot\",\"origins\":[],\"newSamples\":null}",
                ApiInventoryOperationPost.class));
  }

  @Test
  void aClientAssembledSampleAndOriginFieldsAreDroppedAtBinding() {
    // newSample and origins[].extraFields are the server's: the request builder fills them from
    // the definition, and the core reads them. Both are @JsonIgnore, so a body in the shape the
    // endpoint accepted before DevDocs/adr/0007 M5 binds with neither, even under a
    // mapper that rejects unknown properties (probed: Jackson treats an ignored property as
    // ignorable, not as unknown).
    for (ObjectMapper mapper : new ObjectMapper[] {apiMapper, new ObjectMapper()}) {
      ApiInventoryOperationPost request =
          assertDoesNotThrow(
              () ->
                  mapper.readValue(
                      "{\"operationType\":\"destroy\",\"origins\":[{\"id\":7,"
                          + "\"extraFields\":[{\"name\":\"Disposed\",\"type\":\"text\"}]}],"
                          + "\"newSample\":{\"name\":\"smuggled\"}}",
                      ApiInventoryOperationPost.class));
      assertNull(request.getNewSample(), "newSample is not on the wire");
      assertTrue(request.getOrigins().get(0).getExtraFields().isEmpty(), "origin fields either");
    }
  }

  @Test
  void acceptsEveryPropertyTheEndpointDeclares() {
    ApiInventoryOperationPost request =
        assertDoesNotThrow(
            () ->
                apiMapper.readValue(
                    "{\"operationType\":\"derive\",\"origins\":[{\"id\":7,"
                        + "\"amountMode\":\"explicit\","
                        + "\"amountTaken\":{\"numericValue\":5,\"unitId\":3}}],"
                        + "\"inputs\":{\"processName\":\"PCR\",\"sampleName\":\"Derived\","
                        + "\"count\":1,\"eachAmount\":{\"numericValue\":1,\"unitId\":3}},"
                        + "\"templateId\":42,\"documentedByGlobalId\":\"SD99\"}",
                    ApiInventoryOperationPost.class));
    assertEquals("derive", request.getOperationType());
    ApiInventoryOperationOriginUpdate origin = request.getOrigins().get(0);
    assertEquals(Long.valueOf(7), origin.getId());
    assertEquals(ApiInventoryOperationAmountMode.EXPLICIT, origin.getAmountMode());
    assertEquals(0, new BigDecimal("5").compareTo(origin.getAmountTaken().getNumericValue()));
    assertEquals("PCR", request.getInputs().get("processName"));
    assertEquals(1, request.getInputs().get("count"));
    assertEquals(Long.valueOf(42), request.getTemplateId());
    assertEquals("SD99", request.getDocumentedByGlobalId());
  }

  @Test
  void bindsAmountModeFromItsLowercaseWireValueAndMarksAnythingElseUnknown() {
    assertEquals(
        ApiInventoryOperationAmountMode.EXPLICIT,
        assertDoesNotThrow(
                () ->
                    apiMapper.readValue(
                        "{\"id\":1,\"amountMode\":\"explicit\"}",
                        ApiInventoryOperationOriginUpdate.class))
            .getAmountMode());
    // An unrecognised mode binds to UNKNOWN, which the validator rejects with a catalog key, rather
    // than throwing out of the @JsonCreator: that throw became an HttpMessageNotReadableException
    // whose raw English message the shared advice copied into the 400 body, echoing the client's
    // own input back untranslated (parallel review). Still a 400 either way, and crucially still
    // NOT a silent fallthrough to EXPLICIT, which would turn a client's "all" typo into a partial
    // take.
    assertEquals(
        ApiInventoryOperationAmountMode.UNKNOWN,
        assertDoesNotThrow(
                () ->
                    apiMapper.readValue(
                        "{\"id\":1,\"amountMode\":\"everything\"}",
                        ApiInventoryOperationOriginUpdate.class))
            .getAmountMode());
  }

  @Test
  void serialisesAmountModeAsItsLowercaseWireValue() throws Exception {
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(1L);
    origin.setAmountMode(ApiInventoryOperationAmountMode.ALL);
    assertTrue(apiMapper.writeValueAsString(origin).contains("\"amountMode\":\"all\""));
  }

  @Test
  void extraFieldsSerialiseTheirOperationFieldKeyButNeverBindIt() {
    // Serialised, because the next run of an operation reads the parent's fields over GET and needs
    // the key to identify the previous generation (RSDEV-1231, F6).
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    field.setName("Passage number");
    field.setOperationFieldKey("operations.passage.numberField");
    String json = assertDoesNotThrow(() -> apiMapper.writeValueAsString(field));
    assertTrue(
        json.contains("\"operationFieldKey\":\"operations.passage.numberField\""),
        () -> "key missing from the response body: " + json);

    // Never bound, on any endpoint: the key is READ_ONLY, so only the server's request builder can
    // put one on a field, and a client echoing a GET body back is not rejected for carrying it.
    // Under the strict bare mapper too, which would throw for a genuinely unknown property.
    for (ObjectMapper mapper : new ObjectMapper[] {apiMapper, new ObjectMapper()}) {
      ApiExtraField bound =
          assertDoesNotThrow(
              () ->
                  mapper.readValue(
                      "{\"name\":\"f\",\"operationFieldKey\":\"operations.passage.numberField\"}",
                      ApiExtraField.class));
      assertNull(bound.getOperationFieldKey(), "a request must not be able to set the key");
      assertEquals("f", bound.getName());
    }
    assertThrows(
        UnrecognizedPropertyException.class,
        () -> new ObjectMapper().readValue("{\"name\":\"f\",\"bogus\":1}", ApiExtraField.class),
        "control: the strict mapper does reject a property that is genuinely unknown");
  }
}
