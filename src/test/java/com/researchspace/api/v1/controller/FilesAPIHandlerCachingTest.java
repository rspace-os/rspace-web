package com.researchspace.api.v1.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.testutils.DatabaseCleanerLifecycle;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;

public class FilesAPIHandlerCachingTest extends SpringTransactionalTest
    implements DatabaseCleanerLifecycle {

  private @Autowired FilesAPIHandler handler;
  private @Autowired DataSource dataSource;

  @Override
  public DataSource getDataSourceForCleanup() {
    return dataSource;
  }

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
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
