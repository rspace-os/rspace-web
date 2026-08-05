package com.researchspace.api.v1.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.testutils.DatabaseCleaner;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestRunnerController;
import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;

public class FilesAPIHandlerCachingTest extends SpringTransactionalTest {

  private @Autowired FilesAPIHandler handler;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {}

  @AfterClass
  public static void after() {
    if (!TestRunnerController.isFastRun()) DatabaseCleaner.cleanUp();
  }

  @Test
  public void fileRenameTriggersCacheEviction() throws IOException {
    User anyUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(anyUser);
    logoutAndLoginAs(anyUser); // simulate

    EcatImage galleryImage = addImageToGallery(anyUser);

    ApiFile file = handler.getFile(galleryImage.getId(), anyUser);
    ApiFile cached = handler.getFile(galleryImage.getId(), anyUser);
    assertThat(cached, sameInstance(file));
    // force transaction commit so that transactional event listener is enabled
    TestTransaction.flagForCommit();
    recordMgr.renameRecord("newname", galleryImage.getId(), anyUser);
    TestTransaction.end();
    // no revert to normal rollback behaviour
    TestTransaction.start();
    TestTransaction.flagForRollback();
    ApiFile reloaded = handler.getFile(galleryImage.getId(), anyUser);
    assertThat(reloaded, not(sameInstance(file)));
  }
}
