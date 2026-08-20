package com.researchspace.model.comms;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;

public class UnanimousVotingRequestCompletionUpdatePolicyTest {

	User sender;
	User recipient1;
	User recipient2;
	UnanimousVotingRequestCompletionUpdatePolicy policy = new UnanimousVotingRequestCompletionUpdatePolicy();

	@Before
	public void setUp() throws Exception {
		sender = TestFactory.createAnyUser("sender");
		recipient1 = TestFactory.createAnyUser("recipient1");
		recipient2 = TestFactory.createAnyUser("recipient2");
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testUpdateStatus() {
		MessageOrRequest mor = new MessageOrRequest(MessageType.REQUEST_EXTERNAL_SHARE);
		mor.setOriginator(sender);
		CommunicationTarget ct1 = new CommunicationTarget();
		ct1.setCommunication(mor);
		ct1.setRecipient(recipient1);
		CommunicationTarget ct2 = new CommunicationTarget();
		ct2.setRecipient(recipient2);
		ct2.setCommunication(mor);
		mor.setRecipients(TransformerUtils.toSet(ct1, ct2));

		// check all is 'new' status at start of test:
		assertTrue(mor.isNew());
		for (CommunicationTarget ct : mor.getRecipients()) {
			assertTrue(ct.isNew());
		}
		ct1.setStatus(CommunicationStatus.COMPLETED);
		policy.voteCompleted(ct1);
		assertTrue(mor.isNew());
		assertTrue(ct2.isNew());
		ct2.setStatus(CommunicationStatus.COMPLETED);
		policy.voteCompleted(ct2);

		assertTrue("Should be completed but is " + mor.getStatus(), mor.isCompleted());
	}

	@Test
	public void testUpdateGlobalMessageStatus() {
		MessageOrRequest mor = new MessageOrRequest(MessageType.GLOBAL_MESSAGE);
		mor.setOriginator(sender);
		CommunicationTarget ct1 = new CommunicationTarget();
		ct1.setCommunication(mor);
		ct1.setRecipient(recipient1);
		CommunicationTarget ct2 = new CommunicationTarget();
		ct2.setRecipient(recipient2);
		ct2.setCommunication(mor);
		mor.setRecipients(TransformerUtils.toSet(ct1, ct2));

		// check all is 'new' status at start of test:
		assertTrue(mor.isNew());
		assertTrue(ct1.isNew());
		assertTrue(ct2.isNew());

		ct1.setStatus(CommunicationStatus.COMPLETED);
		policy.voteCompleted(ct1);
		assertTrue(mor.isNew());
		assertTrue(ct1.isCompleted());
		assertTrue(ct2.isNew());
		
		ct2.setStatus(CommunicationStatus.COMPLETED);
		policy.voteCompleted(ct2);
		assertTrue("should stay uncompleted for global message", mor.isNew());
		assertTrue(ct1.isCompleted());
		assertTrue(ct2.isCompleted());
	}
	
}
