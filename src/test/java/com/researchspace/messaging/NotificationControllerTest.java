package com.researchspace.messaging;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

  @Mock private SimpMessagingTemplate messagingTemplate;

  @InjectMocks private NotificationController notificationController;

  @Test
  void sendNotificationUpdate_ShouldSendToCorrectDestination() {
    // Arrange
    Long userId = 123L;
    NotificationMessage notification =
        new NotificationMessage(1, 2); // You'll need to create appropriate Message object

    // Act
    notificationController.sendNotificationUpdate(userId, notification);

    // Assert
    verify(messagingTemplate).convertAndSend("/topic/notifications/" + userId, notification);
  }
}
