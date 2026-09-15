package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.service.Broadcaster;
import com.researchspace.testutils.TestFactory;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CommunicationManagerImplTest {

  class CommunicationManagerImplTSS extends CommunicationManagerImpl {
    boolean notified = false;

    public Notification systemNotify(
        NotificationType notificationType, String msg, String recipientName, boolean broadcast) {
      notified = true;
      return null;
    }
  }

  @Mock Broadcaster broadcaster1;
  @Mock Broadcaster broadcaster2;

  CommunicationManagerImplTSS mgr = new CommunicationManagerImplTSS();

  @Test
  public void testBroadcastWillUseAllBroadcastersEvenIfOneThrowsException() {
    mgr.setBroadcasters(Arrays.asList(new Broadcaster[] {broadcaster1, broadcaster2}));
    Mockito.doThrow(RuntimeException.class)
        .when(broadcaster1)
        .broadcast(Mockito.any(Communication.class));

    User sender = TestFactory.createAnyUser("s");
    User recip = TestFactory.createAnyUser("recip");

    MessageOrRequest mor = TestFactory.createAnyMessageForRecipuent(sender, recip);
    mgr.broadcast(mor, Collections.EMPTY_SET);
    Mockito.verify(broadcaster2, Mockito.times(1)).broadcast(Mockito.any(Communication.class));
    assertTrue(mgr.notified);
  }
}
