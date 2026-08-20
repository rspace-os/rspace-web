package com.researchspace.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;

public class CoollabGroupCreationTrackerTest {
	
	CollabGroupCreationTracker tracker;

	@Before
	public void setUp() throws Exception {
		MessageOrRequest anyMsg= new MessageOrRequest(MessageType.REQUEST_EXTERNAL_SHARE);
		tracker= new CollabGroupCreationTracker();
		tracker.setMor(anyMsg);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCountRepliesVsInvitations() {
		tracker.setNumInvitations((short)3);
		tracker.incrementReplies();
		tracker.incrementReplies();
		assertFalse(tracker.allReplied());
		tracker.incrementReplies();
		assertTrue(tracker.allReplied());
		
	}

}
