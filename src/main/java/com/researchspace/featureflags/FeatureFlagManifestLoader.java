package com.researchspace.featureflags;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FeatureFlagManifestLoader {

  private static final String MANIFEST = "classpath:feature-flags/feature-flags.jsonc";

  private final ObjectMapper objectMapper =
      JsonMapper.builder()
          .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
          .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
          .build();
  private final ResourceLoader resourceLoader;

  public FeatureFlagManifestLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  public List<FeatureFlagDefinition> loadDefinitions() {
    try (InputStream inputStream = resourceLoader.getResource(MANIFEST).getInputStream()) {
      JsonNode flags = objectMapper.readTree(inputStream).path("flags");
      if (!flags.isArray()) {
        throw new IllegalStateException("Feature flag manifest must contain a flags array");
      }
      Map<String, FeatureFlagDefinition> definitions = new LinkedHashMap<>();
      for (JsonNode flag : flags) {
        FeatureFlagDefinition definition = parseDefinition(flag);
        if (definitions.putIfAbsent(definition.name(), definition) != null) {
          throw new IllegalStateException(
              "Feature flag manifest contains duplicate flag " + definition.name());
        }
      }
      return List.copyOf(definitions.values());
    } catch (IOException e) {
      log.error("Unable to read feature flag manifest", e);
      throw new IllegalStateException("Unable to read feature flag manifest", e);
    }
  }

  private static FeatureFlagDefinition parseDefinition(JsonNode flag) {
    JsonNode nameNode = flag.path("name");
    if (!flag.isObject() || !nameNode.isTextual()) {
      throw new IllegalStateException("Feature flag manifest contains an unnamed flag");
    }
    String name = nameNode.textValue();
    if (name.length() > FeatureFlagDefinition.MAX_NAME_LENGTH
        || !name.matches("^[a-z][a-zA-Z0-9]*$")) {
      throw new IllegalStateException(
          "Feature flag manifest contains an invalid flag name: " + name);
    }
    requireNonBlankText(flag, "description", name);
    requireNonBlankText(flag, "owner", name);
    String expires = requireNonBlankText(flag, "expires", name);
    try {
      LocalDate.parse(expires);
    } catch (DateTimeParseException e) {
      throw new IllegalStateException(
          "Feature flag manifest contains an invalid expires date for " + name, e);
    }
    JsonNode defaultNode = flag.path("default");
    if (!defaultNode.isMissingNode() && !defaultNode.isBoolean()) {
      throw new IllegalStateException(
          "Feature flag manifest contains an invalid default for " + name);
    }
    return new FeatureFlagDefinition(name, defaultNode.asBoolean(false));
  }

  private static String requireNonBlankText(JsonNode flag, String field, String flagName) {
    JsonNode value = flag.path(field);
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalStateException(
          "Feature flag manifest contains an invalid " + field + " for " + flagName);
    }
    return value.textValue();
  }
}
