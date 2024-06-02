package com.axiope.service.cfg;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

/** This class configures beans used for Ldap integration. */
@Configuration
public class LdapConfig {

  protected Logger log = LoggerFactory.getLogger(LdapConfig.class);

  @Value("${ldap.enabled}")
  private String ldapEnabled;

  @Value("${ldap.url}")
  private String ldapUrl;

  @Value("${ldap.baseSuffix}")
  private String ldapBaseSuffix;

  @Value("${ldap.ignorePartialResultException}")
  private String ignorePartialResultException;

  @Value("${ldap.bindQuery.dn}")
  private String ldapBindDn;

  @Value("${ldap.bindQuery.password}")
  private String ldapBindPassword;

  @Value("${ldap.anonymousBind}")
  private String ldapAnonymousBind;

  @Value("${ldap.userSearchQuery.objectSidField}")
  private String ldapSearchObjectSidField;

  @Bean
  public LdapContextSource contextSource() {
    LdapContextSource contextSource = new LdapContextSource();
    if ("true".equals(ldapEnabled)) {
      contextSource.setUrl(ldapUrl);
      contextSource.setBase(ldapBaseSuffix);
      contextSource.setUserDn(ldapBindDn);
      contextSource.setPassword(ldapBindPassword);
      contextSource.setAnonymousReadOnly("true".equals(ldapAnonymousBind));
      if (StringUtils.isNotBlank(ldapSearchObjectSidField)) {
        Map<String, Object> baseEnvironmentProperties = new HashMap<>();
        baseEnvironmentProperties.put(
            "java.naming.ldap.attributes.binary", ldapSearchObjectSidField);
        contextSource.setBaseEnvironmentProperties(baseEnvironmentProperties);
      }

      log.info(
          "creating production LdapContextSource and connecting to "
              + contextSource.getUrls()[0]
              + " - "
              + contextSource.getBaseLdapPathAsString());
    } else {
      contextSource.setUrl(""); // set to avoid exception
      log.info("Skipping LdapContextSource initialisation");
    }

    return contextSource;
  }

  @Bean
  public LdapTemplate ldapTemplate() {
    LdapTemplate ldapTemplate = new LdapTemplate(contextSource());
    ldapTemplate.setIgnorePartialResultException("true".equals(ignorePartialResultException));
    return ldapTemplate;
  }
}
