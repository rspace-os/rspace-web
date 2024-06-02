package com.researchspace.testsandbox;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

public class SubscriberTest extends SpringTransactionalTest {

  @Autowired ApplicationEventPublisher publisher;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void test() {
    User u = TestFactory.createAnyUser("any");
    publisher.publishEvent(new CreationEvent(u));
    u.setEnabled(false);
  }
}
