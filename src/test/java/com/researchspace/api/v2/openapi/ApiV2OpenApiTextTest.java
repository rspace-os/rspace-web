package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.openapi.ApiV2OpenApiText.Key;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ApiV2OpenApiTextTest {
  private static final Path LOCALE_DIRECTORY =
      Path.of("src/main/webapp/ui/src/modules/common/i18n/locales/en-US");

  @Test
  void everyBuilderKeyIsInTheServerCatalogAndNoCatalogKeyIsUnused() throws IOException {
    Set<String> catalog = new TreeSet<>();
    ObjectMapper mapper = new ObjectMapper();
    try (Stream<Path> files = Files.list(LOCALE_DIRECTORY)) {
      for (Path file :
          files.filter(path -> path.getFileName().toString().startsWith("server.")).toList()) {
        collect(mapper.readTree(file.toFile()), "", catalog);
      }
    }
    Set<String> used =
        Arrays.stream(Key.values()).map(Key::code).collect(Collectors.toCollection(TreeSet::new));

    assertEquals(used, catalog);
    Arrays.stream(Key.values()).forEach(ApiV2OpenApiTestText.english()::get);
  }

  private static void collect(JsonNode node, String prefix, Set<String> keys) {
    if (!node.isObject()) {
      if (prefix.startsWith(Key.PREFIX)) {
        keys.add(prefix);
      }
      return;
    }
    node.fields()
        .forEachRemaining(
            field ->
                collect(
                    field.getValue(),
                    prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey(),
                    keys));
  }
}
