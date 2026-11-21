package com.axiope.service.cfg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.repository.spi.IRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The Figshare static files are optional in the single prod wiring; a bad setup must not break it.
 */
class ProductionConfigFigshareTest {

  @TempDir Path tempDir;

  private IRepository build(String licensesPath, String categoriesPath) {
    ProductionConfig config = new ProductionConfig();
    ReflectionTestUtils.setField(config, "figshareLicensesPath", licensesPath);
    ReflectionTestUtils.setField(config, "figshareCategoriesPath", categoriesPath);
    return config.figshareRepository();
  }

  private int licenseCount(IRepository repo) {
    List<?> licenses = (List<?>) ReflectionTestUtils.getField(repo, "figshareLicenses");
    return licenses == null ? 0 : licenses.size();
  }

  @Test
  void blankPathsLeaveRepositoryWithoutStaticConfig() {
    IRepository repo = build("", "");
    assertNotNull(repo);
    assertEquals(0, licenseCount(repo));
  }

  @Test
  void validFilesAreLoadedAsStaticConfig() throws Exception {
    Path licenses = tempDir.resolve("licenses.json");
    Path categories = tempDir.resolve("categories.json");
    Files.writeString(licenses, "[{\"value\":1,\"name\":\"CC BY 4.0\",\"url\":\"https://x\"}]");
    Files.writeString(categories, "[{\"id\":1,\"title\":\"Chemistry\",\"parent_id\":0}]");

    IRepository repo = build(licenses.toString(), categories.toString());

    assertTrue(licenseCount(repo) >= 1, "licenses from the static file should be loaded");
  }

  @Test
  void missingFileIsLoggedNotThrown() {
    IRepository repo =
        build(tempDir.resolve("nope-l.json").toString(), tempDir.resolve("nope-c.json").toString());
    assertNotNull(repo);
  }
}
