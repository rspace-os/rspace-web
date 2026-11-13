package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class ApiV2ProblemCatalogTest {
  private static final Pattern ARGUMENT = Pattern.compile("\\{\\d+[,}]");
  // OpenAPI document text is not a problem message, so it has no title sibling.
  private static final String OPENAPI_TEXT = "api.v2.openapi.";

  @Test
  void everyParameterizedApiMessageHasAStableSiblingTitle() throws Exception {
    var resources =
        new PathMatchingResourcePatternResolver()
            .getResources("classpath*:i18n/locales/**/server.*.json");
    assertTrue(resources.length > 0);
    for (var resource : resources) {
      try (InputStream input = resource.getInputStream()) {
        check(new ObjectMapper().readTree(input), "");
      }
    }
  }

  private void check(JsonNode node, String prefix) {
    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      var field = fields.next();
      String key = prefix + field.getKey();
      if (field.getValue().isObject()) {
        check(field.getValue(), key + ".");
      } else if ((key.startsWith("api.v2.") || key.startsWith("errors.api.v2."))
          && !key.startsWith(OPENAPI_TEXT)
          && ARGUMENT.matcher(field.getValue().asText()).find()) {
        JsonNode title = node.get(field.getKey() + "Title");
        assertNotNull(title, key);
        assertTrue(title.isTextual(), key);
        assertFalse(title.asText().isBlank(), key);
        assertFalse(ARGUMENT.matcher(title.asText()).find(), key);
      }
    }
  }
}
