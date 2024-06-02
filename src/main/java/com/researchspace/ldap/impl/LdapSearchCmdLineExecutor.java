package com.researchspace.ldap.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/** Calls shell `ldapsearch` command to retrieve user's LDAP details. */
@Slf4j
public class LdapSearchCmdLineExecutor {

  private String searchBase;
  private String host;
  private String dnField;

  public LdapSearchCmdLineExecutor(String ldapBaseSuffix, String ldapUrl, String dnField) {
    searchBase = ldapBaseSuffix;
    host = ldapUrl.split("//")[1];
    this.dnField = dnField;
  }

  /**
   * Executes 'sh -c ldapsearch' command with and finds dn attribute in response.
   *
   * @param ldapUsername uid to match in ldapsearch
   * @return dn or null if not found
   */
  public String findDnForUid(String ldapUsername) {

    String command =
        String.format("ldapsearch -x -h %s -b \"%s\" uid=%s", host, searchBase, ldapUsername);
    log.debug("running: " + command);

    String foundDn = null;
    try {
      ProcessBuilder builder = new ProcessBuilder();
      builder.command("/bin/sh", "-c", command);
      Process process = builder.start();
      foundDn = readDnFromProcessOutput(process.getInputStream());
      log.info("ldapsearch found dn: " + foundDn);
      if (StringUtils.isBlank(foundDn)) {
        log.warn("dn not found by ldapsearch command: " + command);
      }
      process.waitFor();
    } catch (IOException | InterruptedException e) {
      log.warn("error when executing ldapsearch command: " + command, e);
    }

    return foundDn;
  }

  private String readDnFromProcessOutput(InputStream inputStream) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith(dnField + ": ")) {
          log.debug("found dn line: " + line);
          String[] dnLineTokens = line.split(" ");
          return dnLineTokens[1];
        }
      }
    }
    log.info("no line starting with \"" + dnField + ": \" found");
    return null;
  }
}
