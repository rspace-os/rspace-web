package com.researchspace.featureflags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class FeatureFlagPropertiesLoaderTest {

  private static final Set<String> KNOWN = Set.of(FeatureFlags.BOOKING_ENABLED);

  private FeatureFlagPropertiesLoader loaderReading(String location) {
    return new FeatureFlagPropertiesLoader(new DefaultResourceLoader(), location);
  }

  @Test
  void missingFileHasNoForcedValues() {
    FeatureFlagPropertiesLoader loader =
        loaderReading("classpath:deployments/does-not-exist.properties");

    assertTrue(loader.loadForcedValues(KNOWN).isEmpty());
  }

  @Test
  void readsBooleanValuesFromTheDedicatedFile(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("feature-flags.properties");
    Files.writeString(file, FeatureFlags.BOOKING_ENABLED + "= true \n");
    FeatureFlagPropertiesLoader loader = loaderReading(file.toString());

    assertEquals(Map.of(FeatureFlags.BOOKING_ENABLED, true), loader.loadForcedValues(KNOWN));

    Files.writeString(file, FeatureFlags.BOOKING_ENABLED + "= false \n");
    assertEquals(Map.of(FeatureFlags.BOOKING_ENABLED, false), loader.loadForcedValues(KNOWN));
  }

  @Test
  void readsRelativeFilePath() throws IOException {
    Path file = Path.of("target", "feature-flags-relative-test.properties");
    Files.writeString(file, FeatureFlags.BOOKING_ENABLED + "=true\n");
    try {
      FeatureFlagPropertiesLoader loader = loaderReading(file.toString());

      assertEquals(Map.of(FeatureFlags.BOOKING_ENABLED, true), loader.loadForcedValues(KNOWN));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void unknownFlagInFileFailsFast(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("feature-flags.properties");
    Files.writeString(file, "unknownFlag=true\n");
    FeatureFlagPropertiesLoader loader = loaderReading(file.toString());

    assertThrows(IllegalStateException.class, () -> loader.loadForcedValues(KNOWN));
  }

  @Test
  void nonBooleanValueFailsFast(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("feature-flags.properties");
    Files.writeString(file, FeatureFlags.BOOKING_ENABLED + "=yes\n");
    FeatureFlagPropertiesLoader loader = loaderReading(file.toString());

    assertThrows(IllegalStateException.class, () -> loader.loadForcedValues(KNOWN));
  }
}
