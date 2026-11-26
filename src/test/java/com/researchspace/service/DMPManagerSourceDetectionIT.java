package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

/** The platform of a plan with no recorded source is worked out when read, and saved. */
class DMPManagerSourceDetectionIT extends RealTransactionSpringTestBase {

  private static final Path FIXTURES = Path.of("src/test/resources/TestResources/dmpSources");

  private @Autowired DMPManager dmpManager;
  private @Autowired JdbcTemplate jdbcTemplate;

  @Autowired
  @Qualifier("compositeFileStore")
  private FileStore fileStore;

  private User user;

  @BeforeEach
  void setUpUser() {
    user = createInitAndLoginAnyUser();
  }

  @Test
  void detectedSourceIsSavedAndNotDetectedAgain() throws Exception {
    DMPUser plan = saveUnknownPlanWithFile("dmptool-plan.json");

    assertEquals(DMPSource.DMP_TOOL, detectedPlan().getSource());
    assertEquals("DMP_TOOL", storedSource(plan));

    // a known source is not detected again, so a missing file no longer matters
    assertTrue(fileStore.findFile(plan.getDmpDownloadFile().getFileProperty()).delete());
    assertEquals(DMPSource.DMP_TOOL, detectedPlan().getSource());
  }

  @Test
  void detectedSourceIsSavedEvenWhenTheReadingTransactionRollsBack() throws Exception {
    DMPUser plan = saveUnknownPlanWithFile("dmptool-plan.json");

    openTransaction();
    assertEquals(DMPSource.DMP_TOOL, detectedPlan().getSource());
    getTxMger().rollback(status);

    assertEquals("DMP_TOOL", storedSource(plan));
  }

  @Test
  void planWhoseFileHasNoKnownLayoutStaysUnknown() throws Exception {
    DMPUser plan = saveUnknownPlanWithFile("unrecognised-plan.json");

    assertEquals(DMPSource.UNKNOWN, detectedPlan().getSource());
    assertEquals("UNKNOWN", storedSource(plan));
  }

  private DMPUser saveUnknownPlanWithFile(String fixture) throws Exception {
    EcatDocumentFile file;
    try (InputStream in = Files.newInputStream(FIXTURES.resolve(fixture))) {
      file = mediaMgr.saveNewDMP("Old plan.json", in, user, null);
    }
    DMPUser plan = new DMPUser(user, new DmpDto("1", "Old plan", DMPSource.UNKNOWN, null, null));
    plan.setDmpDownloadFile(file);
    return dmpManager.save(plan);
  }

  private DMPUser detectedPlan() {
    List<DMPUser> plans = dmpManager.detectMissingSources(user);
    assertEquals(1, plans.size());
    return plans.get(0);
  }

  // read straight from the database, so only committed changes are seen
  private String storedSource(DMPUser plan) {
    return jdbcTemplate.queryForObject(
        "select source from DMPUser where id = ?", String.class, plan.getId());
  }
}
