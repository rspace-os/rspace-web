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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class FeatureFlagPropertiesLoaderTest {

  private static final String FLAG = "testFlag";

  private static final Set<String> KNOWN = Set.of(FLAG);

  private FeatureFlagPropertiesLoader loaderReading(String location) {
    return new FeatureFlagPropertiesLoader(new DefaultResourceLoader(), location);
  }

  @Test
  void missingExplicitFileFailsStartup() {
    FeatureFlagPropertiesLoader loader =
        loaderReading("classpath:deployments/does-not-exist.properties");

    assertThrows(IllegalStateException.class, () -> loader.loadForcedValues(KNOWN));
  }

  @Test
  void missingDefaultFileHasNoForcedValues() {
    ResourceLoader resources =
        new DefaultResourceLoader() {
          @Override
          public Resource getResource(String location) {
            assertEquals("classpath:deployments/feature-flags.properties", location);
            return super.getResource("classpath:deployments/does-not-exist.properties");
          }
        };
    FeatureFlagPropertiesLoader loader = new FeatureFlagPropertiesLoader(resources, "");
    assertTrue(loader.loadForcedValues(KNOWN).isEmpty());
  }

  @Test
  void readsBooleanValuesFromTheDedicatedFile(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("feature-flags.properties");
    Files.writeString(file, FLAG + "= true \n");
    FeatureFlagPropertiesLoader loader = loaderReading(file.toString());

    assertEquals(Map.of(FLAG, true), loader.loadForcedValues(KNOWN));

    Files.writeString(file, FLAG + "= false \n");
    assertEquals(Map.of(FLAG, false), loader.loadForcedValues(KNOWN));
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
    Files.writeString(file, FLAG + "=yes\n");
    FeatureFlagPropertiesLoader loader = loaderReading(file.toString());

    assertThrows(IllegalStateException.class, () -> loader.loadForcedValues(KNOWN));
  }
}
