package com.researchspace.messaging;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;

class WebSocketConfigTest {

  @Test
  void configureMessageBroker_ShouldConfigureCorrectly() {
    // Arrange
    WebSocketConfig config = new WebSocketConfig();
    MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
    SimpleBrokerRegistration brokerRegistration = mock(SimpleBrokerRegistration.class);
    when(registry.enableSimpleBroker(anyString())).thenReturn(brokerRegistration);
    when(registry.setApplicationDestinationPrefixes(anyString())).thenReturn(registry);

    // Act
    config.configureMessageBroker(registry);

    // Assert
    verify(registry).enableSimpleBroker("/topic");
    verify(registry).setApplicationDestinationPrefixes("/app");
  }

  @Test
  void registerStompEndpoints_ShouldConfigureCorrectly() {
    // Arrange
  }
}
