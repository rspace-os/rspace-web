package com.researchspace.files.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.dao.FileMetadataDao;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.service.impl.MediaManagerImpl;
import com.researchspace.testutils.EgnyteTestConfig;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;

/**
 * Egnyte setup, except we're mocking call to Egnyte server for speed/reliability of tests and to
 * test exceptional cases;
 */
@EgnyteTestConfig
@Ignore
public class ExternalFileServiceTest extends SpringTransactionalTest {

  private @Autowired ExternalFileService extFS;
  private @Autowired FileMetadataDao fileMetaDao;

  private @Mock ExternalFileStore extFStore;
  private @Autowired InternalFileStore localFs;
  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;

  private User anyUser;
  EcatDocumentFile mediaFile;
  File someFile;
  UserConnection uc;

  @Before
  public void before() throws Exception {
    anyUser = createInitAndLoginAnyUser();
    // we have to set the FS to be local here, to simulate initial upload just being local
    // getTargetObject(mediaMgr, MediaManagerImpl.class).setFileStore(localFs);
    mediaFile = addDocumentToGallery(anyUser);
    someFile = RSpaceTestUtils.getAnyAttachment();
    MockitoAnnotations.initMocks(this);
    // this stores access token
    uc =
        createAndSaveEgnyteUserConnectionWithAccessToken(
            anyUser, "anyToken - this is a mock API call");
  }

  @Before
  public void after() throws Exception {
    // tidy up so that original FS is left
    getTargetObject(mediaMgr, MediaManagerImpl.class).setFileStore(fileStore);
  }

  @Test
  public void saveLocalToExternalWhenExtsIsOK() {
    FileProperty fp = mediaFile.getFileProperty();
    ExternalFileStoreWithCredentials exFSWC = new ExternalFileStoreWithCredentials(extFStore, uc);
    ExtFileOperationStatus<ExternalFileId> mockedResult = createMockSuccessEgnyteSaveResult();
    Mockito.when(extFStore.save(fp, someFile, FileDuplicateStrategy.AS_NEW, uc))
        .thenReturn(mockedResult);
    extFS.save(exFSWC, fp, someFile, FileDuplicateStrategy.AS_NEW);

    FileProperty retrievedFromDB = fileMetaDao.get(fp.getId());
    assertTrue(retrievedFromDB.isExternal());
    assertTrue(retrievedFromDB.getRoot().isExternal());
  }

  @Test
  public void fileRemainsLocalIfEgnyteSaveFails() {
    FileProperty fp = mediaFile.getFileProperty();
    ExternalFileStoreWithCredentials exFSWC = new ExternalFileStoreWithCredentials(extFStore, uc);
    ExtFileOperationStatus<ExternalFileId> mockedResult = createMockFailureEgnyteSaveResult();
    Mockito.when(extFStore.save(fp, someFile, FileDuplicateStrategy.AS_NEW, uc))
        .thenReturn(mockedResult);
    extFS.save(exFSWC, fp, someFile, FileDuplicateStrategy.AS_NEW);

    FileProperty retrievedFromDB = fileMetaDao.get(fp.getId());
    assertFalse(retrievedFromDB.isExternal());
    assertFalse(retrievedFromDB.getRoot().isExternal());
  }

  private ExtFileOperationStatus<ExternalFileId> createMockSuccessEgnyteSaveResult() {
    return new ExtFileOperationStatus<ExternalFileId>(
        HttpStatus.CREATED.value(), null, new ExternalFileId("egnyteid"));
  }

  private ExtFileOperationStatus<ExternalFileId> createMockFailureEgnyteSaveResult() {
    return new ExtFileOperationStatus<ExternalFileId>(
        HttpStatus.BAD_REQUEST.value(), "Some error message", null);
  }
}
