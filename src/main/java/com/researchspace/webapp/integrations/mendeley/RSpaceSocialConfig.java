package com.researchspace.webapp.integrations.mendeley;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;
import static org.springframework.context.annotation.ScopedProxyMode.INTERFACES;

import com.researchspace.mendeley.api.Mendeley;
import com.researchspace.mendeley.connect.MendeleyConnectionFactory;
import com.researchspace.properties.IPropertyHolder;
import javax.sql.DataSource;
import org.apache.shiro.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.social.UserIdSource;
import org.springframework.social.config.annotation.ConnectionFactoryConfigurer;
import org.springframework.social.config.annotation.EnableSocial;
import org.springframework.social.config.annotation.SocialConfigurer;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionFactoryLocator;
import org.springframework.social.connect.ConnectionRepository;
import org.springframework.social.connect.UsersConnectionRepository;
import org.springframework.social.connect.jdbc.JdbcUsersConnectionRepository;
import org.springframework.social.connect.web.ConnectController;

@Configuration
@EnableSocial
// can remove SpringSocial once Mendeley refactored too.
@Deprecated(forRemoval = true)
public class RSpaceSocialConfig implements SocialConfigurer {
  Logger log = LoggerFactory.getLogger(RSpaceSocialConfig.class);

  @Value("${mendeley.id}")
  private String mendeleyId;

  @Value("${mendeley.secret}")
  private String mendeleySecret;

  private @Autowired com.researchspace.model.permissions.TextEncryptor encryptor;
  private @Autowired IPropertyHolder properties;
  private @Autowired DataSource dataSource;

  @Bean
  public ConnectController connectController(
      ConnectionFactoryLocator connectionFactoryLocator,
      ConnectionRepository connectionRepository) {
    ConnectController controller =
        new ConnectController(connectionFactoryLocator, connectionRepository);
    String baseURL = properties.getServerUrl();
    if (baseURL.endsWith("/")) {
      baseURL = baseURL.substring(0, baseURL.length() - 1);
    }
    controller.setApplicationUrl(baseURL);
    return controller;
  }

  @Override
  public void addConnectionFactories(ConnectionFactoryConfigurer cfConfig, Environment env) {
    configureMendeley(cfConfig);
  }

  private void configureMendeley(ConnectionFactoryConfigurer cfConfig) {
    log.info("Configuring Mendeley: Adding {} and {}", mendeleyId, mendeleySecret);
    cfConfig.addConnectionFactory(new MendeleyConnectionFactory(mendeleyId, mendeleySecret));
  }

  @Override
  public UserIdSource getUserIdSource() {
    return () -> (String) SecurityUtils.getSubject().getPrincipal();
  }

  /**
   * Shiro implementation of Spring's TextEncryptor interface - Spring security implementation
   * require modification of JVM libraries.
   */
  static class ShiroEncryptor implements TextEncryptor {

    private com.researchspace.model.permissions.TextEncryptor service;

    public ShiroEncryptor(com.researchspace.model.permissions.TextEncryptor service) {
      super();
      this.service = service;
    }

    @Override
    public String encrypt(String text) {
      return service.encrypt(text);
    }

    @Override
    public String decrypt(String encryptedText) {
      return service.decrypt(encryptedText);
    }
  }

  @Override
  public UsersConnectionRepository getUsersConnectionRepository(
      ConnectionFactoryLocator connectionFactoryLocator) {
    return new JdbcUsersConnectionRepository(
        dataSource, connectionFactoryLocator, new ShiroEncryptor(encryptor));
  }

  /**
   * Have to override with prototype scope so can be used in background threads to instantiate API
   * template beans.
   */
  @Bean
  @Scope(value = SCOPE_PROTOTYPE, proxyMode = ScopedProxyMode.INTERFACES)
  public ConnectionRepository connectionRepository(
      UsersConnectionRepository usersConnectionRepository) {
    return usersConnectionRepository.createConnectionRepository(getUserIdSource().getUserId());
  }

  @Bean
  @Scope(value = SCOPE_PROTOTYPE, proxyMode = INTERFACES)
  public Mendeley mendeley(ConnectionRepository repository) {
    Connection<Mendeley> connection = repository.findPrimaryConnection(Mendeley.class);
    return connection != null ? connection.getApi() : null;
  }
}
