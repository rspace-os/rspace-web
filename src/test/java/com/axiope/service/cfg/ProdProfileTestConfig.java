package com.axiope.service.cfg;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
