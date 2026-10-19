package com.researchspace.model.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import java.util.Date;
import org.junit.jupiter.api.Test;

class UserAccountEventTest {

  @Test
  void equalsHashcodeDependsOnTimestampFail() throws InterruptedException {
    User anyUser = TestFactory.createAnyUser("any");
    UserAccountEvent e1 = new UserAccountEvent(anyUser, AccountEventType.DISABLED);
    Thread.sleep(1);
    UserAccountEvent e2 = new UserAccountEvent(anyUser, AccountEventType.DISABLED);
    assertNotEquals(e2, e1);
    assertNotEquals(e2.hashCode(), e1.hashCode());
  }

  @Test
  void equalsHashcodeDependsOnTimestamp() throws InterruptedException {
    User anyUser = TestFactory.createAnyUser("any");
    Date nowInstant = new Date();
    UserAccountEvent e1 = new UserAccountEvent(1L, anyUser, AccountEventType.DISABLED, nowInstant);
    Thread.sleep(1);
    UserAccountEvent e2 = new UserAccountEvent(1L, anyUser, AccountEventType.DISABLED, nowInstant);
    assertEquals(e2, e1);
    assertEquals(e2.hashCode(), e1.hashCode());
  }
}
