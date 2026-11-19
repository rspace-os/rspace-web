package com.researchspace.service.impl;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.dao.UserDao;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.IGroupCreationStrategy;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Prod registers this initialiser, so it must be a no-op unless dev group creation is enabled. */
@ExtendWith(MockitoExtension.class)
class DeveloperGroupSetupTest {

  @Mock private UserDao userDao;
  @Mock private IContentInitializer contentInitializer;
  @Mock private IGroupCreationStrategy groupCreationStrategy;
  @Mock private Logger logger;

  private DeveloperGroupSetup setup;

  @BeforeEach
  void setUp() {
    setup = new DeveloperGroupSetup();
    setup.setUserdao(userDao);
    setup.setContentInitializor(contentInitializer);
    ReflectionTestUtils.setField(setup, "grpStrategy", groupCreationStrategy);
    setup.logger = logger;
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"false", "", "TRUE"})
  void doesNothingUnlessPropertyIsExactlyTrue(String propertyValue) {
    ReflectionTestUtils.setField(setup, "createTestGroups", propertyValue);

    setup.onInitialAppDeployment();

    verifyNoInteractions(userDao, contentInitializer, groupCreationStrategy);
  }

  @Test
  void skipsGroupCreationAndWarnsWhenAllSeedUsersAreMissing() {
    ReflectionTestUtils.setField(setup, "createTestGroups", "true");

    setup.onInitialAppDeployment();

    verify(logger)
        .warn(
            anyString(), eq((Object) List.of("sysadmin1", "user1a", "user2b", "user3c", "user4d")));
    verify(userDao, never()).getUserByUsername(anyString());
    verifyNoInteractions(contentInitializer, groupCreationStrategy);
  }

  @Test
  void warnsOnlyAboutTheSeedUsersThatAreMissing() {
    ReflectionTestUtils.setField(setup, "createTestGroups", "true");
    when(userDao.userExists("sysadmin1")).thenReturn(true);
    when(userDao.userExists("user1a")).thenReturn(true);

    setup.onInitialAppDeployment();

    verify(logger).warn(anyString(), eq((Object) List.of("user2b", "user3c", "user4d")));
    verify(userDao, never()).getUserByUsername(anyString());
    verifyNoInteractions(contentInitializer, groupCreationStrategy);
  }
}
