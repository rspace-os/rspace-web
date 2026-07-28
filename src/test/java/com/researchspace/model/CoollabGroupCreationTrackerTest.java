package com.researchspace.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CoollabGroupCreationTrackerTest {

  CollabGroupCreationTracker tracker;

  @BeforeEach
  public void setUp() throws Exception {
    MessageOrRequest anyMsg = new MessageOrRequest(MessageType.REQUEST_EXTERNAL_SHARE);
    tracker = new CollabGroupCreationTracker();
    tracker.setMor(anyMsg);
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testCountRepliesVsInvitations() {
    tracker.setNumInvitations((short) 3);
    tracker.incrementReplies();
    tracker.incrementReplies();
    assertFalse(tracker.allReplied());
    tracker.incrementReplies();
    assertTrue(tracker.allReplied());
  }
}
