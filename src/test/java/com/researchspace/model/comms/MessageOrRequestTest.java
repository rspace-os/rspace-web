package com.researchspace.model.comms;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageOrRequestTest {
	
	private MessageOrRequest mor;
	
	@BeforeEach
	public void setUp() {
		mor = new MessageOrRequest();
	}
	
	@Test
	public void testMEssageType() {
		for (MessageType mt : MessageType.values()) {
			assertFalse(StringUtils.isBlank(mt.getLabel()));
			if (mt.equals(MessageType.REQUEST_EXTERNAL_SHARE)) {
				assertFalse(ArrayUtils.contains(mt.getValidStatusesByRecipient(),
						CommunicationStatus.REPLIED));
			}
		}
	}

	@Test
	public void testStateAfterConstructions() {
		
		assertNotNull(mor.getCreationTime());
		assertEquals(CommunicationStatus.NEW, mor.getStatus());
		assertFalse(mor.isTerminated());
		// these need to be explicity set
		assertNull(mor.getMessageType());
		assertFalse(mor.isLatest());
	}

	@Test
	public void testIsMessage() {
		mor.setMessageType(MessageType.SIMPLE_MESSAGE);
		assertTrue(mor.isSimpleMessage());
		assertFalse(mor.isStatefulRequest());
		assertTrue(mor.isMessageOrRequest());
	}
	
	@Test
	public void testSetPreviousIAEIfDatesNotInCorrectORder() throws InterruptedException {
		mor.setMessageType(MessageType.SIMPLE_MESSAGE);
		Thread.sleep(1);
		MessageOrRequest next = new MessageOrRequest();
		assertThrows(IllegalArgumentException.class,()->mor.setPreviousMessage(next));
	}
	
	@Test
	public void testSetNextIAEIfDatesNotInCorrectORder() throws InterruptedException {
		mor.setMessageType(MessageType.SIMPLE_MESSAGE);
		Thread.sleep(1);
		MessageOrRequest next = new MessageOrRequest();
		assertThrows(IllegalArgumentException.class,()->next.setNextMessage(mor));
	}
	
	@Test
	public void testSetLatestThrowsISEIfNotMostCurrentByTime() throws InterruptedException {
		mor.setMessageType(MessageType.SIMPLE_MESSAGE);
		Thread.sleep(1);
		MessageOrRequest next = new MessageOrRequest();
		mor.setNextMessage(next);
		assertThrows(IllegalStateException.class,()->mor.setLatest(true));
	}
	
	@Test
	public void testSetLatestHappyCase() throws InterruptedException {
		mor.setMessageType(MessageType.SIMPLE_MESSAGE);
		Thread.sleep(1);
		MessageOrRequest next = new MessageOrRequest();
		mor.setNextMessage(next);
		next.setLatest(true);
		assertTrue(mor.isHasNextMessage());
		assertFalse(mor.isHasPreviousMessage());
	}

	@Test
	public void testIsRequest() {
		mor.setMessageType(MessageType.REQUEST_RECORD_REVIEW);
		assertFalse(mor.isSimpleMessage());
		assertTrue(mor.isStatefulRequest());
		assertTrue(mor.isMessageOrRequest());
	}

	@Test 
	public void testMessageTruncation() {
		String testShortMessage = "Test Short Message";
		mor.setMessage(testShortMessage);
		assertEquals(testShortMessage, mor.getMessage());
		
		String maxLengthMessage = new String(new char[Communication.MESSAGE_COLUMN_LENGTH]);
		mor.setMessage(maxLengthMessage);
		assertEquals(maxLengthMessage, mor.getMessage());

		String tooLongMessage = maxLengthMessage + testShortMessage;
		mor.setMessage(tooLongMessage);
		assertNotEquals(tooLongMessage, mor.getMessage());
		assertEquals(Communication.MESSAGE_COLUMN_LENGTH, mor.getMessage().length());
		assertTrue(mor.getMessage().endsWith("..."));
	}
}
