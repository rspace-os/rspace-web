package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiV2OpenApiComponentsTest {

  @Test
  void aNameHoldsOneValueAndAppearsOnlyOnceReferenced() {
    ApiV2OpenApiComponents components = new ApiV2OpenApiComponents();
    Map<String, Object> page = Map.of("name", "page");

    assertEquals(
        Map.of("$ref", "#/components/parameters/Page"),
        components.reference("parameters", "Page", page));
    components.reference("parameters", "Page", Map.of("name", "page"));
    assertThrows(
        IllegalArgumentException.class,
        () -> components.reference("parameters", "Page", Map.of("name", "other")));
    assertEquals(
        Map.of("name", "other"),
        components.referenceOrInline("parameters", "Page", Map.of("name", "other")));

    Map<String, Object> document = new LinkedHashMap<>();
    document.put("responses", new LinkedHashMap<>(Map.of("NotFound", Map.of())));
    components.addTo(document);
    assertEquals(Map.of("Page", page), document.get("parameters"));
    assertEquals(
        List.of("NotFound"), List.copyOf(((Map<?, ?>) document.get("responses")).keySet()));
  }

  @Test
  void aComponentCannotReplaceOneTheDocumentAlreadyHas() {
    ApiV2OpenApiComponents components = new ApiV2OpenApiComponents();
    components.reference("responses", "NotFound", Map.of("description", "missing"));
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("responses", new LinkedHashMap<>(Map.of("NotFound", Map.of())));

    assertThrows(IllegalArgumentException.class, () -> components.addTo(document));
  }
}
