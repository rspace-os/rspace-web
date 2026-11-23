package com.researchspace.testutils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.researchspace.service.GlobalInitManager;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.LicenseService;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.DeveloperGroupSetup;
import com.researchspace.service.impl.GroupSharedSnippetsFolderAppInitialiser;
import com.researchspace.spring.taskexecutors.ShiroThreadBindingSubjectThreadPoolExecutor;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;

/**
 * Guardrail: boots the prod Spring context, the wiring every running RSpace now uses. Booting runs
 * the prod startup initialisers against the configured database, as an app start would. That seeds
 * built-in forms and other content and nothing removes it afterwards, so the pom runs this class in
 * its own {@code unit-tests-production} execution, after {@code default-test}, and needs a
 * disposable database. A {@code -Djdbc.url} override is used as is. The tests themselves only read.
 * Group snippet-folder migration is covered by {@code GroupManagerTest}.
 */
@ProductionProfileTestConfiguration
@TestPropertySource(
    properties = {"docConverter.taskExecutor.max=251", "index.taskExecutor.queue=123"})
@WithSpringContext
public class ProdConfigWiringTest {

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

  @BeforeAll
  public static void skipFastRuns() throws Exception {
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
    userManager.exists(1L);
  }

  @Test
  public void testGlobalInits() {
    List<IApplicationInitialisor> inits = globalInitManager.getApplicationInitialisors();
    assertThat(inits).hasSizeGreaterThan(0);
    boolean containsSnippetFolderInit = false;
    boolean containsDevGroupSetup = false;
    for (IApplicationInitialisor anInit : inits) {
      Object target = AopProxyUtils.getSingletonTarget(anInit);
      if (target instanceof GroupSharedSnippetsFolderAppInitialiser) {
        containsSnippetFolderInit = true;
      }
      if (target instanceof DeveloperGroupSetup) {
        containsDevGroupSetup = true;
      }
    }
    if (!containsSnippetFolderInit) {
      fail("No init for global group snippets folders");
    }
    // run seeded dev groups; prod must keep doing so when rs.dev.groupcreation=true
    assertTrue(containsDevGroupSetup, "No init for developer groups");
  }

  @Test
  @EnabledIfSystemProperty(named = "nightly", matches = "(|true)")
  public void testIsLicenseActive() {
    service.isLicenseActive();
  }
}
