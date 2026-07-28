package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.record.EcatDocumentThumbnailInitializationPolicy;
import com.researchspace.model.record.LinkedFieldsToMediaRecordInitPolicy;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This test class is outside of the Spring tests transaction environment. This is because auditing
 * only happens after a transaction is really committed to the database, and regular Spring Tests
 * always roll back. <br>
 */
public class BaseRecordManagerIT extends RealTransactionSpringTestBase {

  private @Autowired BaseRecordManager baseRecordMgr;
  private @Autowired AuditManager auditManager;
  private @Autowired InternalFileStore fileStore;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void retrieveMediaFileByVersion() throws Exception {
    User anyUser = createAndSaveUser(getRandomName(10));
    setUpUserWithoutCustomContent(anyUser);
    logoutAndLoginAs(anyUser);

    EcatImage image = addImageToGallery(anyUser);
    image = updateImageInGallery(image.getId(), anyUser);

    EcatMediaFile firstImg =
        baseRecordMgr.retrieveMediaFile(anyUser, image.getId(), null, 1L, null);
    assertEquals(1, firstImg.getVersion());
    EcatMediaFile secondImg =
        baseRecordMgr.retrieveMediaFile(anyUser, image.getId(), null, 2L, null);
    assertEquals(2, secondImg.getVersion());
    EcatMediaFile latestImg =
        baseRecordMgr.retrieveMediaFile(anyUser, image.getId(), null, null, null);
    assertEquals(2, latestImg.getVersion());
  }

  @Test
  public void retrieveAuditedDocumentInitializesThumbnailFileProperty() throws Exception {
    User anyUser = createAndSaveUser(getRandomName(10));
    setUpUserWithoutCustomContent(anyUser);
    logoutAndLoginAs(anyUser);

    EcatDocumentFile document = addDocumentToGallery(anyUser);
    FileProperty thumbnail =
        fileStore.createAndSaveFileProperty(
            InternalFileStore.DOC_THUMBNAIL_CATEGORY,
            anyUser,
            "thumbnail.png",
            RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png"));
    document.setDocThumbnailFP(thumbnail);
    baseRecordMgr.save(document, anyUser);

    List<AuditedEntity<EcatDocumentFile>> revisions =
        auditManager.getRevisionsForEntity(EcatDocumentFile.class, document.getId());
    Number latestRevision = revisions.get(revisions.size() - 1).getRevision();
    EcatDocumentFile auditedDocument =
        (EcatDocumentFile)
            baseRecordMgr.retrieveMediaFile(
                anyUser,
                document.getId(),
                latestRevision.longValue(),
                null,
                new EcatDocumentThumbnailInitializationPolicy(
                    new LinkedFieldsToMediaRecordInitPolicy()));

    assertNotNull(auditedDocument.getDocThumbnailFP().getRoot());
  }
}
