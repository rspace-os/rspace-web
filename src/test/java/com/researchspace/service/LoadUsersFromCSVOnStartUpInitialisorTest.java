package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.service.impl.LoadUsersFromCSVOnStartUpInitialisor;
import com.researchspace.testutils.SpringTransactionalTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

public class LoadUsersFromCSVOnStartUpInitialisorTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("loadUsersFromCSVOnStartUpInitialisor")
  private IApplicationInitialisor init;

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
    contentInitializer.setCustomInitActive(true); // restore default setting
  }

  @Test
  public void testOnInitialAppDeploymentFailsGracefullyIfCSVFileMissing() throws Exception {
    LoadUsersFromCSVOnStartUpInitialisor tss =
        getTargetObject(init, LoadUsersFromCSVOnStartUpInitialisor.class);
    String original = tss.getLoadUsersOnStartUpFile();
    tss.setLoadUsersOnStartUpFile("sddaddsad"); // non existent fi
    int initialNumGRoups = getTotalNumGroups();
    init.onInitialAppDeployment();
    assertEquals(initialNumGRoups, getTotalNumGroups());
    tss.setLoadUsersOnStartUpFile(original);
  }

  @Test
  public void testOnInitialAppDeploymentHappyCase() throws Exception {
    LoadUsersFromCSVOnStartUpInitialisor target =
        getTargetObject(init, LoadUsersFromCSVOnStartUpInitialisor.class);
    ResourceLoader originalResourceLoader =
        (ResourceLoader) ReflectionTestUtils.getField(target, "resourceLoader");
    String originalFile = target.getLoadUsersOnStartUpFile();
    String username = CoreTestUtils.getRandomName(10);
    String csv =
        "Test, User, "
            + username
            + "@example.com,ROLE_PI,"
            + username
            + ",testpass\n#Groups\nTest Group,"
            + username;
    ResourceLoader resourceLoader = mock(ResourceLoader.class);
    when(resourceLoader.getResource("classpath:test-users.csv"))
        .thenReturn(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)));

    int initialNumUsers = getTotalNumUsers();
    int initialNumGroups = getTotalNumGroups();
    try {
      ReflectionTestUtils.setField(target, "resourceLoader", resourceLoader);
      target.setLoadUsersOnStartUpFile("test-users.csv");
      contentInitializer.setCustomInitActive(false);
      init.onInitialAppDeployment();
      assertEquals(initialNumUsers + 1, getTotalNumUsers());
      assertEquals(initialNumGroups + 1, getTotalNumGroups());
    } finally {
      ReflectionTestUtils.setField(target, "resourceLoader", originalResourceLoader);
      target.setLoadUsersOnStartUpFile(originalFile);
    }
  }
}
