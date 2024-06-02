package com.researchspace.testutils;

import static org.junit.Assert.assertEquals;
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
import com.researchspace.service.LicenseService;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.GroupSharedSnippetsFolderAppInitialiser;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.spring.taskexecutors.ShiroThreadBindingSubjectThreadPoolExecutor;
import java.util.List;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ProductionProfileTestConfiguration
@RunWith(ConditionalTestRunner.class)
@TestPropertySource(
    properties = {"docConverter.taskExecutor.max=251", "index.taskExecutor.queue=123"})
@Ignore // ignore for now, as failinig in open-source branch
public class ProdConfigWiringTest extends AbstractJUnit4SpringContextTests {

  @Autowired private UserManager userManager;

  @Autowired
  @Qualifier("RemoteTestLicenseServiceImpl")
  private LicenseService service;

  @Autowired
  @Qualifier("indexTaskExecutor")
  private ShiroThreadBindingSubjectThreadPoolExecutor indexTaskExecutor;

  @Autowired
  @Qualifier("docConverter")
  private ShiroThreadBindingSubjectThreadPoolExecutor docConverterTaskExecutor;

  @Autowired private GlobalInitManager globalInitManager;
  @Autowired private ApplicationContext appContext;
  @Autowired private GroupManager groupManager;
  @Autowired private FolderManager folderManager;

  @BeforeClass
  public static void BeforeClass() throws Exception {
    TestRunnerController.ignoreIfFastRun();
  }

  @Test
  public void testTaskExecutorConfiguration() {
    // check that property values are parsed and injected OK
    assertEquals(123, indexTaskExecutor.getConfiguredQueueCapacity());
    assertEquals(251, docConverterTaskExecutor.getMaxPoolSize());
  }

  @Test
  public void testConfiguration() {
    // we don't run any code, we just want to check that no exception is thrown
    assertTrue(1 == 1);
    userManager.exists(1L);
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

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testIsLicenseActive() {
    service.isLicenseActive();
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
