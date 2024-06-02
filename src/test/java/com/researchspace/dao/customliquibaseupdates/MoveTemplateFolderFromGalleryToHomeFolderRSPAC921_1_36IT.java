package com.researchspace.dao.customliquibaseupdates;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.UserFolderCreator;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class MoveTemplateFolderFromGalleryToHomeFolderRSPAC921_1_36IT
    extends RealTransactionSpringTestBase {

  private @Autowired IContentInitializer contentInit;

  @Autowired()
  @Qualifier("oldFolderSetup")
  private UserFolderCreator oldTemplateInGallerySetup;

  @Before
  public void setup() {
    contentInit.setUserFolderCreator(oldTemplateInGallerySetup);
  }

  @Test
  public void testDoExecute() throws Exception {
    List<User> users = new ArrayList<User>();
    int counter = 0;
    for (int i = 0; i < 10; i++) {
      User user = createAndSaveUser(getRandomAlphabeticString("any"));
      users.add(user);
      initUser(user);
    }
    User anyUser = users.get(0);
    Folder root = initUser(anyUser);
    Folder oldTemplateFolder = folderMgr.getTemplateFolderForUser(anyUser);
    Folder mediaRoot = folderMgr.getGalleryRootFolderForUser(anyUser);
    //	Folder templateSubFolder = createSubFolder(oldTemplateFolder, "TemplateSub", anyUser);
    assertTrue(oldTemplateFolder.getParent().equals(mediaRoot));
    logoutAndLoginAs(anyUser);
    StructuredDocument anyDoc = createBasicDocumentInFolder(anyUser, root, "any");
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(anyDoc.getId(), anyUser);
    assertTrue(template.isTemplate());

    RSpaceTestUtils.logout();
    MoveTemplateFolderFromGalleryToHomeFolderRSPAC921_1_36 liquibase =
        new MoveTemplateFolderFromGalleryToHomeFolderRSPAC921_1_36();
    liquibase.setSysadminUname(Constants.SYSADMIN_UNAME);
    liquibase.setSysadminPwd(AbstractAppInitializor.SYSADMIN_PWD);
    liquibase.setUp();
    liquibase.execute(null);

    logoutAndLoginAs(anyUser);
    Folder newTemplateFolder = folderMgr.getTemplateFolderForUser(anyUser);
    assertTrue(newTemplateFolder.getParent().equals(root));
    assertFalse(newTemplateFolder.isInvisible());
    template = recordMgr.get(template.getId()).asStrucDoc(); // refresh
    assertFalse(template.isInvisible());

    assertTrue(template.getSharingACL().isPermitted(anyUser, PermissionType.DELETE));
    assertTrue(template.getSharingACL().isPermitted(anyUser, PermissionType.RENAME));
    assertTrue(permissionUtils.isPermitted(template, PermissionType.WRITE, anyUser));

    List<Long> descendantIds =
        recordMgr.getDescendantRecordIdsExcludeFolders(newTemplateFolder.getId());
    for (Long id : descendantIds) {
      StructuredDocument doc = recordMgr.get(id).asStrucDoc();
      assertTrue(doc.isTemplate());
      assertFalse(doc.isInvisible());
    }
  }
}
