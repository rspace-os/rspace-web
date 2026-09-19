package com.researchspace.model.comms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RequestFactoryTest {

  RequestFactory rf = new RequestFactory();

  MsgOrReqstCreationCfg createConfig(MessageType type) {
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(type);
    return config;
  }

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testCreateMessageOrREquestObject() {
    User user = TestFactory.createAnyUser("any");

    MessageOrRequest mor =
        rf.createMessageOrRequestObject(
            createConfig(MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP), null, null, user);
    assertNotNull(mor);
    assertTrue(mor instanceof GroupMessageOrRequest);

    MessageOrRequest simple =
        rf.createMessageOrRequestObject(
            createConfig(MessageType.REQUEST_RECORD_REVIEW), null, null, user);
    assertNotNull(simple);
    assertFalse(simple instanceof GroupMessageOrRequest);
  }
}
