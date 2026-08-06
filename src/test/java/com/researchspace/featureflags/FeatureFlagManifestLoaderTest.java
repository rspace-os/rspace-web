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
  void loadsJsoncAndDefaultsMissingValuesToFalse() {
    FeatureFlagManifestLoader loader =
        loaderFor(
            """
            {
              // Defaults are optional.
              "flags": [
                {"name":"firstFlag","description":"First","owner":"RSpace","expires":"2026-01-01","default":true},
                {"name":"secondFlag","description":"Second","owner":"RSpace","expires":"2026-01-02"},
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
    FeatureFlagManifestLoader loader = loaderFor(validManifestWith("\"default\":\"false\""));

    assertThrows(IllegalStateException.class, loader::loadDefinitions);
  }

  @Test
  void rejectsDuplicateNames() {
    FeatureFlagManifestLoader loader =
        loaderFor(
            """
            {"flags":[
              {"name":"someFlag","description":"First","owner":"RSpace","expires":"2026-01-01","default":true},
              {"name":"someFlag","description":"Second","owner":"RSpace","expires":"2026-01-02","default":false}
            ]}
            """);

    assertThrows(IllegalStateException.class, loader::loadDefinitions);
  }

  @Test
  void acceptsNameAtMaximumLength() {
    String name = "a".repeat(FeatureFlagDefinition.MAX_NAME_LENGTH);

    assertEquals(
        List.of(new FeatureFlagDefinition(name, false)),
        loaderFor(manifestWithName(name)).loadDefinitions());
  }

  @Test
  void rejectsNameOverMaximumLength() {
    String name = "a".repeat(FeatureFlagDefinition.MAX_NAME_LENGTH + 1);

    assertThrows(IllegalStateException.class, loaderFor(manifestWithName(name))::loadDefinitions);
  }

  @Test
  void rejectsMissingOrBlankMetadata() {
    assertThrows(
        IllegalStateException.class,
        loaderFor(
                """
                {"flags":[{"name":"someFlag","owner":"RSpace","expires":"2026-01-01"}]}
                """)
            ::loadDefinitions);
    assertThrows(
        IllegalStateException.class,
        loaderFor(manifestWithMetadata(" ", "RSpace", "2026-01-01"))::loadDefinitions);
    assertThrows(
        IllegalStateException.class,
        loaderFor(manifestWithMetadata("Description", " ", "2026-01-01"))::loadDefinitions);
  }

  @Test
  void rejectsInvalidExpiryDateButAllowsExpiredFlagsAtRuntime() {
    assertThrows(
        IllegalStateException.class,
        loaderFor(manifestWithMetadata("Description", "RSpace", "not-a-date"))::loadDefinitions);

    assertEquals(
        List.of(new FeatureFlagDefinition("someFlag", false)),
        loaderFor(manifestWithMetadata("Description", "RSpace", "2000-01-01")).loadDefinitions());
  }

  private static String validManifestWith(String fields) {
    return "{\"flags\":[{\"name\":\"someFlag\",\"description\":\"Description\","
        + "\"owner\":\"RSpace\",\"expires\":\"2026-01-01\","
        + fields
        + "}]}";
  }

  private static String manifestWithMetadata(String description, String owner, String expires) {
    return "{\"flags\":[{\"name\":\"someFlag\",\"description\":\""
        + description
        + "\",\"owner\":\""
        + owner
        + "\",\"expires\":\""
        + expires
        + "\"}]}";
  }

  private static String manifestWithName(String name) {
    return "{\"flags\":[{\"name\":\""
        + name
        + "\",\"description\":\"Description\",\"owner\":\"RSpace\",\"expires\":\"2026-01-01\"}]}";
  }

  private FeatureFlagManifestLoader loaderFor(String manifest) {
    ResourceLoader resourceLoader = mock(ResourceLoader.class);
    when(resourceLoader.getResource("classpath:feature-flags/feature-flags.jsonc"))
        .thenReturn(new ByteArrayResource(manifest.getBytes(StandardCharsets.UTF_8)));
    return new FeatureFlagManifestLoader(resourceLoader);
  }
}
