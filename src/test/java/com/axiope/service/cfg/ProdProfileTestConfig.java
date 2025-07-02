package com.axiope.service.cfg;

import javax.sql.DataSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** Adds test-specific beans in to tests requiring 'prod-test' profile */
@Configuration
@Profile("prod-test")
public class ProdProfileTestConfig {

  /*
   * Creates a JdbCTemplate for use in some convenient JdbcTestUtil methods
   */
  @Bean
  JdbcTemplate jdbcTemplate() {
    return new JdbcTemplate(dataSource);
  }

  private @Autowired DataSource dataSource;

  /*
   * Mock websocket messaging bean for test context as WebSocket configuration is excluded
   * from test context
   */
  @Bean
  public SimpMessagingTemplate simpMessagingTemplate() {
    return Mockito.mock(SimpMessagingTemplate.class);
  }

}
