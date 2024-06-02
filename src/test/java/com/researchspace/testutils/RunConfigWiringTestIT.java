package com.researchspace.testutils;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.researchspace.auth.GlobalInitSysadminAuthenticationToken;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import com.researchspace.service.GlobalInitManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.GroupSharedSnippetsFolderAppInitialiser;
import com.researchspace.webapp.controller.MVCTestBase;
import java.util.List;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.web.WebAppConfiguration;

@WebAppConfiguration()
@RunProfileTestConfiguration
public class RunConfigWiringTestIT extends MVCTestBase {

  @Autowired private GlobalInitManager globalInitManager;
  @Autowired private ApplicationContext appContext;
  @Autowired private GroupManager groupManager;
  @Autowired private FolderManager folderManager;
  @Autowired private UserManager userManager;

  @BeforeClass
  public static void BeforeClass() throws Exception {
    TestRunnerController.ignoreIfFastRun();
  }

  @Test
  public void testGlobalInits() {
    List<IApplicationInitialisor> inits = globalInitManager.getApplicationInitialisors();
    assertTrue(inits.size() > 0);
    boolean containsSnippetFolderInit = false;
    for (IApplicationInitialisor anInit : inits) {
      if (AopProxyUtils.getSingletonTarget(anInit)
          instanceof GroupSharedSnippetsFolderAppInitialiser) {
        containsSnippetFolderInit = true;
      }
    }
    if (!containsSnippetFolderInit) {
      fail("No init for global group snippets folders");
    }
  }

  @Test // NOTE - shows that existing groups get a shared snippets folder with a non empty ACL
  // However, this did not expose the issue of liquibase update requiring an explict save of the
  // shared snippets folder (see the comments in GroupManagerImpl.createSharedCommunalGroupFolders()
  // around the code which saves the shared snippet folder.
  // THEREFORE this test can pass but the correct updating of shared snippets folders in liquibase
  // profile might not be working
  public void testExistingGroupsUpdatedWithSharedSnippetFolderWithACL() {
    List<IApplicationInitialisor> inits = globalInitManager.getApplicationInitialisors();
    GroupSharedSnippetsFolderAppInitialiser testee = null;
    for (IApplicationInitialisor anInit : inits) {
      if (AopProxyUtils.getSingletonTarget(anInit)
          instanceof GroupSharedSnippetsFolderAppInitialiser) {
        testee = (GroupSharedSnippetsFolderAppInitialiser) AopProxyUtils.getSingletonTarget(anInit);
      }
    }
    GlobalInitSysadminAuthenticationToken sysAdminToken =
        new GlobalInitSysadminAuthenticationToken();
    final User sysAdmin = userManager.getUserByUsername(sysAdminToken.getPrincipal().toString());
    final Subject subject = SecurityUtils.getSubject();
    subject.login(sysAdminToken);
    for (Group group : groupManager.list()) {
      Folder shareSnippet =
          folderManager.getFolder(group.getSharedSnippetGroupFolderId(), group.getOwner());
      shareSnippet.setSharingACL(new RecordSharingACL());
      folderManager.save(shareSnippet, sysAdmin);
      assertTrue(shareSnippet.getSharingACL().getAclElements().size() == 0);
      group.setSharedSnippetGroupFolderId(null);
      groupManager.saveGroup(group, group.getOwner());
    }
    testee.onAppStartup(appContext);
    for (Group group : groupManager.list()) {
      Folder shareSnippet =
          folderManager.getFolder(group.getSharedSnippetGroupFolderId(), group.getOwner());
      assertTrue(shareSnippet.getSharingACL().getAclElements().size() > 0);
    }
  }
}
