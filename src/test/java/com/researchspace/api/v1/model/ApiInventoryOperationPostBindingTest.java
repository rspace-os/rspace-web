package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Binding rules for the operations request body (F7).
 *
 * <p>Every test here reads through a mapper built by {@link Jackson2ObjectMapperBuilder}, the same
 * builder the API's message converter is built from, rather than a bare {@code new ObjectMapper()}.
 * That distinction is the entire point: the builder turns FAIL_ON_UNKNOWN_PROPERTIES OFF, so a bare
 * mapper would pass these tests whether or not the capture works, and the defect would still be
 * live in production.
 */
class ApiInventoryOperationPostBindingTest {

  /** The mapper the API's own converter is built from, unknown-property feature and all. */
  private final ObjectMapper apiMapper = Jackson2ObjectMapperBuilder.json().build();

  @Test
  void theApiMapperItselfWouldOtherwiseIgnoreUnknownProperties() {
    // Pins the premise the capture exists for. If a future Spring or config change makes the shared
    // mapper strict, this fails and the capture becomes redundant rather than silently
    // load-bearing.
    assertDoesNotThrow(
        () -> apiMapper.readValue("{\"nope\":1}", ApiSampleWithFullSubSamples.class),
        "binding must not throw on an unknown property; the endpoint's validator reports it");
  }

  @Test
  void capturesAnUnknownPropertyOnTheRequestRatherThanDroppingIt() {
    // A mistyped property is a caller believing the request does something it does not. Dropped
    // silently, it performs a different operation than the one asked for with no indication why.
    ApiInventoryOperationPost request =
        assertDoesNotThrow(
            () ->
                apiMapper.readValue(
                    "{\"operationType\":\"aliquot\",\"origins\":[],\"newSamples\":null}",
                    ApiInventoryOperationPost.class));
    assertEquals(List.of("newSamples"), request.getUnknownProperties());
  }

  @Test
  void capturesAnUnknownPropertyAtEveryLevelOfThePayload() {
    // The motivating case is newSample.storageTemperature, a typo for storageTempMin/Max:
    // strictness
    // on the two operation-owned DTOs alone could never see it, because the sample DTO is shared.
    ApiInventoryOperationPost request =
        assertDoesNotThrow(
            () ->
                apiMapper.readValue(
                    "{\"operationType\":\"aliquot\",\"rootTypo\":1,"
                        + "\"origins\":[{\"id\":1,\"originTypo\":2}],"
                        + "\"newSample\":{\"name\":\"s\",\"storageTemperature\":3,"
                        + "\"subSamples\":[{\"subTypo\":4}],"
                        + "\"extraFields\":[{\"name\":\"f\",\"fieldTypo\":5}]}}",
                    ApiInventoryOperationPost.class));

    assertEquals(List.of("rootTypo"), request.getUnknownProperties());
    assertEquals(List.of("originTypo"), request.getOrigins().get(0).getUnknownProperties());
    assertEquals(List.of("storageTemperature"), request.getNewSample().getUnknownProperties());
    assertEquals(
        List.of("subTypo"), request.getNewSample().getSubSamples().get(0).getUnknownProperties());
    assertEquals(
        List.of("fieldTypo"),
        request.getNewSample().getExtraFields().get(0).getUnknownProperties());
  }

  @Test
  void capturesNothingFromAResponseBodySentStraightBack() {
    // The guarantee read-modify-write depends on: a client that GETs a record and sends it back
    // carries every property the RESPONSE held, and none of those may be reported as unknown.
    // Rejecting a value the API itself returned is the regression the earlier
    // endpoint-enumeration attempt caused (parallel review, I2).
    ApiExtraField field =
        assertDoesNotThrow(
            () ->
                apiMapper.readValue(
                    "{\"id\":3,\"globalId\":\"IF3\",\"name\":\"Passage number\","
                        + "\"type\":\"text\",\"content\":\"4\",\"link\":null,"
                        + "\"lastModified\":null,\"modifiedBy\":null,"
                        + "\"parentGlobalId\":\"SS1\","
                        + "\"operationFieldKey\":\"operations.passage.numberField\","
                        + "\"_links\":[]}",
                    ApiExtraField.class));
    assertEquals(
        List.of(),
        field.getUnknownProperties(),
        () -> "a property the API returns must never be reported as unknown");
  }

  @Test
  void reportsAPropertyTheDtoHidesFromTheApiAsUnknown() {
    // Jackson routes a property declared but marked @JsonIgnore to the any-setter, exactly as it
    // routes an invented one, so the capture cannot tell them apart. That is acceptable BECAUSE a
    // @JsonIgnore property is absent from every response too, so no client echoing a GET can send
    // one: reaching this case means the caller invented the key. Pinned rather than worked around,
    // since the alternative is telling a caller who tried to set the F6 verification flag directly
    // that it was accepted.
    ApiExtraField field =
        assertDoesNotThrow(
            () ->
                apiMapper.readValue(
                    "{\"name\":\"f\",\"operationFieldKeyVerified\":true}", ApiExtraField.class));
    assertEquals(List.of("operationFieldKeyVerified"), field.getUnknownProperties());
    assertTrue(
        !field.isOperationFieldKeyVerified(),
        "the value must be discarded, not bound: only the validator may set this flag");
  }

  @Test
  void capturesTheNameOnlyAndLeavesBoundValuesUntouched() {
    // The value is discarded, so nothing a caller invents can reach an entity through this list.
    ApiInventoryOperationOriginUpdate origin =
        assertDoesNotThrow(
            () ->
                apiMapper.readValue(
                    "{\"id\":7,\"amountTaken\":{\"numericValue\":5,\"unitId\":3},"
                        + "\"invented\":{\"nested\":\"payload\"}}",
                    ApiInventoryOperationOriginUpdate.class));
    assertEquals(List.of("invented"), origin.getUnknownProperties());
    assertEquals(Long.valueOf(7), origin.getId());
    assertEquals(0, new BigDecimal("5").compareTo(origin.getAmountTaken().getNumericValue()));
  }

  @Test
  void stopsCapturingOnceTheCapIsReachedSoAJunkBodyCannotAmplify() {
    StringBuilder json = new StringBuilder("{\"operationType\":\"aliquot\"");
    for (int i = 0; i < UnknownPropertyCapturing.MAX_CAPTURED_UNKNOWN_PROPERTIES * 3; i++) {
      json.append(",\"junk").append(i).append("\":1");
    }
    ApiInventoryOperationPost request =
        assertDoesNotThrow(
            () ->
                apiMapper.readValue(json.append("}").toString(), ApiInventoryOperationPost.class));
    assertEquals(
        UnknownPropertyCapturing.MAX_CAPTURED_UNKNOWN_PROPERTIES,
        request.getUnknownProperties().size());
  }

  @Test
  void capturesNothingFromAConformantRequest() {
    ApiInventoryOperationPost request =
        assertDoesNotThrow(
            () ->
                apiMapper.readValue(
                    "{\"operationType\":\"destroy\",\"origins\":[{\"id\":7,"
                        + "\"amountMode\":\"all\","
                        + "\"amountTaken\":{\"numericValue\":5,\"unitId\":3},"
                        + "\"extraFields\":[]}],\"newSample\":null}",
                    ApiInventoryOperationPost.class));
    assertEquals(List.of(), request.getUnknownProperties());
    assertEquals(List.of(), request.getOrigins().get(0).getUnknownProperties());
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
  void neverSerialisesTheCaptureList() {
    // It is request-side bookkeeping. Serializing it would put it in every API response body and,
    // worse, make it bindable on the next request.
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(1L);
    String json = assertDoesNotThrow(() -> apiMapper.writeValueAsString(origin));
    assertTrue(!json.contains("unknownProperties"), () -> "capture list leaked into: " + json);
  }

  @Test
  void extraFieldsSerialiseTheirOperationFieldKeyBackToTheClient() {
    // It was WRITE_ONLY, which is what made the Passage counter fall back to matching on a
    // localized NAME: the next run reads the parent's fields over GET, so a key that never comes
    // back cannot identify the previous generation (RSDEV-1231, F6).
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
