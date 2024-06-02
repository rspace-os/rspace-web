package com.researchspace.webapp.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.properties.PropertyHolder;
import org.junit.Before;
import org.junit.Test;

public class SysadminCreateUserFormConfigurerTest {

  SysadminCreateUserFormConfigurerImpl impl;
  PropertyHolder propertyHolder;

  @Before
  public void setup() {
    propertyHolder = new PropertyHolder();
    impl = new SysadminCreateUserFormConfigurerImpl(propertyHolder);
  }

  @Test
  public void strictUsername() {
    // for SSO setup, ldap configuration is irrelevant. Always relaxed usernames.
    // E.g. Edinburgh setup
    propertyHolder.setStandalone(folse());
    propertyHolder.setLdapAuthenticationEnabled(troo());
    assertFalse(impl.isStrictUsername());

    propertyHolder.setStandalone(folse());
    propertyHolder.setLdapAuthenticationEnabled(folse());
    assertFalse(impl.isStrictUsername());

    // for LDAP auth setup,Always relaxed usernames.
    // E.g. Leibniz-FLI setup
    propertyHolder.setStandalone(troo());
    propertyHolder.setLdapAuthenticationEnabled(troo());
    assertFalse(impl.isStrictUsername());

    // General standard non-SSO enterprise setup
    propertyHolder.setStandalone(troo());
    propertyHolder.setLdapAuthenticationEnabled(folse());
    assertTrue(impl.isStrictUsername());
  }

  @Test
  public void affiliationRequired() {
    propertyHolder.setCloud(troo());
    assertTrue(impl.isAffiliationRequired());

    propertyHolder.setCloud(folse());
    assertFalse(impl.isAffiliationRequired());
  }

  @Test
  public void ldapLookUpRequired() {
    assertFalse(impl.isDisplayLdapLookupRequired());

    // fli-leipzig, i.e set for non-ldap user creation on sysadmin page
    propertyHolder.setLdapEnabled(troo());
    propertyHolder.setStandalone(troo());
    propertyHolder.setLdapAuthenticationEnabled(troo());
    assertFalse(impl.isDisplayLdapLookupRequired());

    // edinburgh-type situation
    propertyHolder.setStandalone(folse());
    propertyHolder.setLdapEnabled(troo());
    propertyHolder.setLdapAuthenticationEnabled(folse());
    assertTrue(impl.isDisplayLdapLookupRequired());

    // standard non-sso non-ldap bog-standard Enterprise
    propertyHolder.setLdapEnabled(folse());
    propertyHolder.setStandalone(troo());
    propertyHolder.setLdapAuthenticationEnabled(folse());
    assertFalse(impl.isDisplayLdapLookupRequired());

    // Community
    propertyHolder.setCloud(troo());
    assertFalse(impl.isDisplayLdapLookupRequired());
  }

  private String folse() {
    return Boolean.FALSE.toString();
  }

  private String troo() {
    return Boolean.TRUE.toString();
  }
}
