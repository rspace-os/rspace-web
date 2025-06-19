package com.axiope.service.cfg;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.service.IMessageAndNotificationTracker;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

  @Mock private IMessageAndNotificationTracker tracker;

  @InjectMocks private WebSocketEventListener listener;

  @ParameterizedTest
  @ValueSource(strings = {"123", "-456"})
  void handleSubscribeEvent_shouldExtractUserIdAndSendNotification(String userId) {
    String topic = "/topic/notifications/" + userId;
    Map<String, Object> headers = new HashMap<>();
    headers.put("simpDestination", topic);
    MessageHeaders messageHeaders = new MessageHeaders(headers);
    Message message = mock(Message.class);
    when(message.getHeaders()).thenReturn(messageHeaders);
    Principal principal = mock(Principal.class);

    SessionSubscribeEvent event = new SessionSubscribeEvent(this, message, principal);

    listener.handleSubscribeEvent(event);

    ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
    verify(tracker, times(1)).sendNotificationUpdate(captor.capture());
    assert (captor.getValue().equals(Long.valueOf(userId)));
  }
}
