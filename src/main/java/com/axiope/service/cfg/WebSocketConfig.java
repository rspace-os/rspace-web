package com.axiope.service.cfg;

import com.researchspace.webapp.filter.OriginRefererChecker;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.SockJsServiceRegistration;
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
    SockJsServiceRegistration sockJs =
        registry
            .addEndpoint("/ws")
            .setAllowedOrigins(acceptedDomains.toArray(new String[0]))
            .withSockJS();
    if (!isTomcatContainer()) {
      sockJs.setWebSocketEnabled(false);
    }
  }

  /**
   * Spring 6's StandardWebSocketUpgradeStrategy needs ServerContainer.upgradeHttpToWebSocket, added
   * in Jakarta WebSocket 2.1 (EE 10). Tomcat 10.1 provides it; Jetty 11 only implements WebSocket
   * 2.0, so a native handshake under the dev Jetty throws NoSuchMethodError. When not on Tomcat we
   * disable the native transport and let SockJS fall back to its HTTP transports. Remove this guard
   * once the dev container is Jetty 12 (EE10 environment) or later.
   */
  private boolean isTomcatContainer() {
    try {
      Class.forName("org.apache.tomcat.websocket.server.WsServerContainer");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
