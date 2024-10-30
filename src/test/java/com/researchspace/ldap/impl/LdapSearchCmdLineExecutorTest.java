package com.researchspace.ldap.impl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LdapSearchCmdLineExecutorTest {

  @Test
  public void testLdapsearchCommandConstruction() {
    LdapSearchCmdLineExecutor cmdLineExecutor =
        new LdapSearchCmdLineExecutor(
            "dc=test,dc=howler,dc=researchspace,dc=com",
            "ldap://howler.researchspace.com",
            "description");

    String expectedCmd =
        "ldapsearch -x -H ldap://howler.researchspace.com -b"
            + " \"dc=test,dc=howler,dc=researchspace,dc=com\" uid=ldapUser1";
    assertEquals(expectedCmd, cmdLineExecutor.getLdapSearchCommandString("ldapUser1"));
  }
}
