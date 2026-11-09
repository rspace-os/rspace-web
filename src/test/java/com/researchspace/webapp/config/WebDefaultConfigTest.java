package com.researchspace.webapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.researchspace.api.v2.json.ApiV2JsonBody;
import com.researchspace.api.v2.json.ApiV2JsonMappers;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import com.researchspace.model.collection.DocumentValidationException.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

class WebDefaultConfigTest {

  @Test
  void webConfigCollectsFeatureBodyTypeBeans() {
    try (var context =
        new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
      context.registerBean(
          com.researchspace.api.v2.json.ApiV2JsonBodyTypes.class,
          () -> new com.researchspace.api.v2.json.ApiV2JsonBodyTypes(List.of(Detail.class)));
      context.registerBean(
          "otherBodyTypes",
          com.researchspace.api.v2.json.ApiV2JsonBodyTypes.class,
          () -> new com.researchspace.api.v2.json.ApiV2JsonBodyTypes(List.of(StrictFields.class)));
      context.refresh();
      WebConfig config = new WebConfig();
      config.setJsonBodyTypes(
          List.copyOf(
              context
                  .getBeansOfType(com.researchspace.api.v2.json.ApiV2JsonBodyTypes.class)
                  .values()));
      config.defaultConfig = new WebDefaultConfig();
      config.objectMapper = new WebDefaultConfig().objectMapper();
      var converter = new MappingJackson2HttpMessageConverter();
      config.extendMessageConverters(new java.util.ArrayList<>(List.of(converter)));
      assertTrue(ApiV2JsonMappers.isStrictBody(converter, Detail.class));
      assertTrue(ApiV2JsonMappers.isStrictBody(converter, StrictFields.class));
    }
  }

  @Test
  void boundsJsonDocumentsBeforeTreeMaterializationCanGrowWithoutLimit() {
    ObjectMapper mapper = new WebDefaultConfig().objectMapper();
    StreamReadConstraints constraints = mapper.getFactory().streamReadConstraints();

    assertEquals(WebDefaultConfig.MAX_JSON_DOCUMENT_LENGTH, constraints.getMaxDocumentLength());
    assertEquals(WebDefaultConfig.MAX_JSON_STRING_LENGTH, constraints.getMaxStringLength());
    assertEquals(WebDefaultConfig.MAX_JSON_TOKEN_COUNT, constraints.getMaxTokenCount());
    assertEquals(WebDefaultConfig.MAX_JSON_NESTING_DEPTH, constraints.getMaxNestingDepth());
  }

  @Test
  void limitedMapperUsesTheBeanConstraintsWithoutChangingTheBaseFactory() {
    ObjectMapper base = new ObjectMapper();
    long baseDocumentLength = base.getFactory().streamReadConstraints().getMaxDocumentLength();
    ObjectMapper limited = ApiV2JsonMappers.limited(base);

    assertNotSame(base.getFactory(), limited.getFactory());
    assertEquals(
        baseDocumentLength, base.getFactory().streamReadConstraints().getMaxDocumentLength());
    assertEquals(
        WebDefaultConfig.streamReadConstraints().getMaxDocumentLength(),
        limited.getFactory().streamReadConstraints().getMaxDocumentLength());
    assertEquals(
        WebDefaultConfig.streamReadConstraints().getMaxStringLength(),
        limited.getFactory().streamReadConstraints().getMaxStringLength());
    assertEquals(
        WebDefaultConfig.streamReadConstraints().getMaxTokenCount(),
        limited.getFactory().streamReadConstraints().getMaxTokenCount());
    assertEquals(
        WebDefaultConfig.streamReadConstraints().getMaxNestingDepth(),
        limited.getFactory().streamReadConstraints().getMaxNestingDepth());
  }

  @Test
  void strictMapperCopiesTheBaseAndRejectsScalarCoercions() throws Exception {
    ObjectMapper base =
        new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    ObjectMapper strict = ApiV2JsonMappers.strict(base);

    assertNotSame(base, strict);
    assertNotSame(base.getFactory(), strict.getFactory());
    assertTrue(strict.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    assertFalse(base.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

    String valid = "{\"text\":\"x\",\"count\":15,\"ratio\":1.5,\"enabled\":true}";
    strict.readValue(valid, StrictFields.class);
    base.readValue("{\"text\":1,\"count\":15,\"ratio\":1.5,\"enabled\":true}", StrictFields.class);
    for (String json :
        List.of(
            "{\"text\":1,\"count\":15,\"ratio\":1.5,\"enabled\":true}",
            "{\"text\":1.5,\"count\":15,\"ratio\":1.5,\"enabled\":true}",
            "{\"text\":true,\"count\":15,\"ratio\":1.5,\"enabled\":true}",
            "{\"text\":\"x\",\"count\":15,\"ratio\":1.5,\"enabled\":1}",
            "{\"text\":\"x\",\"count\":15,\"ratio\":1.5,\"enabled\":0}",
            "{\"text\":\"x\",\"count\":15,\"ratio\":1.5,\"enabled\":1.5}",
            "{\"text\":\"x\",\"count\":\"15\",\"ratio\":1.5,\"enabled\":true}",
            "{\"text\":\"x\",\"count\":\"\",\"ratio\":1.5,\"enabled\":true}",
            "{\"text\":\"x\",\"count\":15.0,\"ratio\":1.5,\"enabled\":true}",
            "{\"text\":\"x\",\"count\":15,\"ratio\":\"1.5\",\"enabled\":true}",
            "{\"text\":\"x\",\"count\":15,\"ratio\":\"\",\"enabled\":true}",
            "{\"text\":\"x\",\"count\":15,\"ratio\":1.5,\"enabled\":\"true\"}",
            "{\"text\":\"x\",\"count\":15,\"ratio\":1.5,\"enabled\":\"\"}")) {
      assertThrows(
          MismatchedInputException.class, () -> strict.readValue(json, StrictFields.class), json);
    }
    assertThrows(
        MismatchedInputException.class, () -> strict.readValue("{\"value\":1}", EnumField.class));
  }

  @Test
  void registersOnlyTheStrictTypesSuppliedForEachConverter() {
    MappingJackson2HttpMessageConverter registered = new MappingJackson2HttpMessageConverter();
    MappingJackson2HttpMessageConverter other = new MappingJackson2HttpMessageConverter();
    ApiV2JsonMappers.register(registered, Detail.class);
    ApiV2JsonMappers.register(other);

    assertTrue(ApiV2JsonMappers.isStrictBody(registered, Detail.class));
    assertFalse(ApiV2JsonMappers.isStrictBody(other, Detail.class));
    assertFalse(ApiV2JsonMappers.isStrictBody(registered, ApiV2JsonBody.class));
    assertTrue(registered.getObjectMappersForType(JsonNode.class).isEmpty());
  }

  @Test
  void mapsCreatorAndUnknownPropertyReasonsFromStrictBinding() throws Exception {
    ObjectMapper strict = ApiV2JsonMappers.strict(new ObjectMapper());

    assertViolation(strict, "{}", new Violation("name", Reason.REQUIRED), Detail.class);
    assertViolation(strict, "{}", new Violation("name", Reason.REQUIRED), RequiredDetail.class);
    assertViolation(
        strict, "{\"name\":null}", new Violation("name", Reason.NULL_NOT_ALLOWED), Detail.class);
    assertViolation(
        strict,
        "{\"name\":\"value\",\"extra\":true}",
        new Violation("extra", Reason.UNKNOWN_FIELD),
        Detail.class);
  }

  @Test
  void mapsJacksonFailuresToNestedFieldViolations() throws Exception {
    ObjectMapper strict = ApiV2JsonMappers.strict(new ObjectMapper());

    assertViolation(
        strict,
        "{\"items\":[{\"kind\":\"a\",\"detail\":{\"name\":\"b\",\"extra\":1}}]}",
        new Violation("items[0].detail.extra", Reason.UNKNOWN_FIELD));
    assertViolation(
        strict,
        "{\"items\":[{\"kind\":\"a\",\"detail\":{}}]}",
        new Violation("items[0].detail.name", Reason.REQUIRED));
    assertViolation(
        strict,
        "{\"items\":[{\"kind\":\"a\",\"detail\":{\"name\":null}}]}",
        new Violation("items[0].detail.name", Reason.NULL_NOT_ALLOWED));
    assertViolation(
        strict,
        "{\"items\":[{\"kind\":\"a\",\"detail\":{\"name\":1}}]}",
        new Violation("items[0].detail.name", Reason.WRONG_TYPE));
    assertViolation(
        strict,
        "{\"items\":[null]}",
        new Violation("items[0]", Reason.NULL_NOT_ALLOWED),
        NullableEnvelope.class);
  }

  private static void assertViolation(ObjectMapper mapper, String json, Violation expected)
      throws Exception {
    assertViolation(mapper, json, expected, Envelope.class);
  }

  private static void assertViolation(
      ObjectMapper mapper, String json, Violation expected, Class<?> bodyType) throws Exception {
    MismatchedInputException failure =
        assertThrows(MismatchedInputException.class, () -> mapper.readValue(json, bodyType));
    DocumentValidationException validation = ApiV2JsonMappers.validationException(failure);
    assertEquals("errors.api.v2.invalidRequest", validation.getErrorKey());
    assertEquals(List.of(expected), validation.getViolations());
  }

  private record StrictFields(String text, Integer count, Float ratio, Boolean enabled) {}

  private record EnumField(Mode value) {}

  private enum Mode {
    FIRST,
    SECOND
  }

  private record Envelope(List<Item> items) {}

  private record NullableEnvelope(@JsonSetter(contentNulls = Nulls.FAIL) List<Item> items) {}

  private record Item(String kind, Detail detail) {}

  private record Detail(String name) {}

  private record RequiredDetail(@JsonProperty(required = true) String name) {}
}
