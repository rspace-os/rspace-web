package com.axiope.service.cfg;

import com.researchspace.webapp.filter.OriginRefererChecker;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final OriginRefererChecker originRefererChecker;

  public WebSocketConfig(OriginRefererChecker originRefererChecker) {
    this.originRefererChecker = originRefererChecker;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    List<String> acceptedDomains = originRefererChecker.listAcceptedDomains();
    registry
        .addEndpoint("/ws")
        .setAllowedOrigins(acceptedDomains.toArray(new String[0]))
        .withSockJS();
  }
}
