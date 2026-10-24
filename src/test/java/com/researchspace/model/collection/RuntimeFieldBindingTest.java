package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeFieldBindingTest {

  @Test
  void acceptsDottedAsciiPropertyPaths() {
    RuntimeFieldBinding binding =
        new RuntimeFieldBinding(
            Object.class, "_instrument2.id", "editInfo.description_2", Map.of("deleted", false));

    assertEquals("_instrument2.id", binding.parentIdProperty());
    assertEquals("editInfo.description_2", binding.valueProperty());
  }

  @Test
  void rejectsMalformedPropertyPathsInEveryPosition() {
    Map<String, Object> emptyMatch = Map.of();
    for (String path :
        Arrays.asList(
            null,
            "",
            " ",
            ".",
            "..",
            ".id",
            "id.",
            "a..b",
            "1id",
            "123",
            "a.1b",
            "\uff49\uff44",
            "\u00e9",
            "\u0663",
            "a-b",
            "a$b",
            "id\n")) {
      Map<String, Object> match = new HashMap<>();
      match.put(path, 1L);

      assertThrows(
          IllegalArgumentException.class,
          () -> new RuntimeFieldBinding(Object.class, path, "data", emptyMatch),
          path);
      assertThrows(
          IllegalArgumentException.class,
          () -> new RuntimeFieldBinding(Object.class, "id", path, emptyMatch),
          path);
      assertThrows(
          IllegalArgumentException.class,
          () -> new RuntimeFieldBinding(Object.class, "id", "data", match),
          path);
    }
  }

  @Test
  void rejectsNullMatchValues() {
    Map<String, Object> match = new HashMap<>();
    match.put("deleted", null);

    assertThrows(
        NullPointerException.class,
        () -> new RuntimeFieldBinding(Object.class, "id", "data", match));
  }
}
