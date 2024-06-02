package com.researchspace.files.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.api.client.util.IOUtils;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.RSpaceTestUtils;
import com.researchspace.model.User;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.testutils.EgnyteTestConfig;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

// this will externalFS beans to be setup for Egnyte
@EgnyteTestConfig
public class FileStoreConfiguredForExternalFS extends SpringTransactionalTest {

  // set 'egnyte.accessToken' as -D commandline or launch config argument;
  @Value("${egnyte.accessToken}")
  private String accessToken;

  private @Autowired FileMetadataDao fileDao;

  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;

  User anyUser;
  EcatDocumentFile mediaFile;
  File someFile;

  @Before
  public void before() throws IOException, URISyntaxException, InterruptedException {
    anyUser = createInitAndLoginAnyUser();
    mediaFile = addDocumentToGallery(anyUser);
    someFile = RSpaceTestUtils.getAnyAttachment();
    // this stores access token
    createAndSaveEgnyteUserConnectionWithAccessToken(anyUser, accessToken);
    Thread.sleep(1000);
  }

  @Test
  public void existsIsTrueAfterSavingFile() throws IOException {
    // random component so as not to get 403 duplicate errors.
    mediaFile.getFileProperty().setFileCategory(RandomStringUtils.randomAlphabetic(5));
    URI localUri =
        fileStore.save(mediaFile.getFileProperty(), someFile, FileDuplicateStrategy.AS_NEW);
    assertNotNull(localUri);
    assertTrue(fileDao.get(mediaFile.getFileProperty().getId()).isExternal());
    assertTrue(fileStore.exists(mediaFile.getFileProperty()));
    // now we try to download it
    Optional<FileInputStream> fis = fileStore.retrieve(mediaFile.getFileProperty());

    assertNotNull(fis.get());
    File used = File.createTempFile("egnyte", ".pdf");
    FileOutputStream fos = new FileOutputStream(used);
    IOUtils.copy(fis.get(), fos);
    assertEquals(someFile.length(), used.length());
  }

  @Test
  public void fileNotExists() throws IOException {
    // random component so as not to get 403 duplicate errors.
    mediaFile.getFileProperty().setFileCategory(RandomStringUtils.randomAlphabetic(5));
    /// not savedd, so won't exist yet
    assertFalse(fileStore.exists(mediaFile.getFileProperty()));
  }
}
