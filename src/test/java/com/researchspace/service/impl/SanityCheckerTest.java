package com.researchspace.service.impl;

import static com.researchspace.core.testutil.CoreTestUtils.configureStringLogger;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axiope.search.FileSearchStrategy;
import com.axiope.search.IFileIndexer;
import com.axiope.search.IFileSearcher;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
public class SanityCheckerTest {

  StringAppenderForTestLogging strglogger;

  @Mock IFileIndexer fileIndexer;
  @Mock IFileSearcher searcher;
  @Mock FileSearchStrategy searchStrategy;

  @Mock ApplicationContext context;
  @Mock IPropertyHolder propHolder;
  @Mock UserDao userDao;
  @InjectMocks SanityChecker sanityChecker;

  @BeforeEach
  public void setUp() throws Exception {
    strglogger = configureStringLogger(AbstractAppInitializor.log);
  }

  @Test
  public void testSanityCheckOnAppStartup() throws Exception {
    when(userDao.getUserByUsername(Mockito.anyString()))
        .thenReturn(TestFactory.createAnyUser("any"));
    when(searchStrategy.isLocal()).thenReturn(false);
    sanityChecker.onAppStartup(context);
    // Mockito.verify(chemModule, Mockito.times(1)).substructureSearch(Mockito.anyString(),
    // Mockito.any(User.class));
    assertTrue(strglogger.logContents.contains(SanityChecker.SANITY_CHECK_RUN_ALL_OK_MSG + "true"));
    // not local search should not run here, it might not be configured yet
    verify(searchStrategy, never()).searchFiles(Mockito.anyString(), Mockito.any(User.class));
    strglogger.logContents = ""; // clear contents
    // get implementation class to set in a mock object that will throw an
    // exception
    // and assert that log does not log 'All OK'

    Mockito.doThrow(new IOException()).when(fileIndexer).init(false);

    sanityChecker.onAppStartup(context);
    assertFalse(
        strglogger.logContents.contains(SanityChecker.SANITY_CHECK_RUN_ALL_OK_MSG + "true"));
  }

  @Test
  public void truncateSensitivePropertyNames() {
    final String FULL_VALUE = "123456789";
    var sensitiveMapList =
        List.of(
            Map.of("xxx-secret-property", FULL_VALUE),
            Map.of("xxx-key-property", FULL_VALUE),
            Map.of("xxx-password", FULL_VALUE),
            Map.of("xxx-token", FULL_VALUE),
            Map.of("xxx-client", FULL_VALUE));
    for (var map : sensitiveMapList) {
      String sanitised = sanityChecker.deploymentPropertiesToString(map);
      assertFalse(sanitised.contains("56789"));
      assertTrue(sanitised.contains("123"));
    }

    String normalProperty =
        sanityChecker.deploymentPropertiesToString(Map.of("any-other-property", "123456789"));
    assertTrue(normalProperty.contains("123456789"));
  }
}
