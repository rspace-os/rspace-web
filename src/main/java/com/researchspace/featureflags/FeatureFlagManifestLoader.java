package com.researchspace.featureflags;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
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
    Resource resource = resourceLoader.getResource(MANIFEST);
    try (InputStream inputStream = resource.getInputStream()) {
      JsonNode flags = objectMapper.readTree(inputStream).path("flags");
      if (!flags.isArray()) {
        throw new IllegalStateException("Feature flag manifest must contain a flags array");
      }
      List<FeatureFlagDefinition> definitions = new ArrayList<>();
      Set<String> names = new HashSet<>();
      for (JsonNode flag : flags) {
        JsonNode nameNode = flag.get("name");
        if (!flag.isObject() || nameNode == null || !nameNode.isTextual()) {
          throw new IllegalStateException("Feature flag manifest contains an unnamed flag");
        }
        String name = nameNode.textValue();
        if (name.length() > FeatureFlagDefinition.MAX_NAME_LENGTH
            || !name.matches("^[a-z][a-zA-Z0-9]*$")) {
          throw new IllegalStateException(
              "Feature flag manifest contains an invalid flag name: " + name);
        }
        if (!names.add(name)) {
          throw new IllegalStateException("Feature flag manifest contains duplicate flag " + name);
        }
        JsonNode defaultNode = flag.get("default");
        if (defaultNode != null && !defaultNode.isBoolean()) {
          throw new IllegalStateException(
              "Feature flag manifest contains an invalid default for " + name);
        }
        definitions.add(
            new FeatureFlagDefinition(name, defaultNode != null && defaultNode.booleanValue()));
      }
      return definitions;
    } catch (IOException e) {
      log.error("Unable to read feature flag manifest", e);
      throw new IllegalStateException("Unable to read feature flag manifest", e);
    }
  }
}
