package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Binding rules for the operations request body (F7).
 *
 * <p>Every test here reads through a mapper built by {@link Jackson2ObjectMapperBuilder}, the same
 * builder the API's message converter is built from, rather than a bare {@code new ObjectMapper()}.
 * That distinction is the entire point: the builder turns FAIL_ON_UNKNOWN_PROPERTIES OFF, so a bare
 * mapper would pass these tests whether or not the DTOs carry the annotation, and the defect would
 * still be live in production.
 */
class ApiInventoryOperationPostBindingTest {

  /** The mapper the API's own converter is built from, unknown-property feature and all. */
  private final ObjectMapper apiMapper = Jackson2ObjectMapperBuilder.json().build();

  @Test
  void theApiMapperItselfWouldOtherwiseIgnoreUnknownProperties() {
    // Pins the premise. If a future Spring or config change makes the shared mapper strict, this
    // fails and the per-class annotations below become redundant rather than silently load-bearing.
    assertDoesNotThrow(
        () -> apiMapper.readValue("{\"nope\":1}", ApiSampleWithFullSubSamples.class),
        "a shared DTO still ignores unknown properties; only the operations DTOs are strict");
  }

  @Test
  void rejectsAnUnknownPropertyOnTheRequest() {
    // A mistyped property is a caller believing the request does something it does not. Silently
    // dropping it performs a different operation than the one asked for, with no indication why.
    assertTrue(
        assertThrows(
                Exception.class,
                () ->
                    apiMapper.readValue(
                        "{\"operationType\":\"aliquot\",\"origins\":[],\"newSamples\":null}",
                        ApiInventoryOperationPost.class))
            .getMessage()
            .contains("newSamples"));
  }

  @Test
  void rejectsAnUnknownPropertyOnAnOrigin() {
    assertTrue(
        assertThrows(
                Exception.class,
                () ->
                    apiMapper.readValue(
                        "{\"operationType\":\"aliquot\",\"origins\":[{\"id\":1,\"amountTakenn\":null}]}",
                        ApiInventoryOperationPost.class))
            .getMessage()
            .contains("amountTakenn"));
  }

  @Test
  void acceptsEveryPropertyTheEndpointDeclares() {
    ApiInventoryOperationPost request =
        assertDoesNotThrow(
            () ->
                apiMapper.readValue(
                    "{\"operationType\":\"destroy\",\"origins\":[{\"id\":7,"
                        + "\"amountMode\":\"all\","
                        + "\"amountTaken\":{\"numericValue\":5,\"unitId\":3},"
                        + "\"extraFields\":[]}],\"newSample\":null}",
                    ApiInventoryOperationPost.class));
    assertEquals("destroy", request.getOperationType());
    ApiInventoryOperationOriginUpdate origin = request.getOrigins().get(0);
    assertEquals(Long.valueOf(7), origin.getId());
    assertEquals(ApiInventoryOperationAmountMode.ALL, origin.getAmountMode());
    assertEquals(0, new BigDecimal("5").compareTo(origin.getAmountTaken().getNumericValue()));
  }

  @Test
  void bindsAmountModeFromItsLowercaseWireValueAndRejectsAnythingElse() {
    assertEquals(
        ApiInventoryOperationAmountMode.EXPLICIT,
        assertDoesNotThrow(
                () ->
                    apiMapper.readValue(
                        "{\"id\":1,\"amountMode\":\"explicit\"}",
                        ApiInventoryOperationOriginUpdate.class))
            .getAmountMode());
    // An unrecognised mode is a binding failure (the endpoint's ordinary 400), not a silent
    // fallthrough to EXPLICIT, which would turn a client's "all" typo into a partial take.
    assertTrue(
        assertThrows(
                Exception.class,
                () ->
                    apiMapper.readValue(
                        "{\"id\":1,\"amountMode\":\"everything\"}",
                        ApiInventoryOperationOriginUpdate.class))
            .getMessage()
            .contains("everything"));
  }

  @Test
  void serialisesAmountModeAsItsLowercaseWireValue() throws Exception {
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(1L);
    origin.setAmountMode(ApiInventoryOperationAmountMode.ALL);
    assertTrue(apiMapper.writeValueAsString(origin).contains("\"amountMode\":\"all\""));
  }

  @Test
  void extraFieldsSerialiseTheirOperationFieldKeyBackToTheClient() {
    // It was WRITE_ONLY, which is what made the Passage counter fall back to matching on a
    // localized
    // NAME: the next run reads the parent's fields over GET, so a key that never comes back cannot
    // identify the previous generation (RSDEV-1231, F6).
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    field.setName("Passage number");
    field.setOperationFieldKey("operations.passage.numberField");

    String json = assertDoesNotThrow(() -> apiMapper.writeValueAsString(field));
    assertTrue(
        json.contains("\"operationFieldKey\":\"operations.passage.numberField\""),
        () -> "key missing from the response body: " + json);

    // And still binds from a request, so the operations endpoint can set it.
    assertEquals(
        "operations.passage.numberField",
        assertDoesNotThrow(
                () ->
                    apiMapper.readValue(
                        "{\"operationFieldKey\":\"operations.passage.numberField\"}",
                        ApiExtraField.class))
            .getOperationFieldKey());
  }
}
