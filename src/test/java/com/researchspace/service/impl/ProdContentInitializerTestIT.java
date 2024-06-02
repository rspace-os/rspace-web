package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.FormDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.SampleDao;
import com.researchspace.dao.UserDao;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.service.FolderManager;
import com.researchspace.service.FormManager;
import com.researchspace.service.IContentInitialiserUtils;
import com.researchspace.service.IconImageManager;
import com.researchspace.service.InitializedContent;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserFolderCreator;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProdContentInitializerTestIT extends RealTransactionSpringTestBase {

  private static final int EXPECTED_EXAMPLE_IMG_COUNT = 7;

  private ProdContentInitializerManager initializer;
  private @Autowired RecordManager recordMgr;

  private User user;

  @Before
  public void setUp() throws Exception {
    // we have to init manually, as is only loaded in 'prod' profile and tests
    // run in 'dev' profile.
    initializer = new ProdContentInitializerManager();
    initializer.setFolderDao(getBeanOfClass(FolderDao.class));
    initializer.setFormDao(getBeanOfClass(FormDao.class));
    initializer.setPermissnUtils(getBeanOfClass(IPermissionUtils.class));
    initializer.setRecordDao(getBeanOfClass(RecordDao.class));
    initializer.setUserDao(getBeanOfClass(UserDao.class));
    initializer.setRecordFactory(getBeanOfClass(RecordFactory.class));

    initializer.setApplicationContext(applicationContext);
    initializer.setIconMgr(getBeanOfClass(IconImageManager.class));
    initializer.setRecordMgr(getBeanOfClass(RecordManager.class));
    initializer.setFormMgr(getBeanOfClass(FormManager.class));
    initializer.setMediaMgr(getBeanOfClass(MediaManager.class));
    initializer.setRichTextUpdater(getBeanOfClass(RichTextUpdater.class));
    initializer.setContentInitialiserUtils(getBeanOfClass(IContentInitialiserUtils.class));
    initializer.setImporter(getBeanOfClass(ExportImport.class));
    initializer.setFolderManager(getBeanOfClass(FolderManager.class));
    initializer.setSampleDao(getBeanOfClass(SampleDao.class));
    initializer.setSampleApiMgr(getBeanOfClass(SampleApiManager.class));
    initializer.setContainerApiMgr(getBeanOfClass(ContainerApiManager.class));
    initializer.setImportRecordsOnly(
        applicationContext.getBean("importRecordsOnly", ImportStrategy.class));

    UserFolderCreator ufc =
        applicationContext.getBeansOfType(UserFolderCreator.class).get("defaultUserFolderCreator");
    initializer.setUserFolderCreator(ufc);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testInit() throws Exception {

    user = createAndSaveUser(getRandomAlphabeticString("any"));
    logoutAndLoginAs(user);
    InitializedContent content = doInTransaction(() -> initializer.init(user.getId()));
    doInTransaction(
        () -> {
          assertEquals(
              4,
              folderDao
                  .getRootRecordForUser(user)
                  .getChildrens()
                  .size()); // shared + templates + examples + media
          // 3 + 4 in chem images
          assertEquals(
              EXPECTED_EXAMPLE_IMG_COUNT,
              getRecordCountInFolderForUser(content.getFolder().getMediaImgExamples().getId()));
        });
  }

  @Test
  public void testImagesAddedAndReadableRSPAC_665() throws Exception {

    user = createAndSaveUser(getRandomAlphabeticString("any"));
    logoutAndLoginAs(user);
    doInTransaction(() -> initializer.init(user.getId()));

    doInTransaction(
        () -> {
          Folder imagesFolder =
              recordMgr.getGallerySubFolderForUser(MediaUtils.IMAGES_MEDIA_FLDER_NAME, user);
          assertNotNull(imagesFolder);

          Set<BaseRecord> subfolders = imagesFolder.getChildrens();
          assertEquals(1, subfolders.size()); /* just 'Examples' subfolder + 4 chem images */
          assertTrue(subfolders.stream().map(BaseRecord::getName).anyMatch("Examples"::equals));

          Set<BaseRecord> exampleImages =
              subfolders.stream()
                  .filter(br -> br.getName().equals("Examples"))
                  .findFirst()
                  .get()
                  .getChildrens();
          assertEquals(EXPECTED_EXAMPLE_IMG_COUNT, exampleImages.size()); /* */

          for (BaseRecord img : exampleImages) {
            assertTrue(
                "user should have permission to modify imported image " + img.getName(),
                img.getSharingACL().isPermitted(user, PermissionType.WRITE));
          }
        });
  }
}
