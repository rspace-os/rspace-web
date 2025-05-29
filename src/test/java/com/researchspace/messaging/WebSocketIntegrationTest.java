package com.researchspace.messaging;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringJUnitConfig(
    classes = {WebSocketConfig.class, NotificationController.class, TestWebSocketConfig.class})
class WebSocketIntegrationTest {

  @Autowired private NotificationController notificationController;

  WebSocketStompClient stompClient;

  @BeforeEach
  void setup() {
    stompClient =
        new WebSocketStompClient(
            new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
  }

  @Test
  void shouldReceiveNotification()
      throws ExecutionException, InterruptedException, TimeoutException {
    // Create a WebSocket client
    stompClient.setMessageConverter(new MappingJackson2MessageConverter());

    // Create a CompletableFuture to handle the async nature of WebSocket communication
    CompletableFuture<NotificationMessage> messageFuture = new CompletableFuture<>();

    // Connect to the WebSocket endpoint
    StompSession stompSession =
        stompClient
            .connect("ws://localhost:8080/ws", new StompSessionHandlerAdapter() {})
            .get(1, SECONDS);

    // Subscribe to the notifications topic for a specific user
    Long userId = 123L;
    stompSession.subscribe(
        "/topic/notifications/" + userId,
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return NotificationMessage.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            messageFuture.complete((NotificationMessage) payload);
          }
        });

    // Create and send a test notification
    NotificationMessage testNotificationMessage = new NotificationMessage(1, 2);
    notificationController.sendNotificationUpdate(userId, testNotificationMessage);

    // Wait for the message to be received and verify its content
    NotificationMessage receivedNotificationMessage = messageFuture.get(5, SECONDS);
    assertNotNull(receivedNotificationMessage);
    assertEquals(1, testNotificationMessage.getNotificationCount());
    assertEquals(2, testNotificationMessage.getMessageCount());
  }

  @Test
  void verifyGreetingIsReceived() throws Exception {

    BlockingQueue<String> blockingQueue = new ArrayBlockingQueue<>(1);

    stompClient.setMessageConverter(new StringMessageConverter());

    StompSession session =
        stompClient
            .connect("ws://localhost:8080/ws", new StompSessionHandlerAdapter() {})
            .get(1, SECONDS);

    session.subscribe(
        "/topic/greetings",
        new StompFrameHandler() {

          @Override
          public Type getPayloadType(StompHeaders headers) {
            return String.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            blockingQueue.add((String) payload);
          }
        });

    session.send("/app/welcome", "Mike");

    CompletableFuture<String> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return blockingQueue.poll(1, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                return null;
              }
            });

    String message = future.get(1, TimeUnit.SECONDS);
    assertEquals("Hello, Mike!", message);
  }
}
