package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ServerMessageCatalogueTest {

  private static final Path LOCALE_DIRECTORY =
      Path.of("src/main/webapp/ui/src/modules/common/i18n/locales/en-US");
  private static final Pattern LOWER_CAMEL_CASE_KEY_COMPONENT =
      Pattern.compile("[a-z][A-Za-z0-9]*");
  private static final Pattern LOWERCASE_FIELD_SUFFIX = Pattern.compile(".+field(?:Name|Value).*");

  @Test
  void serverJsonKeysAreUniqueAndUseLowerCamelCase() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, List<String>> keyToFiles = new TreeMap<>();
    List<String> invalidKeys = new ArrayList<>();

    try (Stream<Path> files = Files.list(LOCALE_DIRECTORY)) {
      for (Path file :
          files.filter(p -> p.getFileName().toString().startsWith("server.")).toList()) {
        inspect(
            mapper.readTree(file.toFile()),
            "",
            file.getFileName().toString(),
            keyToFiles,
            invalidKeys);
      }
    }

    List<String> collisions =
        keyToFiles.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .toList();
    assertAll(
        () ->
            assertEquals(
                List.of(), collisions, "Dotted key defined in more than one server.*.json file"),
        () ->
            assertEquals(
                List.of(), invalidKeys, "Server JSON property names must use lower camel case"));
  }

  private void inspect(
      JsonNode node,
      String prefix,
      String filename,
      Map<String, List<String>> keyToFiles,
      List<String> invalidKeys) {
    if (!node.isObject()) {
      if (node.isValueNode() && !prefix.isEmpty()) {
        keyToFiles.computeIfAbsent(prefix, key -> new ArrayList<>()).add(filename);
      }
      return;
    }

    Iterator<Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Entry<String, JsonNode> field = fields.next();
      String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
      if (!LOWER_CAMEL_CASE_KEY_COMPONENT.matcher(field.getKey()).matches()
          || LOWERCASE_FIELD_SUFFIX.matcher(field.getKey()).matches()) {
        invalidKeys.add(key);
      }
      inspect(field.getValue(), key, filename, keyToFiles, invalidKeys);
    }
  }
}
