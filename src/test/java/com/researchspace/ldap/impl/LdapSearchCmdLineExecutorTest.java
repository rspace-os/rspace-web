package com.researchspace.ldap.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

public class LdapSearchCmdLineExecutorTest {

  private final LdapSearchCmdLineExecutor cmdLineExecutor =
      new LdapSearchCmdLineExecutor(
          "dc=test,dc=howler,dc=researchspace,dc=com",
          "ldap://howler.researchspace.com",
          "description");

  @Test
  public void testLdapsearchCommandConstruction() {
    List<String> expectedCmd =
        List.of(
            "ldapsearch",
            "-x",
            "-H",
            "ldap://howler.researchspace.com",
            "-b",
            "dc=test,dc=howler,dc=researchspace,dc=com",
            "uid=ldapUser1");
    assertEquals(expectedCmd, cmdLineExecutor.getLdapSearchCommand("ldapUser1"));
  }

  @Test
  public void filterMetacharactersAreEscapedInUidArg() {
    // RFC4515 special chars ( ) * are escaped (not stripped), so the exact value is pinned.
    List<String> cmd = cmdLineExecutor.getLdapSearchCommand("evil)(uid=*");
    assertEquals("uid=evil\\29\\28uid=\\2a", cmd.get(cmd.size() - 1));
  }
}
