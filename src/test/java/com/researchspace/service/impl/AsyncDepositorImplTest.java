package com.researchspace.service.impl;

import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.model.system.SystemPropertyTestFactory;
import com.researchspace.repository.spi.RepositoryOperationResult;
import com.researchspace.service.CommunicationManager;
import com.researchspace.testutils.VelocityTestUtils;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsyncDepositorImplTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();
  @Mock CommunicationManager comm;

  VelocityEngine engine;
  AsyncDepositorImplTSS impl;
  User any;
  File testFile;

  class AsyncDepositorImplTSS extends AsyncDepositorImpl {
    boolean updateDMPsCalled = false;

    void updateDMPS(
        RepositoryOperationResult result, User subject, RepoDepositConfig repoDepositConfig) {
      this.updateDMPsCalled = true;
    }
  }

  @Before
  public void setUp() throws Exception {
    impl = new AsyncDepositorImplTSS();
    engine =
        VelocityTestUtils.setupVelocity(
            "src/main/resources/velocityTemplates/messageAndNotificationEmails");
    impl.setVelocity(engine);
    impl.setCommMgr(comm);
    any = createAnyUser("any");
    testFile = File.createTempFile("test", ".txt");
  }

  @Test
  public void testMessageNoLink() {
    RepositoryOperationResult result = new RepositoryOperationResult(true, "hello", null);
    impl.postDeposit(
        result, SystemPropertyTestFactory.createAnyApp(), any, testFile, new RepoDepositConfig());
    Mockito.verify(comm)
        .systemNotify(
            Mockito.any(NotificationType.class),
            Mockito.contains("hello"),
            Mockito.eq(any.getUsername()),
            Mockito.eq(true));
    assertTrue(impl.updateDMPsCalled);
  }

  @Test
  public void testMessageLink() throws MalformedURLException {
    RepositoryOperationResult result =
        new RepositoryOperationResult(true, "hello", new URL("http://www.bbc.co.uk"));
    impl.postDeposit(
        result, SystemPropertyTestFactory.createAnyApp(), any, testFile, new RepoDepositConfig());
    Mockito.verify(comm)
        .systemNotify(
            Mockito.any(NotificationType.class),
            (Mockito.contains("http://www.bbc.co.uk")),
            Mockito.eq(any.getUsername()),
            Mockito.eq(true));
    assertTrue(impl.updateDMPsCalled);
  }
}
