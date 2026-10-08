package com.researchspace.featureflags;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FeatureFlagPropertiesLoader {

  private static final String DEFAULT_LOCATION = "classpath:deployments/feature-flags.properties";

  private final ResourceLoader resourceLoader;
  private final String location;

  public FeatureFlagPropertiesLoader(
      ResourceLoader resourceLoader,
      @Value("${featureFlags.properties.location:}") String location) {
    this.resourceLoader = resourceLoader;
    this.location = location == null || location.isBlank() ? DEFAULT_LOCATION : location.trim();
  }

  public Map<String, Boolean> loadForcedValues(Set<String> knownFlagNames) {
    Resource resource = resourceLoader.getResource(normaliseLocation(location));
    if (!resource.exists()) {
      return Map.of();
    }

    Properties properties = new Properties();
    try (InputStream inputStream = resource.getInputStream()) {
      properties.load(inputStream);
    } catch (IOException e) {
      log.error("Unable to read feature flag properties from {}", location, e);
      throw new IllegalStateException("Unable to read feature flag properties from " + location, e);
    }

    Map<String, Boolean> values = new LinkedHashMap<>();
    for (String name : properties.stringPropertyNames()) {
      if (!knownFlagNames.contains(name)) {
        throw new IllegalStateException(
            "Unknown feature flag '" + name + "' in properties file " + location);
      }
      values.put(
          name,
          FeatureFlagBooleanParser.parse(
              properties.getProperty(name), "Feature flag '" + name + "' in " + location));
    }
    return values;
  }

  private String normaliseLocation(String location) {
    if (location.startsWith("classpath:") || location.startsWith("file:")) {
      return location;
    }
    return "file:" + location;
  }
}
