package com.researchspace.model.comms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

public class MessageTypeTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@Test
	public void isStandardType() {
		assertTrue(MessageType.SIMPLE_MESSAGE.isStandardType());
		assertFalse(MessageType.REQUEST_CREATE_LAB_GROUP.isStandardType());
		assertFalse(MessageType.REQUEST_SHARE_RECORD.isStandardType());
		assertFalse(MessageType.REQUEST_JOIN_LAB_GROUP.isStandardType());
	}

}
