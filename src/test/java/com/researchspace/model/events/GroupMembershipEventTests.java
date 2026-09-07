package com.researchspace.model.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import java.util.Date;
import org.junit.jupiter.api.Test;

class GroupMembershipEventTests {

  @Test
  void equalsHashcodeDependsOnTimestampFail() throws InterruptedException {
    User anyUser = TestFactory.createAnyUser("any");
    User piUser = TestFactory.createAnyUserWithRole("any", Constants.PI_ROLE);
    Group g = TestFactory.createAnyGroup(piUser, new User[] {});
    GroupMembershipEvent e1 = new GroupMembershipEvent(anyUser, g, GroupEventType.JOIN);
    Thread.sleep(1);
    GroupMembershipEvent e2 = new GroupMembershipEvent(anyUser, g, GroupEventType.JOIN);
    assertNotEquals(e2, e1);
    assertNotEquals(e2.hashCode(), e1.hashCode());
  }

  @Test
  void equalsHashcodeDependsOnTimestamp() throws InterruptedException {
    User anyUser = TestFactory.createAnyUser("any");
    User piUser = TestFactory.createAnyUserWithRole("any", Constants.PI_ROLE);
    Group g = TestFactory.createAnyGroup(piUser, new User[] {});
    Date nowInstant = new Date();
    GroupMembershipEvent e1 =
        new GroupMembershipEvent(1L, anyUser, g, GroupEventType.JOIN, nowInstant);

    GroupMembershipEvent e2 =
        new GroupMembershipEvent(1L, anyUser, g, GroupEventType.JOIN, nowInstant);
    assertEquals(e2, e1);
    assertEquals(e2.hashCode(), e1.hashCode());
  }
}
