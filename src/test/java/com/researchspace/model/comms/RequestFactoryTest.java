package com.researchspace.model.comms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;

public class RequestFactoryTest {

	RequestFactory rf = new RequestFactory();

	MsgOrReqstCreationCfg createConfig(MessageType type) {
		MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
		config.setMessageType(type);
		return config;
	}

	@Before
	public void setUp() throws Exception {
	
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCreateMessageOrREquestObject() {
		User user = TestFactory.createAnyUser("any");

		MessageOrRequest mor = rf.createMessageOrRequestObject(
				createConfig(MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP), null, null, user);
		assertNotNull(mor);
		assertTrue(mor instanceof GroupMessageOrRequest);

		MessageOrRequest simple = rf.createMessageOrRequestObject(createConfig(MessageType.REQUEST_RECORD_REVIEW), null,
				null, user);
		assertNotNull(simple);
		assertFalse(simple instanceof GroupMessageOrRequest);
	}

}
