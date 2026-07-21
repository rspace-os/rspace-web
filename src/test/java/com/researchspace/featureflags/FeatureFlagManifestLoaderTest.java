package com.researchspace.featureflags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

class FeatureFlagManifestLoaderTest {

  @Test
  void loadsValidDefinitionsInManifestOrder() {
    FeatureFlagManifestLoader loader =
        loaderFor(
            """
            {"flags":[
              {"name":"firstFlag","default":true},
              {"name":"secondFlag","default":false}
            ]}
            """);

    assertEquals(
        List.of(
            new FeatureFlagDefinition("firstFlag", true),
            new FeatureFlagDefinition("secondFlag", false)),
        loader.loadDefinitions());
  }

  @Test
  void loadsJsoncAndDefaultsMissingValuesToFalse() {
    FeatureFlagManifestLoader loader =
        loaderFor(
            """
            {
              // Defaults are optional.
              "flags": [
                {"name":"firstFlag","default":true},
                {"name":"secondFlag"},
              ],
            }
            """);

    assertEquals(
        List.of(
            new FeatureFlagDefinition("firstFlag", true),
            new FeatureFlagDefinition("secondFlag", false)),
        loader.loadDefinitions());
  }

  @Test
  void rejectsNonBooleanDefaults() {
    FeatureFlagManifestLoader loader =
        loaderFor("{\"flags\":[{\"name\":\"someFlag\",\"default\":\"false\"}]}");

    assertThrows(IllegalStateException.class, loader::loadDefinitions);
  }

  @Test
  void rejectsDuplicateNames() {
    FeatureFlagManifestLoader loader =
        loaderFor(
            """
            {"flags":[
              {"name":"someFlag","default":true},
              {"name":"someFlag","default":false}
            ]}
            """);

    assertThrows(IllegalStateException.class, loader::loadDefinitions);
  }

  @Test
  void acceptsNameAtMaximumLength() {
    String name = "a".repeat(FeatureFlagDefinition.MAX_NAME_LENGTH);

    assertEquals(
        List.of(new FeatureFlagDefinition(name, false)),
        loaderFor("{\"flags\":[{\"name\":\"" + name + "\"}]}").loadDefinitions());
  }

  @Test
  void rejectsNameOverMaximumLength() {
    String name = "a".repeat(FeatureFlagDefinition.MAX_NAME_LENGTH + 1);

    assertThrows(
        IllegalStateException.class,
        loaderFor("{\"flags\":[{\"name\":\"" + name + "\"}]}")::loadDefinitions);
  }

  private FeatureFlagManifestLoader loaderFor(String manifest) {
    ResourceLoader resourceLoader = mock(ResourceLoader.class);
    when(resourceLoader.getResource("classpath:feature-flags/feature-flags.jsonc"))
        .thenReturn(new ByteArrayResource(manifest.getBytes(StandardCharsets.UTF_8)));
    return new FeatureFlagManifestLoader(resourceLoader);
  }
}
