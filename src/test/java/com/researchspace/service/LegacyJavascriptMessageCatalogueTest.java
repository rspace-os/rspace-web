package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LegacyJavascriptMessageCatalogueTest {

  private static final Path CATALOGUE =
      Path.of("src/main/webapp/ui/src/modules/common/i18n/locales/en-US/server.legacyJs.json");
  private static final Path SCRIPTS_ROOT = Path.of("src/main/webapp/scripts");
  private static final Pattern MESSAGE_KEY =
      Pattern.compile("(?:RS|tinymceDialogUtils)\\.msg\\(\\s*[\"'](legacyjs\\.[^\"']+)[\"']");
  private static final Pattern JAVA_CHOICE_FORMAT =
      Pattern.compile("\\{\\s*[^,{}]+\\s*,\\s*choice\\s*,");
  private static final Pattern ICU_ARGUMENT_TYPE =
      Pattern.compile("\\{\\s*[^,{}]+\\s*,\\s*([A-Za-z]+)\\s*(?:,|\\})");
  private static final Set<String> SUPPORTED_ICU_ARGUMENT_TYPES =
      Set.of("date", "number", "plural", "select", "selectordinal", "time");
  private static final Pattern UNDOUBLED_ICU_QUOTE_BEFORE_ARGUMENT =
      Pattern.compile("(^|[^'])'\\{");

  @Test
  void allMessageKeysUsedByScriptsAreDefined() throws IOException {
    Map<String, String> messages = messages();
    Set<String> referencedKeys = referencedKeys();
    Set<String> missingKeys = new TreeSet<>(referencedKeys);
    missingKeys.removeAll(messages.keySet());

    assertFalse(referencedKeys.isEmpty());
    assertEquals(Set.of(), missingKeys);
  }

  @Test
  void messagesUseSupportedIcuSyntax() throws IOException {
    Set<String> javaChoiceFormatKeys = new TreeSet<>();
    Set<String> unsupportedArgumentTypeKeys = new TreeSet<>();
    Set<String> unescapedQuoteKeys = new TreeSet<>();

    messages()
        .forEach(
            (key, message) -> {
              if (JAVA_CHOICE_FORMAT.matcher(message).find()) {
                javaChoiceFormatKeys.add(key);
              }
              ICU_ARGUMENT_TYPE
                  .matcher(message)
                  .results()
                  .map(match -> match.group(1))
                  .filter(type -> !SUPPORTED_ICU_ARGUMENT_TYPES.contains(type))
                  .findAny()
                  .ifPresent(type -> unsupportedArgumentTypeKeys.add(key + " (" + type + ")"));
              if (UNDOUBLED_ICU_QUOTE_BEFORE_ARGUMENT.matcher(message).find()) {
                unescapedQuoteKeys.add(key);
              }
            });

    assertAll(
        () ->
            assertEquals(Set.of(), javaChoiceFormatKeys, "Messages must not use Java ChoiceFormat"),
        () ->
            assertEquals(
                Set.of(), unsupportedArgumentTypeKeys, "Messages use unsupported argument types"),
        () ->
            assertEquals(
                Set.of(),
                unescapedQuoteKeys,
                "Visible quotes before ICU arguments must be escaped"));
  }

  private Map<String, String> messages() throws IOException {
    Map<String, String> messages = new TreeMap<>();
    flatten("", new ObjectMapper().readTree(CATALOGUE.toFile()), messages);
    return messages;
  }

  private void flatten(String prefix, JsonNode node, Map<String, String> target) {
    if (node.isObject()) {
      Iterator<Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Entry<String, JsonNode> field = fields.next();
        String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
        flatten(key, field.getValue(), target);
      }
    } else if (node.isValueNode() && !prefix.isEmpty()) {
      target.put(prefix, node.asText());
    }
  }

  private Set<String> referencedKeys() throws IOException {
    Set<String> keys = new TreeSet<>();
    try (Stream<Path> scripts = Files.walk(SCRIPTS_ROOT)) {
      for (Path script : scripts.filter(this::isAuthoredJavascriptFile).toList()) {
        String source = Files.readString(script, StandardCharsets.UTF_8);
        MESSAGE_KEY.matcher(source).results().forEach(match -> keys.add(match.group(1)));
      }
    }
    return keys;
  }

  private boolean isAuthoredJavascriptFile(Path path) {
    String unixPath = path.toString().replace('\\', '/');
    return Files.isRegularFile(path)
        && unixPath.endsWith(".js")
        && !unixPath.contains("/bower_components/")
        && !unixPath.contains("/handsontable-ruleJS/")
        && !unixPath.contains("/tinymce/");
  }
}
