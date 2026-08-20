package com.researchspace.model.comms;

import com.researchspace.model.User;

public class CommsTestUtils {
	

	public static MessageOrRequest createARequest(User originator) {
		return createRequestOfType(originator, MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP);
	}

	public static MessageOrRequest createRequestOfType(User originator, MessageType type) {
		RequestFactory rf = createARequestFactory();
		MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
		config.setMessageType(type);
		MessageOrRequest message=rf.createMessageOrRequestObject(config,
				null,null,originator);
		
		message.setLatest(true);
		message.setMessage("Please look at line 250");

		return message;
	}

	private static RequestFactory createARequestFactory() {
		return new RequestFactory();
	}

}
